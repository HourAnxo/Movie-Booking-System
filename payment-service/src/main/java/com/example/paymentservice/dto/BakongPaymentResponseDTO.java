package com.example.paymentservice.dto;

import com.example.paymentservice.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What the checkout screen needs: the KHQR string to render, how long it
 * stays payable, and the status to poll until it is final.
 *
 * qrString is null once the payment is no longer PENDING — there is nothing
 * left to scan, and showing an old QR invites a second payment.
 */
public record BakongPaymentResponseDTO(

        Integer paymentId,

        Integer bookingId,

        PaymentStatus paymentStatus,

        BigDecimal amount,

        String currency,

        String qrString,

        LocalDateTime expiresAt,

        // For the countdown. expiresAt has no zone, and a browser in
        // Phnom Penh reading a UTC container's clock would be seven hours off.
        long secondsRemaining,

        LocalDateTime paidAt,

        String transactionId

) {
}
