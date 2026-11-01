package com.example.seatservice.service;

import com.example.seatservice.dto.SeatResponseDTO;
import com.example.seatservice.entity.Seat;
import com.example.seatservice.entity.SeatStatus;
import com.example.seatservice.exception.SeatNotAvailableException;
import com.example.seatservice.repository.SeatRepository;
import com.example.seatservice.repository.SeatReservationRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The one property the seat-service must never lose: a seat is sold at most
 * once per showtime.
 *
 * A reservation is a row in seat_reservations keyed by (showtime_id,
 * seat_id), and the UNIQUE key on that pair is the guard. booking-service
 * treats a failed reserve as "someone else got the seat" and writes no
 * booking row. If two concurrent callers could both be told they won, the
 * system double-books and nothing downstream would notice.
 *
 * That property cannot be tested with mocks or an embedded database. The
 * race is resolved by InnoDB: the first insert takes the unique-key lock and
 * commits, the rest wait on it and then fail with a duplicate key. So the
 * test runs against a real MySQL from Testcontainers, on the real Flyway
 * schema, through SeatServiceImpl.
 *
 * Requires a running Docker daemon. It does not need the local MySQL or
 * Eureka that the contextLoads test depends on — Eureka is switched off and
 * the datasource is wired to the container by @ServiceConnection.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false",
        // One connection per contending thread. With the default pool of
        // ten, the threads would queue on the pool instead of racing for
        // the row, and the test would still pass without proving anything.
        "spring.datasource.hikari.maximum-pool-size=24"
})
@Testcontainers
@DisplayName("Seat reservation per showtime")
class SeatReservationConcurrencyTest {

    private static final int CONTENDING_THREADS = 16;

    private static final int SCREEN = 1;
    private static final int SHOW_2PM = 101;
    private static final int SHOW_8PM = 102;

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @Autowired
    private SeatService seatService;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private SeatReservationRepository reservationRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Integer seatId;

    @BeforeEach
    void createOneAvailableSeat() {

        // Reservations go with their seats (ON DELETE CASCADE).
        seatRepository.deleteAll();

        seatId = createSeat("A1", SeatStatus.AVAILABLE);
    }

    private Integer createSeat(String number, SeatStatus status) {

        return seatRepository.save(
                Seat.builder()
                        .screenId(SCREEN)
                        .seatNumber(number)
                        .seatType("STANDARD")
                        .status(status)
                        .createdAt(LocalDateTime.now())
                        .build()
        ).getSeatId();
    }

    private Map<String, SeatStatus> gridFor(int showtimeId) {

        return seatService.getSeatsByScreenId(SCREEN, showtimeId)
                .stream()
                .collect(Collectors.toMap(
                        SeatResponseDTO::seatNumber,
                        SeatResponseDTO::status
                ));
    }


    @Test
    @DisplayName("sixteen callers race for one seat in one show and exactly one wins")
    void exactlyOneReservationWins() throws Exception {

        AtomicInteger won = new AtomicInteger();
        AtomicInteger lost = new AtomicInteger();
        List<Throwable> unexpected = new ArrayList<>();

        // Every thread blocks on this latch and is released at once, so the
        // reservations genuinely overlap. Submitting them in a loop without
        // it would let most finish before the next one started.
        CountDownLatch startLine = new CountDownLatch(1);

        ExecutorService pool =
                Executors.newFixedThreadPool(CONTENDING_THREADS);

        List<Future<?>> attempts = new ArrayList<>();

        for (int i = 0; i < CONTENDING_THREADS; i++) {
            attempts.add(pool.submit(() -> {

                startLine.await();

                try {
                    seatService.reserveSeat(seatId, SHOW_2PM);
                    won.incrementAndGet();
                } catch (SeatNotAvailableException expected) {
                    lost.incrementAndGet();
                } catch (Throwable other) {
                    // A deadlock, a lock-wait timeout or a missing
                    // transaction must not be mistaken for a clean loss.
                    synchronized (unexpected) {
                        unexpected.add(other);
                    }
                }

                return null;
            }));
        }

        startLine.countDown();

        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS))
                .as("all reservation attempts finished")
                .isTrue();

        for (Future<?> attempt : attempts) {
            attempt.get();
        }

        assertThat(unexpected)
                .as("no attempt failed for a reason other than losing the race")
                .isEmpty();

        assertThat(won.get())
                .as("exactly one caller may be told it reserved the seat")
                .isEqualTo(1);

        assertThat(lost.get())
                .as("every other caller must be refused")
                .isEqualTo(CONTENDING_THREADS - 1);

        assertThat(reservationRepository.count())
                .as("the seat is held exactly once")
                .isEqualTo(1);
    }


    @Test
    @DisplayName("booking a seat for one show leaves it free for another show")
    void showtimesAreIndependent() {

        Integer a2 = createSeat("A2", SeatStatus.AVAILABLE);

        seatService.reserveSeat(seatId, SHOW_2PM);
        seatService.reserveSeat(a2, SHOW_8PM);

        assertThat(gridFor(SHOW_2PM))
                .containsEntry("A1", SeatStatus.BOOKED)
                .containsEntry("A2", SeatStatus.AVAILABLE);

        assertThat(gridFor(SHOW_8PM))
                .containsEntry("A1", SeatStatus.AVAILABLE)
                .containsEntry("A2", SeatStatus.BOOKED);

        // The same seat can be sold for the other show.
        seatService.reserveSeat(seatId, SHOW_8PM);
        assertThat(gridFor(SHOW_8PM)).containsEntry("A1", SeatStatus.BOOKED);

        // The seat row itself is never touched by a reservation.
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus())
                .isEqualTo(SeatStatus.AVAILABLE);
    }


    @Test
    @DisplayName("a showtime nobody has booked shows every seat available")
    void newShowtimeStartsEmpty() {

        createSeat("A2", SeatStatus.AVAILABLE);
        seatService.reserveSeat(seatId, SHOW_2PM);

        assertThat(gridFor(999))
                .containsOnly(
                        Map.entry("A1", SeatStatus.AVAILABLE),
                        Map.entry("A2", SeatStatus.AVAILABLE)
                );
        assertThat(seatService.getAvailableSeatsByScreenId(SCREEN, 999))
                .hasSize(2);
    }


    @Test
    @DisplayName("release frees the seat for that show only, and is idempotent")
    void releaseIsPerShowAndIdempotent() {

        seatService.reserveSeat(seatId, SHOW_2PM);
        seatService.reserveSeat(seatId, SHOW_8PM);

        seatService.releaseSeat(seatId, SHOW_2PM);
        // booking-service retries this call and swallows failures. If the
        // second release threw, a retried cancellation would surface an
        // error for work that had already succeeded.
        seatService.releaseSeat(seatId, SHOW_2PM);

        assertThat(gridFor(SHOW_2PM)).containsEntry("A1", SeatStatus.AVAILABLE);
        assertThat(gridFor(SHOW_8PM)).containsEntry("A1", SeatStatus.BOOKED);
    }


    @Test
    @DisplayName("a seat already booked for a show cannot be reserved again for it")
    void secondReservationIsRefused() {

        seatService.reserveSeat(seatId, SHOW_2PM);

        assertThat(
                assertThrows(
                        SeatNotAvailableException.class,
                        () -> seatService.reserveSeat(seatId, SHOW_2PM)
                )
        ).hasMessageContaining(String.valueOf(seatId));
    }


    @Test
    @DisplayName("a blocked seat cannot be reserved for any show")
    void blockedSeatIsRefused() {

        Integer broken = createSeat("A2", SeatStatus.BLOCKED);

        assertThrows(
                SeatNotAvailableException.class,
                () -> seatService.reserveSeat(broken, SHOW_2PM)
        );
        assertThat(gridFor(SHOW_8PM)).containsEntry("A2", SeatStatus.BLOCKED);
        assertThat(reservationRepository.count()).isZero();
    }


    @Test
    @DisplayName("a pre-V4 BOOKED seat is still held, and its booking can release it")
    void legacyBookedSeatIsReleased() {

        transactionTemplate.executeWithoutResult(status ->
                seatRepository.compareAndSetStatus(
                        seatId, SeatStatus.AVAILABLE, SeatStatus.BOOKED
                )
        );

        assertThrows(
                SeatNotAvailableException.class,
                () -> seatService.reserveSeat(seatId, SHOW_2PM)
        );

        // The old booking is cancelled: no reservation row, so the seat row
        // is freed the old way.
        seatService.releaseSeat(seatId, SHOW_2PM);

        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus())
                .isEqualTo(SeatStatus.AVAILABLE);
        seatService.reserveSeat(seatId, SHOW_2PM);
    }


    @Test
    @DisplayName("reserve without a showtime is a 400, not a reservation")
    void showtimeIsRequired() {

        assertThrows(
                IllegalArgumentException.class,
                () -> seatService.reserveSeat(seatId, null)
        );
        assertThat(reservationRepository.count()).isZero();
    }
}
