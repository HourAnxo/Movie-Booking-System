package com.example.paymentservice.service;

import com.example.paymentservice.config.BakongProperties;
import kh.gov.nbc.bakong_khqr.BakongKHQR;
import kh.gov.nbc.bakong_khqr.model.IndividualInfo;
import kh.gov.nbc.bakong_khqr.model.KHQRCurrency;
import kh.gov.nbc.bakong_khqr.model.KHQRData;
import kh.gov.nbc.bakong_khqr.model.KHQRResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Builds a dynamic KHQR with the National Bank of Cambodia's SDK.
 *
 * The QR string is EMVCo TLV: the receiving Bakong account, the exact
 * amount and currency, a bill number, an expiry timestamp and a CRC. Any
 * KHQR-member bank app (ABA, ACLEDA, Wing, Bakong…) can scan it and pay.
 *
 * The md5 of that string is what the Bakong Open API looks the resulting
 * transaction up by, so the pair (qr, md5) is stored with the payment.
 *
 * Built as an *individual* KHQR, which is what a personal Bakong account
 * receives. A merchant account would use BakongKHQR.generateMerchant with a
 * merchant id and acquiring bank instead; nothing else changes.
 */
@Component
public class KhqrGenerator {

    /** EMVCo caps the bill number at 25 characters. */
    private static final int MAX_BILL_NUMBER = 25;

    private final BakongProperties properties;

    public KhqrGenerator(BakongProperties properties) {
        this.properties = properties;
    }

    public record Khqr(String qr, String md5) {
    }

    public Khqr generate(
            String billNumber,
            BigDecimal amount,
            Instant expiresAt
    ) {

        IndividualInfo info = new IndividualInfo();
        info.setBakongAccountId(properties.accountId());
        info.setMerchantName(properties.merchantName());
        info.setMerchantCity(properties.merchantCity());
        info.setCurrency(KHQRCurrency.valueOf(properties.currency()));
        info.setAmount(amount.doubleValue());
        info.setBillNumber(truncate(billNumber));
        info.setExpirationTimestamp(expiresAt.toEpochMilli());

        KHQRResponse<KHQRData> response = BakongKHQR.generateIndividual(info);

        if (response.getKHQRStatus() == null
                || response.getKHQRStatus().getCode() != 0
                || response.getData() == null) {

            String reason = response.getKHQRStatus() == null
                    ? "no status"
                    : response.getKHQRStatus().getMessage();

            // A bad account id or merchant name in configuration, not a
            // caller mistake.
            throw new IllegalStateException(
                    "KHQR generation failed: " + reason
            );
        }

        return new Khqr(
                response.getData().getQr(),
                response.getData().getMd5()
        );
    }

    private static String truncate(String bill) {

        return bill.length() > MAX_BILL_NUMBER
                ? bill.substring(0, MAX_BILL_NUMBER)
                : bill;
    }
}
