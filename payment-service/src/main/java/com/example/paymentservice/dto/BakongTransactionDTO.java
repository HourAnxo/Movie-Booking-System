package com.example.paymentservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * A completed transaction as the Bakong Open API reports it.
 *
 * toAccountId, amount and currency are what payment-service compares
 * against the payment before marking it PAID — the md5 alone proves which
 * QR was scanned, not that the right money reached the right account.
 * hash is Bakong's id for the transaction and is stored unique, so one
 * transfer can never settle two payments.
 *
 * ignoreUnknown because this is a third-party API that adds fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BakongTransactionDTO(

        String hash,

        String fromAccountId,

        String toAccountId,

        String currency,

        BigDecimal amount,

        String description,

        Long createdDateMs,

        Long acknowledgedDateMs,

        String externalRef

) {
}
