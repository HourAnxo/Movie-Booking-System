package com.example.seatservice.service;

import com.example.seatservice.entity.Seat;
import com.example.seatservice.entity.SeatStatus;
import com.example.seatservice.exception.SeatNotAvailableException;
import com.example.seatservice.repository.SeatRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one test the seat-service actually needs.
 *
 * SeatRepository.compareAndSetStatus is a conditional UPDATE rather than a
 * read-then-write, and every other correctness claim in the booking saga
 * rests on that: booking-service treats a failed reserve as "someone else
 * got the seat" and writes no booking row. If two concurrent callers could
 * both be told they won, the system double-books and nothing downstream
 * would notice.
 *
 * That property cannot be tested with mocks or an embedded database. The
 * race is resolved by InnoDB's row lock — the first UPDATE to take the lock
 * commits, the rest block, re-read the row under the lock and match zero
 * rows because the status has moved on. So the test runs against a real
 * MySQL from Testcontainers, on the real Flyway schema, through
 * SeatServiceImpl so that the actual @Transactional boundary is exercised.
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
@DisplayName("Seat reservation under concurrency")
class SeatReservationConcurrencyTest {

    private static final int CONTENDING_THREADS = 16;

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @Autowired
    private SeatService seatService;

    @Autowired
    private SeatRepository seatRepository;

    private Integer seatId;

    @BeforeEach
    void createOneAvailableSeat() {

        seatRepository.deleteAll();

        Seat seat = seatRepository.save(
                Seat.builder()
                        .screenId(1)
                        .seatNumber("A1")
                        .seatType("STANDARD")
                        .status(SeatStatus.AVAILABLE)
                        .createdAt(LocalDateTime.now())
                        .build()
        );

        seatId = seat.getSeatId();
    }


    @Test
    @DisplayName("sixteen callers race for one seat and exactly one wins")
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
                    seatService.reserveSeat(seatId);
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

        assertThat(seatRepository.findById(seatId))
                .get()
                .extracting(Seat::getStatus)
                .as("the seat ends up BOOKED exactly once")
                .isEqualTo(SeatStatus.BOOKED);
    }


    @Test
    @DisplayName("release is idempotent, so a retried compensation cannot fail")
    void releaseIsIdempotent() {

        seatService.reserveSeat(seatId);

        seatService.releaseSeat(seatId);
        seatService.releaseSeat(seatId);

        // booking-service retries this call and swallows failures. If the
        // second release threw, a retried cancellation would surface an
        // error for work that had already succeeded.
        assertThat(seatRepository.findById(seatId))
                .get()
                .extracting(Seat::getStatus)
                .isEqualTo(SeatStatus.AVAILABLE);
    }


    @Test
    @DisplayName("a seat that is already BOOKED cannot be reserved again")
    void secondReservationIsRefused() {

        seatService.reserveSeat(seatId);

        assertThat(
                org.junit.jupiter.api.Assertions.assertThrows(
                        SeatNotAvailableException.class,
                        () -> seatService.reserveSeat(seatId)
                )
        ).hasMessageContaining(String.valueOf(seatId));
    }
}
