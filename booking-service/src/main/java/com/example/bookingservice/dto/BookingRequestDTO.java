package com.example.bookingservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * These constraints run before createBooking does anything, which matters
 * more here than elsewhere: an invalid body that reached the service would
 * still have cost a remote user check and a seat reservation before the
 * insert failed, and the seat would then need releasing. Rejecting it at
 * the edge means the saga never starts.
 *
 * @Digits mirrors DECIMAL(10,2) — 8 digits before the point, 2 after.
 */
public record BookingRequestDTO(

        @NotNull(message = "is required")
        @Positive(message = "must be a positive id")
        Integer userId,

        @NotNull(message = "is required")
        @Positive(message = "must be a positive id")
        Integer showtimeId,

        @NotNull(message = "is required")
        @Positive(message = "must be a positive id")
        Integer seatId,

        @NotNull(message = "is required")
        @DecimalMin(value = "0.00", message = "must not be negative")
        @Digits(integer = 8, fraction = 2,
                message = "must have at most 8 digits and 2 decimal places")
        BigDecimal totalAmount
) {
}
