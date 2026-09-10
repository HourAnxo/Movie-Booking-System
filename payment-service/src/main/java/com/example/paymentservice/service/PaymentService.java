package com.example.paymentservice.service;

import com.example.paymentservice.dto.PaymentRequestDTO;
import com.example.paymentservice.dto.PaymentResponseDTO;
import com.example.paymentservice.entity.PaymentStatus;

import java.util.List;

public interface PaymentService {

    PaymentResponseDTO createPayment(
            PaymentRequestDTO request
    );

    List<PaymentResponseDTO> getAllPayments();

    PaymentResponseDTO getPaymentById(
            Integer id
    );

    List<PaymentResponseDTO> getPaymentsByBookingId(
            Integer bookingId
    );

    PaymentResponseDTO updatePaymentStatus(
            Integer id,
            PaymentStatus status
    );

    PaymentResponseDTO markPaymentAsPaid(
            Integer id,
            String transactionId
    );

    PaymentResponseDTO markPaymentAsFailed(
            Integer id
    );

    PaymentResponseDTO cancelPayment(
            Integer id
    );

    PaymentResponseDTO refundPayment(
            Integer id
    );
}