package com.example.paymentservice.repository;

import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository
        extends JpaRepository<Payment, Integer> {

    List<Payment> findByBookingId(Integer bookingId);

    List<Payment> findByBookingIdAndPaymentStatus(
            Integer bookingId,
            PaymentStatus paymentStatus
    );

    /** Unsettled Bakong QRs — what the sweeper checks. */
    List<Payment> findByPaymentStatusAndMd5IsNotNull(
            PaymentStatus paymentStatus
    );

    /**
     * Bakong payments that were paid but whose booking-service confirmation
     * never went through. Bounded by paidAt so a confirmation that is being
     * refused outright (the booking was cancelled meanwhile) is not retried
     * forever — that case is logged for a manual refund instead.
     */
    List<Payment>
    findByPaymentStatusAndMd5IsNotNullAndBookingConfirmedAtIsNullAndPaidAtAfter(
            PaymentStatus paymentStatus,
            LocalDateTime paidAfter
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Payment p
               SET p.bookingConfirmedAt = :now
             WHERE p.paymentId = :paymentId
            """)
    int markBookingConfirmed(
            @Param("paymentId") Integer paymentId,
            @Param("now") LocalDateTime now
    );

    /**
     * Conditional status change, executed as a single atomic UPDATE:
     * {@code ... WHERE payment_id = ? AND payment_status = ?}.
     *
     * The checkout screen's polling and the background sweeper can both
     * notice the same Bakong transaction at the same moment. A read-then-
     * write would let both mark it PAID and both confirm the booking — or
     * let the sweeper EXPIRE a payment the poller has just settled. Returns
     * the rows changed; 0 means someone else got there first.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Payment p
               SET p.paymentStatus = :toStatus,
                   p.updatedAt = :now
             WHERE p.paymentId = :paymentId
               AND p.paymentStatus = :fromStatus
            """)
    int compareAndSetStatus(
            @Param("paymentId") Integer paymentId,
            @Param("fromStatus") PaymentStatus fromStatus,
            @Param("toStatus") PaymentStatus toStatus,
            @Param("now") LocalDateTime now
    );

    /**
     * PENDING -> PAID together with the evidence, in one statement, so a
     * PAID row can never exist without the Bakong transaction that paid it.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Payment p
               SET p.paymentStatus = com.example.paymentservice.entity.PaymentStatus.PAID,
                   p.bakongHash = :hash,
                   p.transactionId = :transactionId,
                   p.paidAt = :now,
                   p.updatedAt = :now
             WHERE p.paymentId = :paymentId
               AND p.paymentStatus = com.example.paymentservice.entity.PaymentStatus.PENDING
            """)
    int markPaidIfPending(
            @Param("paymentId") Integer paymentId,
            @Param("hash") String hash,
            @Param("transactionId") String transactionId,
            @Param("now") LocalDateTime now
    );
}
