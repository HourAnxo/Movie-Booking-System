package com.example.seatservice.service;

import com.example.seatservice.entity.Seat;
import com.example.seatservice.entity.SeatStatus;
import com.example.seatservice.exception.ResourceNotFoundException;
import com.example.seatservice.exception.SeatInUseException;
import com.example.seatservice.exception.SeatNotAvailableException;
import com.example.seatservice.repository.SeatRepository;
import com.example.seatservice.repository.SeatReservationRepository;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A seat that a live booking holds must not be deleted, or the booking ends
 * up pointing at a seat that no longer exists (booking 37 did, after its
 * seat was deleted by hand).
 *
 * "Held" means a reservation row (kept exactly while the booking is PENDING
 * or CONFIRMED) or a pre-V4 BOOKED status. The delete is one conditional
 * statement, so the race test below checks that a reserve and a delete
 * landing together never end with a successful reserve and no seat.
 *
 * Requires Docker, like {@link SeatReservationConcurrencyTest}.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false"
})
@Testcontainers
@DisplayName("Deleting a seat")
class SeatDeleteGuardTest {

    private static final int SCREEN = 1;
    private static final int SHOW = 201;
    private static final int RACE_ROUNDS = 40;

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @Autowired
    private SeatService seatService;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private SeatReservationRepository reservationRepository;

    @BeforeEach
    void clean() {
        // Reservations go with their seats (ON DELETE CASCADE).
        seatRepository.deleteAll();
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

    @Test
    @DisplayName("a seat nobody holds is deleted")
    void freeSeatIsDeleted() {
        Integer seat = createSeat("A1", SeatStatus.AVAILABLE);

        seatService.deleteSeat(seat);

        assertThat(seatRepository.existsById(seat)).isFalse();
    }

    @Test
    @DisplayName("a blocked seat nobody holds is deleted")
    void blockedSeatIsDeleted() {
        Integer seat = createSeat("A1", SeatStatus.BLOCKED);

        seatService.deleteSeat(seat);

        assertThat(seatRepository.existsById(seat)).isFalse();
    }

    @Test
    @DisplayName("a reserved seat is refused with 409 and keeps its reservation")
    void reservedSeatIsKept() {
        Integer seat = createSeat("A1", SeatStatus.AVAILABLE);
        seatService.reserveSeat(seat, SHOW);

        assertThrows(SeatInUseException.class, () -> seatService.deleteSeat(seat));

        assertThat(seatRepository.existsById(seat)).isTrue();
        assertThat(reservationRepository.existsBySeatIdAndShowtimeId(seat, SHOW)).isTrue();
    }

    @Test
    @DisplayName("once its booking releases it, the seat can be deleted")
    void releasedSeatIsDeleted() {
        Integer seat = createSeat("A1", SeatStatus.AVAILABLE);
        seatService.reserveSeat(seat, SHOW);
        seatService.releaseSeat(seat, SHOW);

        seatService.deleteSeat(seat);

        assertThat(seatRepository.existsById(seat)).isFalse();
    }

    @Test
    @DisplayName("a pre-V4 BOOKED seat is refused with 409")
    void legacyBookedSeatIsKept() {
        Integer seat = createSeat("A1", SeatStatus.BOOKED);

        assertThrows(SeatInUseException.class, () -> seatService.deleteSeat(seat));

        assertThat(seatRepository.existsById(seat)).isTrue();
    }

    @Test
    @DisplayName("a seat that doesn't exist is 404")
    void missingSeatIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> seatService.deleteSeat(999_999));
    }

    @Test
    @DisplayName("a reserve and a delete racing never leave a reservation without its seat")
    void reserveAndDeleteRace() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < RACE_ROUNDS; round++) {
                Integer seat = createSeat("R" + round, SeatStatus.AVAILABLE);
                CountDownLatch startLine = new CountDownLatch(1);

                Future<Boolean> reserved = pool.submit(() -> {
                    startLine.await();
                    try {
                        seatService.reserveSeat(seat, SHOW);
                        return true;
                    } catch (SeatNotAvailableException | ResourceNotFoundException lost) {
                        return false;
                    }
                });
                Future<Boolean> deleted = pool.submit(() -> {
                    startLine.await();
                    try {
                        seatService.deleteSeat(seat);
                        return true;
                    } catch (SeatInUseException lost) {
                        return false;
                    }
                });

                startLine.countDown();
                boolean reserveWon = reserved.get(30, TimeUnit.SECONDS);
                boolean deleteWon = deleted.get(30, TimeUnit.SECONDS);

                // Exactly one side wins, and the database agrees with it.
                assertThat(reserveWon).as("round %d: exactly one winner", round).isNotEqualTo(deleteWon);
                assertThat(seatRepository.existsById(seat)).as("round %d: seat kept", round).isEqualTo(reserveWon);
                assertThat(reservationRepository.existsBySeatIdAndShowtimeId(seat, SHOW))
                        .as("round %d: reservation kept", round).isEqualTo(reserveWon);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
