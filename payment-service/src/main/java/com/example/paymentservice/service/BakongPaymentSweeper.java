package com.example.paymentservice.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Bakong does not notify us when a QR is paid, and the checkout screen only
 * polls while it is open. This covers everyone else: the customer who pays
 * and closes the tab still gets a confirmed booking, and the one who walks
 * away still has their seat released when the QR expires.
 *
 * fixedDelay, not fixedRate — a slow Bakong response must not stack sweeps.
 */
@Component
public class BakongPaymentSweeper {

    private final PaymentService paymentService;

    public BakongPaymentSweeper(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Scheduled(
            initialDelayString = "${bakong.sweep-interval-ms:30000}",
            fixedDelayString = "${bakong.sweep-interval-ms:30000}"
    )
    public void sweep() {
        paymentService.sweepBakongPayments();
    }
}
