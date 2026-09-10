package com.example.paymentservice.controller;

import com.example.paymentservice.dto.PaymentRequestDTO;
import com.example.paymentservice.dto.PaymentResponseDTO;
import com.example.paymentservice.entity.PaymentStatus;
import com.example.paymentservice.service.PaymentService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(
            PaymentService paymentService
    ) {
        this.paymentService = paymentService;
    }

    // =========================
    // CREATE PAYMENT
    // =========================
    @PostMapping
    public ResponseEntity<PaymentResponseDTO> createPayment(
            @Valid @RequestBody PaymentRequestDTO request
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        paymentService.createPayment(request)
                );
    }


    // =========================
    // GET ALL PAYMENTS
    // =========================
    @GetMapping
    public ResponseEntity<List<PaymentResponseDTO>> getAllPayments() {

        return ResponseEntity.ok(
                paymentService.getAllPayments()
        );
    }


    // =========================
    // GET PAYMENT BY ID
    // =========================
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponseDTO> getPaymentById(
            @PathVariable Integer id
    ) {

        return ResponseEntity.ok(
                paymentService.getPaymentById(id)
        );
    }


    // =========================
    // GET PAYMENT STATUS
    // =========================
    @GetMapping("/{id}/status")
    public ResponseEntity<PaymentResponseDTO> getPaymentStatus(
            @PathVariable Integer id
    ) {

        return ResponseEntity.ok(
                paymentService.getPaymentById(id)
        );
    }


    // =========================
    // UPDATE PAYMENT STATUS
    // =========================
    @PutMapping("/{id}/status")
    public ResponseEntity<PaymentResponseDTO> updatePaymentStatus(
            @PathVariable Integer id,
            @RequestParam PaymentStatus status
    ) {

        return ResponseEntity.ok(
                paymentService.updatePaymentStatus(id, status)
        );
    }


    // =========================
    // GET PAYMENT HISTORY BY BOOKING ID
    // =========================
    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<List<PaymentResponseDTO>>
    getPaymentsByBookingId(
            @PathVariable Integer bookingId
    ) {

        return ResponseEntity.ok(
                paymentService.getPaymentsByBookingId(bookingId)
        );
    }


    // =========================
    // MARK PAYMENT AS PAID
    // =========================
    @PutMapping("/{id}/paid")
    public ResponseEntity<PaymentResponseDTO> markPaymentAsPaid(
            @PathVariable Integer id,
            @RequestParam String transactionId
    ) {

        return ResponseEntity.ok(
                paymentService.markPaymentAsPaid(
                        id,
                        transactionId
                )
        );
    }


    // =========================
    // MARK PAYMENT AS FAILED
    // =========================
    @PutMapping("/{id}/failed")
    public ResponseEntity<PaymentResponseDTO> markPaymentAsFailed(
            @PathVariable Integer id
    ) {

        return ResponseEntity.ok(
                paymentService.markPaymentAsFailed(id)
        );
    }


    // =========================
    // CANCEL PAYMENT
    // =========================
    @PutMapping("/{id}/cancel")
    public ResponseEntity<PaymentResponseDTO> cancelPayment(
            @PathVariable Integer id
    ) {

        return ResponseEntity.ok(
                paymentService.cancelPayment(id)
        );
    }


    // =========================
    // REFUND PAYMENT
    // =========================
    @PutMapping("/{id}/refund")
    public ResponseEntity<PaymentResponseDTO> refundPayment(
            @PathVariable Integer id
    ) {

        return ResponseEntity.ok(
                paymentService.refundPayment(id)
        );
    }
}
