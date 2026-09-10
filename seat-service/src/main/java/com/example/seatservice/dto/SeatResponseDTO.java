package com.example.seatservice.dto;

import com.example.seatservice.entity.SeatStatus;

import java.time.LocalDateTime;

public record SeatResponseDTO(

        Integer seatId,

        Integer screenId,

        String seatNumber,

        String seatType,

        SeatStatus status,

        LocalDateTime createdAt

) {
}
