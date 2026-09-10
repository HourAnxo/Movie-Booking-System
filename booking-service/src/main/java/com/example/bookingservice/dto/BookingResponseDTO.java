package com.example.bookingservice.dto;

import com.example.bookingservice.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookingResponseDTO(

        Integer bookingId,

        Integer userId,

        Integer showtimeId,

        Integer seatId,

        BookingStatus bookingStatus,

        BigDecimal totalAmount,

        LocalDateTime createdAt

) {
}
