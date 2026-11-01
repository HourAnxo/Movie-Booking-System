package com.example.seatservice.service;

import com.example.seatservice.dto.SeatRequestDTO;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Editing a seat must never undo a reservation. A full-entity save writes
 * every column back, so an edit that read the seat as AVAILABLE and saved
 * after a booking reserved it would free the seat for a second booking.
 *
 * Same setup as SeatReservationConcurrencyTest: real MySQL via
 * Testcontainers (needs Docker), Eureka off.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false"
})
@Testcontainers
@DisplayName("Seat edits keep the reservation status")
class SeatUpdateKeepsStatusTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @Autowired
    private SeatService seatService;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Integer seatId;

    @BeforeEach
    void createOneAvailableSeat() {

        seatRepository.deleteAll();

        seatId = seatRepository.save(
                Seat.builder()
                        .screenId(1)
                        .seatNumber("A1")
                        .seatType("STANDARD")
                        .status(SeatStatus.AVAILABLE)
                        .price(new BigDecimal("5.00"))
                        .createdAt(LocalDateTime.now())
                        .build()
        ).getSeatId();
    }

    private Seat reload() {
        return seatRepository.findById(seatId).orElseThrow();
    }


    @Test
    @DisplayName("a reserve that lands mid-edit is not overwritten by the edit")
    void reserveDuringEditSurvives() throws Exception {

        ExecutorService otherThread = Executors.newSingleThreadExecutor();

        try {
            // Same shape as updateSeat: one transaction that reads the seat,
            // changes a field and saves.
            transactionTemplate.executeWithoutResult(status -> {

                // The edit reads the seat while it is still AVAILABLE...
                Seat seat = seatRepository.findById(seatId).orElseThrow();

                // ...a booking reserves it on another connection and
                // commits in the meantime...
                try {
                    otherThread.submit(() -> seatService.reserveSeat(seatId))
                            .get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }

                // ...then the edit saves. With @DynamicUpdate the UPDATE
                // carries only price, so it cannot write AVAILABLE back.
                seat.setPrice(new BigDecimal("9.00"));
                seatRepository.save(seat);
            });
        } finally {
            otherThread.shutdownNow();
        }

        Seat seat = reload();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.BOOKED);
        assertThat(seat.getPrice()).isEqualByComparingTo("9.00");
    }


    @Test
    @DisplayName("changing the price of a booked seat keeps it booked")
    void priceUpdateKeepsBooked() {

        seatService.reserveSeat(seatId);

        seatService.updateSeatPrice(seatId, new BigDecimal("12.50"));

        Seat seat = reload();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.BOOKED);
        assertThat(seat.getPrice()).isEqualByComparingTo("12.50");
        assertThat(seat.getSeatNumber()).isEqualTo("A1");
        assertThat(seat.getSeatType()).isEqualTo("STANDARD");
    }


    @Test
    @DisplayName("an edit cannot free a booked seat")
    void editCannotReleaseBookedSeat() {

        seatService.reserveSeat(seatId);

        assertThrows(SeatNotAvailableException.class, () ->
                seatService.updateSeat(seatId, new SeatRequestDTO(
                        1, "A1", "VIP", SeatStatus.AVAILABLE, null
                ))
        );

        // The whole edit rolled back, not just the status.
        Seat seat = reload();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.BOOKED);
        assertThat(seat.getSeatType()).isEqualTo("STANDARD");
    }


    @Test
    @DisplayName("an edit cannot book a seat")
    void editCannotBookSeat() {

        assertThrows(IllegalArgumentException.class, () ->
                seatService.updateSeat(seatId, new SeatRequestDTO(
                        1, "A1", "STANDARD", SeatStatus.BOOKED, null
                ))
        );

        assertThat(reload().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }


    @Test
    @DisplayName("an edit can still block an available seat and change fields")
    void editCanBlockAvailableSeat() {

        seatService.updateSeat(seatId, new SeatRequestDTO(
                1, "A1", "VIP", SeatStatus.BLOCKED, new BigDecimal("10.00")
        ));

        Seat seat = reload();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.BLOCKED);
        assertThat(seat.getSeatType()).isEqualTo("VIP");
        assertThat(seat.getPrice()).isEqualByComparingTo("10.00");
    }


    @Test
    @DisplayName("an edit with no status leaves a booked seat booked")
    void editWithoutStatusKeepsBooked() {

        seatService.reserveSeat(seatId);

        seatService.updateSeat(seatId, new SeatRequestDTO(
                1, "A1", "VIP", null, null
        ));

        Seat seat = reload();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.BOOKED);
        assertThat(seat.getSeatType()).isEqualTo("VIP");
        assertThat(seat.getPrice()).isEqualByComparingTo("5.00");
    }
}
