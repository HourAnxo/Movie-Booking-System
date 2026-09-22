package com.example.paymentservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Integer paymentId;

    @Column(name = "booking_id", nullable = false)
    private Integer bookingId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_method", nullable = false)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ================= BAKONG (V2) =================
    // All null for a payment that was not taken through Bakong.

    @Column(length = 3)
    private String currency;

    @Column(name = "qr_string", columnDefinition = "TEXT")
    private String qrString;

    @Column(columnDefinition = "CHAR(32)", unique = true)
    private String md5;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "bakong_hash", length = 64, unique = true)
    private String bakongHash;

    @Column(name = "booking_confirmed_at")
    private LocalDateTime bookingConfirmedAt;
}