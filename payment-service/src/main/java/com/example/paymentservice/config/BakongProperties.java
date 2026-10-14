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
 * @param mock          fake Bakong for development: no Open API calls, and
 *                      POST /api/payments/{id}/bakong/simulate settles a QR
 *                      instead of real money. Never enable it in production.
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
        Duration qrTtl,

        @DefaultValue("false")
        boolean mock

) {

    /**
     * Receiving account the mock QRs are built for. Not a real Bakong
     * account, so a QR scanned by a real bank app cannot send money anywhere.
     */
    public static final String MOCK_ACCOUNT_ID = "cinebook@mock";

    /**
     * Mock mode always uses MOCK_ACCOUNT_ID, even when BAKONG_ACCOUNT_ID is
     * set: a mock QR carrying the real account would be payable for real.
     */
    public BakongProperties {
        if (mock) {
            accountId = MOCK_ACCOUNT_ID;
        }
    }

    /**
     * False until a token and receiving account have been configured, or
     * mock mode is on.
     */
    public boolean isConfigured() {
        return mock || apiToken != null && !apiToken.isBlank()
                && accountId != null && !accountId.isBlank();
    }
}
