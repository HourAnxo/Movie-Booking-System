package com.example.paymentservice.dto;

import com.example.paymentservice.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponseDTO(

        Integer paymentId,

        Integer bookingId,

        BigDecimal amount,

        String paymentMethod,

        PaymentStatus paymentStatus,

        String transactionId,

        LocalDateTime createdAt,

        LocalDateTime updatedAt

) {
}