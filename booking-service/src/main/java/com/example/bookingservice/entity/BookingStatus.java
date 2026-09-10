package com.example.bookingservice.entity;

public enum BookingStatus {

    /** Seat is held, payment not yet settled. */
    PENDING,

    /** Payment succeeded — payment-service confirmed this booking. */
    CONFIRMED,

    /** Cancelled by the user, or payment failed. Seat has been released. */
    CANCELLED
}
