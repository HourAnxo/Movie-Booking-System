package com.example.seatservice.dto;

import com.example.seatservice.entity.SeatStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SeatResponseDTO(

        Integer seatId,

        Integer screenId,

        String seatNumber,

        String seatType,

        SeatStatus status,

        BigDecimal price,

        LocalDateTime createdAt

) {
}
