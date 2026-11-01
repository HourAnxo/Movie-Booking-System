package com.example.seatservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One seat held for one showtime. The absence of a row is what makes a seat
 * available for a show, so nothing is created per showtime in advance.
 *
 * Rows are only ever inserted by SeatReservationRepository.reserve and
 * removed by release; they are never updated.
 */
@Entity
@Table(name = "seat_reservations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Integer reservationId;

    @Column(name = "showtime_id", nullable = false)
    private Integer showtimeId;

    @Column(name = "seat_id", nullable = false)
    private Integer seatId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
