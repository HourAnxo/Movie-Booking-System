-- Real Bakong KHQR payments.
--
-- qr_string / md5  the KHQR handed to the customer and its MD5, which is
--                  the key the Bakong Open API looks the transaction up by.
-- expires_at       after this an unpaid QR is EXPIRED and its seat released.
-- bakong_hash      Bakong's id for the transaction that settled this payment.
--                  UNIQUE so one bank transfer can never settle two payments.
-- paid_at          when payment-service saw the money arrive.
-- booking_confirmed_at
--                  NULL on a PAID payment means booking-service has not yet
--                  been told; the sweeper retries until it has.

ALTER TABLE payments
    ADD COLUMN currency             VARCHAR(3),
    ADD COLUMN qr_string            TEXT,
    ADD COLUMN md5                  CHAR(32),
    ADD COLUMN expires_at           DATETIME,
    ADD COLUMN paid_at              DATETIME,
    ADD COLUMN bakong_hash          VARCHAR(64),
    ADD COLUMN booking_confirmed_at DATETIME,
    ADD CONSTRAINT uk_payments_md5 UNIQUE (md5),
    ADD CONSTRAINT uk_payments_bakong_hash UNIQUE (bakong_hash);

CREATE INDEX idx_payments_status ON payments (payment_status);
CREATE INDEX idx_payments_booking ON payments (booking_id);
