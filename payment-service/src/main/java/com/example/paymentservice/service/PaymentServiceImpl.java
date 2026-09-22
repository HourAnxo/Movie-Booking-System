package com.example.paymentservice.service;

import com.example.paymentservice.client.BakongApiClient;
import com.example.paymentservice.client.BookingServiceClient;
import com.example.paymentservice.config.BakongProperties;
import com.example.paymentservice.dto.BakongPaymentResponseDTO;
import com.example.paymentservice.dto.BakongTransactionDTO;
import com.example.paymentservice.dto.BookingDTO;
import com.example.paymentservice.dto.PaymentRequestDTO;
import com.example.paymentservice.dto.PaymentResponseDTO;
import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.entity.PaymentStatus;
import com.example.paymentservice.exception.BakongNotConfiguredException;
import com.example.paymentservice.exception.PaymentForbiddenException;
import com.example.paymentservice.exception.PaymentStateException;
import com.example.paymentservice.exception.ResourceNotFoundException;
import com.example.paymentservice.repository.PaymentRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentServiceImpl.class);

    private static final String BAKONG_METHOD = "BAKONG";

    /**
     * Bakong can take a few seconds to report a transaction. A customer who
     * pays in the last moment of the QR's life must not have their booking
     * cancelled because we asked too early, so expiry waits this long past
     * expires_at — with one final check — before releasing the seat.
     */
    private static final Duration EXPIRY_GRACE = Duration.ofSeconds(30);

    /** How long a failed booking confirmation keeps being retried. */
    private static final Duration CONFIRM_RETRY_WINDOW = Duration.ofHours(24);

    private final PaymentRepository paymentRepository;
    private final BookingServiceClient bookingServiceClient;
    private final BakongApiClient bakongApiClient;
    private final KhqrGenerator khqrGenerator;
    private final BakongProperties bakongProperties;

    public PaymentServiceImpl(
            PaymentRepository paymentRepository,
            BookingServiceClient bookingServiceClient,
            BakongApiClient bakongApiClient,
            KhqrGenerator khqrGenerator,
            BakongProperties bakongProperties
    ) {
        this.paymentRepository = paymentRepository;
        this.bookingServiceClient = bookingServiceClient;
        this.bakongApiClient = bakongApiClient;
        this.khqrGenerator = khqrGenerator;
        this.bakongProperties = bakongProperties;
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
     * Manual settlement — ADMIN only at the gateway, for reconciling a
     * payment taken outside Bakong. Customers' payments become PAID only
     * through {@link #checkBakongPayment} seeing the money arrive.
     *
     * Only a PENDING payment can be settled, and the change is a
     * conditional UPDATE, so this cannot race the Bakong poller into
     * confirming the booking twice.
     */
    @Override
    public PaymentResponseDTO markPaymentAsPaid(
            Integer id,
            String transactionId
    ) {

        findPaymentById(id);

        int changed = paymentRepository.markPaidIfPending(
                id,
                null,
                transactionId,
                LocalDateTime.now()
        );

        if (changed == 0) {
            throw new PaymentStateException(
                    "Payment " + id + " is not PENDING and cannot be marked paid"
            );
        }

        Payment paidPayment = findPaymentById(id);

        bookingServiceClient.confirmBooking(
                paidPayment.getBookingId()
        );

        paymentRepository.markBookingConfirmed(id, LocalDateTime.now());

        return mapToResponse(findPaymentById(id));
    }

    @Override
    public PaymentResponseDTO markPaymentAsFailed(
            Integer id
    ) {

        return releaseBooking(id, PaymentStatus.PENDING, PaymentStatus.FAILED);
    }

    @Override
    public PaymentResponseDTO cancelPayment(
            Integer id
    ) {

        return releaseBooking(id, PaymentStatus.PENDING, PaymentStatus.CANCELLED);
    }

    @Override
    public PaymentResponseDTO refundPayment(
            Integer id
    ) {

        return releaseBooking(id, PaymentStatus.PAID, PaymentStatus.REFUNDED);
    }

    /**
     * The three terminal states that mean "this seat was not paid for":
     * set the payment status, then cancel the booking, which releases the
     * seat back to AVAILABLE.
     *
     * Each is only legal from one state. Without that, a customer could
     * "cancel" a payment that had already been PAID and get their seat
     * released with no refund ever recorded.
     */
    private PaymentResponseDTO releaseBooking(
            Integer id,
            PaymentStatus from,
            PaymentStatus to
    ) {

        findPaymentById(id);

        int changed = paymentRepository.compareAndSetStatus(
                id, from, to, LocalDateTime.now()
        );

        if (changed == 0) {
            throw new PaymentStateException(
                    "Payment " + id + " must be " + from
                            + " to become " + to
            );
        }

        Payment payment = findPaymentById(id);

        bookingServiceClient.cancelBooking(
                payment.getBookingId()
        );

        return mapToResponse(payment);
    }

    // ================= BAKONG =================

    /**
     * Issues a KHQR for a booking.
     *
     * The amount is the booking's own total — which booking-service took
     * from the seat's price — never a value from the request. Only the
     * booking's owner may pay for it.
     *
     * Reopening the checkout returns the same unexpired QR rather than a new
     * one: two live QRs for one booking could both be paid.
     */
    @Override
    public BakongPaymentResponseDTO createBakongPayment(
            Integer bookingId,
            Integer callerUserId,
            boolean callerIsAdmin
    ) {

        requireBakongConfigured();

        BookingDTO booking = bookingServiceClient.getBooking(bookingId);

        if (!callerIsAdmin
                && (callerUserId == null
                || !callerUserId.equals(booking.userId()))) {
            throw new PaymentForbiddenException(
                    "Booking " + bookingId + " does not belong to the caller"
            );
        }

        if (!paymentRepository
                .findByBookingIdAndPaymentStatus(bookingId, PaymentStatus.PAID)
                .isEmpty()) {
            throw new PaymentStateException(
                    "Booking " + bookingId + " has already been paid"
            );
        }

        // An existing QR: settle or expire it first, then either hand it
        // back or refuse — never issue a second one alongside it.
        for (Payment existing : paymentRepository.findByBookingIdAndPaymentStatus(
                bookingId, PaymentStatus.PENDING)) {

            if (existing.getMd5() == null) {
                continue;
            }

            Payment current = verifyBakongPayment(existing);

            switch (current.getPaymentStatus()) {
                case PENDING -> {
                    return mapToBakongResponse(current);
                }
                case PAID -> throw new PaymentStateException(
                        "Booking " + bookingId + " has already been paid"
                );
                default -> throw new PaymentStateException(
                        "The QR for booking " + bookingId
                                + " expired and the booking was cancelled"
                );
            }
        }

        if (!"PENDING".equals(booking.bookingStatus())) {
            throw new PaymentStateException(
                    "Booking " + bookingId + " is "
                            + booking.bookingStatus() + " and cannot be paid"
            );
        }

        BigDecimal amount = booking.totalAmount();

        if (amount == null || amount.signum() <= 0) {
            throw new PaymentStateException(
                    "Booking " + bookingId + " has no amount to charge"
            );
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(bakongProperties.qrTtl());

        KhqrGenerator.Khqr khqr = khqrGenerator.generate(
                "BK" + bookingId + "-" + (System.currentTimeMillis() / 1000),
                amount,
                expiresAt.atZone(ZoneId.systemDefault()).toInstant()
        );

        Payment payment = Payment.builder()
                .bookingId(bookingId)
                .amount(amount)
                .currency(bakongProperties.currency())
                .paymentMethod(BAKONG_METHOD)
                .paymentStatus(PaymentStatus.PENDING)
                .qrString(khqr.qr())
                .md5(khqr.md5())
                .expiresAt(expiresAt)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return mapToBakongResponse(paymentRepository.save(payment));
    }

    /**
     * What the checkout screen polls. Asks Bakong whether this QR has been
     * paid and moves the payment on if so; otherwise just reports.
     *
     * A Bakong outage surfaces as 502/503 and changes nothing — we cannot
     * tell "unpaid" from "cannot check", so we neither settle nor expire.
     */
    @Override
    public BakongPaymentResponseDTO checkBakongPayment(
            Integer paymentId
    ) {

        Payment payment = findPaymentById(paymentId);

        if (payment.getMd5() == null) {
            throw new IllegalArgumentException(
                    "Payment " + paymentId + " is not a Bakong payment"
            );
        }

        if (payment.getPaymentStatus() == PaymentStatus.PENDING) {
            requireBakongConfigured();
            payment = verifyBakongPayment(payment);
        }

        return mapToBakongResponse(payment);
    }

    /**
     * Background pass for customers who are no longer polling: settle what
     * has been paid, expire what never will be, and retry booking
     * confirmations that failed when the money first arrived.
     */
    @Override
    public void sweepBakongPayments() {

        if (!bakongProperties.isConfigured()) {
            return;
        }

        for (Payment payment : paymentRepository
                .findByPaymentStatusAndMd5IsNotNull(PaymentStatus.PENDING)) {

            try {
                verifyBakongPayment(payment);
            } catch (CallNotPermittedException ex) {
                // Bakong's breaker is open; every remaining check would be
                // refused too. Try again next sweep.
                log.warn("Bakong circuit open — sweep stopped early");
                return;
            } catch (RuntimeException ex) {
                log.warn(
                        "Could not verify Bakong payment {}: {}",
                        payment.getPaymentId(),
                        ex.getMessage()
                );
            }
        }

        for (Payment payment : paymentRepository
                .findByPaymentStatusAndMd5IsNotNullAndBookingConfirmedAtIsNullAndPaidAtAfter(
                        PaymentStatus.PAID,
                        LocalDateTime.now().minus(CONFIRM_RETRY_WINDOW))) {

            confirmBookingFor(payment);
        }
    }

    /**
     * The single place a Bakong payment changes state. Returns the payment
     * as it is afterwards.
     */
    private Payment verifyBakongPayment(Payment payment) {

        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            return payment;
        }

        Optional<BakongTransactionDTO> transaction =
                bakongApiClient.checkTransactionByMd5(payment.getMd5());

        if (transaction.isPresent()) {

            if (matches(payment, transaction.get())) {
                settle(payment, transaction.get());
            } else {
                // The md5 proves which QR was paid, not that the right
                // money reached the right account. Leave it PENDING for a
                // human to look at rather than confirm a booking on it.
                log.error(
                        "Bakong transaction {} for payment {} does not match: "
                                + "expected {} {} to {}, got {} {} to {}",
                        transaction.get().hash(),
                        payment.getPaymentId(),
                        payment.getAmount(),
                        payment.getCurrency(),
                        bakongProperties.accountId(),
                        transaction.get().amount(),
                        transaction.get().currency(),
                        transaction.get().toAccountId()
                );
            }

        } else if (LocalDateTime.now().isAfter(
                payment.getExpiresAt().plus(EXPIRY_GRACE))) {

            expire(payment);
        }

        return findPaymentById(payment.getPaymentId());
    }

    private boolean matches(Payment payment, BakongTransactionDTO transaction) {

        return transaction.amount() != null
                && transaction.amount().compareTo(payment.getAmount()) == 0
                && payment.getCurrency() != null
                && payment.getCurrency().equalsIgnoreCase(transaction.currency())
                && bakongProperties.accountId()
                .equalsIgnoreCase(transaction.toAccountId());
    }

    private void settle(Payment payment, BakongTransactionDTO transaction) {

        int won;

        try {
            won = paymentRepository.markPaidIfPending(
                    payment.getPaymentId(),
                    transaction.hash(),
                    transaction.hash(),
                    LocalDateTime.now()
            );
        } catch (DataIntegrityViolationException ex) {
            // bakong_hash is unique: this transfer already settled another
            // payment. Never let one payment count twice.
            log.error(
                    "Bakong transaction {} already settled another payment; "
                            + "payment {} left PENDING",
                    transaction.hash(),
                    payment.getPaymentId()
            );
            return;
        }

        // 0 rows: the poller and the sweeper saw it at the same moment and
        // the other one already settled it and confirmed the booking.
        if (won == 1) {
            log.info(
                    "Payment {} PAID by Bakong transaction {}",
                    payment.getPaymentId(),
                    transaction.hash()
            );
            confirmBookingFor(payment);
        }
    }

    /**
     * Tells booking-service the booking is paid. The money has already
     * arrived, so a failure here must never undo the payment: a transient
     * failure is retried by the sweeper, and a refusal (the booking was
     * cancelled while the customer was paying) is logged for a refund.
     */
    private void confirmBookingFor(Payment payment) {

        try {
            bookingServiceClient.confirmBooking(payment.getBookingId());
            paymentRepository.markBookingConfirmed(
                    payment.getPaymentId(),
                    LocalDateTime.now()
            );
        } catch (HttpClientErrorException ex) {
            log.error(
                    "Payment {} is PAID but booking {} refused confirmation "
                            + "({}) — refund the customer manually",
                    payment.getPaymentId(),
                    payment.getBookingId(),
                    ex.getStatusCode()
            );
        } catch (RuntimeException ex) {
            log.warn(
                    "Payment {} is PAID but booking {} could not be confirmed "
                            + "yet; will retry: {}",
                    payment.getPaymentId(),
                    payment.getBookingId(),
                    ex.getMessage()
            );
        }
    }

    private void expire(Payment payment) {

        int won = paymentRepository.compareAndSetStatus(
                payment.getPaymentId(),
                PaymentStatus.PENDING,
                PaymentStatus.EXPIRED,
                LocalDateTime.now()
        );

        if (won == 0) {
            return;
        }

        log.info(
                "Payment {} EXPIRED unpaid — cancelling booking {}",
                payment.getPaymentId(),
                payment.getBookingId()
        );

        try {
            bookingServiceClient.cancelBooking(payment.getBookingId());
        } catch (RuntimeException ex) {
            log.error(
                    "Payment {} expired but booking {} could not be cancelled; "
                            + "its seat stays held until reconciled",
                    payment.getPaymentId(),
                    payment.getBookingId(),
                    ex
            );
        }
    }

    private void requireBakongConfigured() {

        if (!bakongProperties.isConfigured()) {
            throw new BakongNotConfiguredException();
        }
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

    private BakongPaymentResponseDTO mapToBakongResponse(
            Payment payment
    ) {

        boolean scannable =
                payment.getPaymentStatus() == PaymentStatus.PENDING;

        return new BakongPaymentResponseDTO(
                payment.getPaymentId(),
                payment.getBookingId(),
                payment.getPaymentStatus(),
                payment.getAmount(),
                payment.getCurrency(),
                scannable ? payment.getQrString() : null,
                payment.getExpiresAt(),
                scannable && payment.getExpiresAt() != null
                        ? Math.max(0, Duration.between(
                                LocalDateTime.now(),
                                payment.getExpiresAt()).toSeconds())
                        : 0,
                payment.getPaidAt(),
                payment.getTransactionId()
        );
    }
}
