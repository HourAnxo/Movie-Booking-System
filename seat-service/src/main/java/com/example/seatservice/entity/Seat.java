package com.example.seatservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "seats",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_seats_screen_seat_number",
                columnNames = {"screen_id", "seat_number"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_id")
    private Integer seatId;

    @Column(name = "screen_id", nullable = false)
    private Integer screenId;

    @Column(name = "seat_number", nullable = false)
    private String seatNumber;

    @Column(name = "seat_type", nullable = false)
    private String seatType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status;

    @Column(name="price", nullable = false, precision = 10,scale=2)
    @Builder.Default
    private BigDecimal price= new BigDecimal("5.00");

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
