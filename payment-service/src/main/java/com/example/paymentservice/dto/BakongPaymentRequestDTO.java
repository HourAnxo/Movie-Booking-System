package com.example.paymentservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Only the booking. The amount is deliberately absent — it is read from the
 * booking, so a caller cannot choose what they are charged.
 */
public record BakongPaymentRequestDTO(

        @NotNull(message = "is required")
        @Positive(message = "must be a positive id")
        Integer bookingId

) {
}
