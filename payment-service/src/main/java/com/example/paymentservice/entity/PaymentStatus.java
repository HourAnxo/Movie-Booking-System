package com.example.paymentservice.entity;

public enum PaymentStatus {

    PENDING,
    PAID,
    FAILED,
    CANCELLED,
    REFUNDED,

    /** A Bakong QR nobody paid before it expired; its booking is cancelled. */
    EXPIRED
}
