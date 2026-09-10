package com.example.paymentservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PaymentRequestDTO(

        @NotNull(message = "is required")
        @Positive(message = "must be a positive id")
        Integer bookingId,

        // DECIMAL(10,2) in payments.amount.
        @NotNull(message = "is required")
        @DecimalMin(value = "0.00", message = "must not be negative")
        @Digits(integer = 8, fraction = 2,
                message = "must have at most 8 digits and 2 decimal places")
        BigDecimal amount,

        @NotBlank(message = "is required")
        @Size(max = 50, message = "must be at most 50 characters")
        String paymentMethod
) {
}
