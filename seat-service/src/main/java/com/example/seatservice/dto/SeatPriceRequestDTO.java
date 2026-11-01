package com.example.seatservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record SeatPriceRequestDTO(

        @NotNull(message = "is required")
        @Positive(message = "must be greater than zero")
        BigDecimal price
) {
}
