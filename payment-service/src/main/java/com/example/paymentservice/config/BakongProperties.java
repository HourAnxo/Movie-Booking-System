package com.example.paymentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Everything payment-service needs to take real Bakong payments.
 *
 * apiToken and accountId have no usable default on purpose. The token comes
 * from registering at https://api-bakong.nbc.gov.kh and expires roughly
 * every 90 days; it belongs in the environment (BAKONG_API_TOKEN), never in
 * a checked-in file. accountId is the Bakong account the money lands in,
 * e.g. "yourname@aclb" — get it wrong and customers pay a stranger.
 *
 * @param apiBaseUrl    Bakong Open API root
 * @param apiToken      bearer token for the Open API
 * @param accountId     receiving Bakong account id
 * @param merchantName  name the customer's bank app shows before they pay
 * @param merchantCity  city encoded into the QR
 * @param currency      USD or KHR; must match how prices are stored
 * @param qrTtl         how long a QR can be paid before the booking is released
 *
 * The background sweep interval is bakong.sweep-interval-ms, read directly
 * by BakongPaymentSweeper's @Scheduled.
 */
@ConfigurationProperties("bakong")
public record BakongProperties(

        @DefaultValue("https://api-bakong.nbc.gov.kh")
        String apiBaseUrl,

        String apiToken,

        String accountId,

        @DefaultValue("CineBook")
        String merchantName,

        @DefaultValue("Phnom Penh")
        String merchantCity,

        @DefaultValue("USD")
        String currency,

        @DefaultValue("5m")
        Duration qrTtl

) {

    /** False until a token and receiving account have been configured. */
    public boolean isConfigured() {
        return apiToken != null && !apiToken.isBlank()
                && accountId != null && !accountId.isBlank();
    }
}
