package com.example.bookingservice.dto;

import java.math.BigDecimal;

/**
 * The part of seat-service's reserve response booking-service cares about.
 * seat-service sends more fields; Jackson ignores the rest.
 *
 * price is what makes the booking's total trustworthy — it comes from the
 * seat row, not from the client, and payment-service charges exactly this.
 */
public record ReservedSeatDTO(

        Integer seatId,

        BigDecimal price

) {
}
