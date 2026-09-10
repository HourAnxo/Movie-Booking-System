package com.example.paymentservice.service;

import com.example.paymentservice.client.BookingServiceClient;
import com.example.paymentservice.dto.PaymentRequestDTO;
import com.example.paymentservice.dto.PaymentResponseDTO;
import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.entity.PaymentStatus;
import com.example.paymentservice.exception.ResourceNotFoundException;
import com.example.paymentservice.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingServiceClient bookingServiceClient;

    public PaymentServiceImpl(
            PaymentRepository paymentRepository,
            BookingServiceClient bookingServiceClient
    ) {
        this.paymentRepository = paymentRepository;
        this.bookingServiceClient = bookingServiceClient;
    }

    @Override
    public PaymentResponseDTO createPayment(
            PaymentRequestDTO request
    ) {

        Payment payment = Payment.builder()
                .bookingId(request.bookingId())
                .amount(request.amount())
                .paymentMethod(request.paymentMethod())
                .paymentStatus(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Payment savedPayment =
                paymentRepository.save(payment);

        return mapToResponse(savedPayment);
    }

    @Override
    public List<PaymentResponseDTO> getAllPayments() {

        return paymentRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public PaymentResponseDTO getPaymentById(
            Integer id
    ) {

        return mapToResponse(
                findPaymentById(id)
        );
    }

    @Override
    public List<PaymentResponseDTO> getPaymentsByBookingId(
            Integer bookingId
    ) {

        return paymentRepository
                .findByBookingId(bookingId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Generic status setter. Kept for manual correction, but it does NOT
     * notify booking-service — use the explicit transitions below so the
     * booking always follows the payment.
     */
    @Override
    public PaymentResponseDTO updatePaymentStatus(
            Integer id,
            PaymentStatus status
    ) {

        return mapToResponse(
                applyStatus(findPaymentById(id), status)
        );
    }

    // ================= SAGA CALLBACKS =================

    /**
     * Settles the payment and confirms the booking. The booking call runs
     * after the payment is persisted, so a booking-service outage cannot
     * lose a payment we have already taken — it surfaces as 502 and the
     * booking can be confirmed by retrying.
     */
    @Override
    public PaymentResponseDTO markPaymentAsPaid(
            Integer id,
            String transactionId
    ) {

        Payment payment = findPaymentById(id);

        payment.setTransactionId(transactionId);

        Payment paidPayment =
                applyStatus(payment, PaymentStatus.PAID);

        bookingServiceClient.confirmBooking(
                paidPayment.getBookingId()
        );

        return mapToResponse(paidPayment);
    }

    @Override
    public PaymentResponseDTO markPaymentAsFailed(
            Integer id
    ) {

        return releaseBooking(id, PaymentStatus.FAILED);
    }

    @Override
    public PaymentResponseDTO cancelPayment(
            Integer id
    ) {

        return releaseBooking(id, PaymentStatus.CANCELLED);
    }

    @Override
    public PaymentResponseDTO refundPayment(
            Integer id
    ) {

        return releaseBooking(id, PaymentStatus.REFUNDED);
    }

    /**
     * The three terminal states that mean "this seat was not paid for":
     * set the payment status, then cancel the booking, which releases the
     * seat back to AVAILABLE.
     */
    private PaymentResponseDTO releaseBooking(
            Integer id,
            PaymentStatus status
    ) {

        Payment payment =
                applyStatus(findPaymentById(id), status);

        bookingServiceClient.cancelBooking(
                payment.getBookingId()
        );

        return mapToResponse(payment);
    }

    private Payment applyStatus(
            Payment payment,
            PaymentStatus status
    ) {

        payment.setPaymentStatus(status);
        payment.setUpdatedAt(LocalDateTime.now());

        return paymentRepository.save(payment);
    }

    private Payment findPaymentById(
            Integer id
    ) {

        return paymentRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Payment", id)
                );
    }

    private PaymentResponseDTO mapToResponse(
            Payment payment
    ) {

        return new PaymentResponseDTO(
                payment.getPaymentId(),
                payment.getBookingId(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getPaymentStatus(),
                payment.getTransactionId(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
