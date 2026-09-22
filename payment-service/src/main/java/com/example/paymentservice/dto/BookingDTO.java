package com.example.paymentservice.dto;

import java.math.BigDecimal;

/**
 * The slice of booking-service's response payment-service needs to charge
 * for a booking: who owns it, whether it can still be paid, and how much.
 * Boot disables FAIL_ON_UNKNOWN_PROPERTIES, so the other fields are ignored.
 *
 * bookingStatus is a String rather than a copy of booking-service's enum —
 * this service only ever asks "is it PENDING".
 */
public record BookingDTO(

        Integer bookingId,

        Integer userId,

        String bookingStatus,

        BigDecimal totalAmount

) {
}
