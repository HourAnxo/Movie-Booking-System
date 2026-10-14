-- At most one live (PENDING) Bakong QR per booking.
--
-- createBakongPayment checks for an existing PENDING QR before issuing one,
-- but two requests arriving together (a double click, two tabs, React
-- StrictMode running the checkout effect twice) both pass that check. Each
-- then saved its own QR, so one booking had two payable QRs, or, when both
-- came out identical, the second insert failed on uk_payments_md5 with a 500.
--
-- MySQL has no partial unique index, so a stored generated column holds the
-- booking id only while the row is a live Bakong QR and NULL otherwise;
-- UNIQUE ignores NULLs. The loser of the race now hits uk_payments_live_qr
-- and returns the winner's QR instead.
--
-- Not mapped on the entity: ddl-auto=validate only checks mapped columns.

ALTER TABLE payments
    ADD COLUMN live_qr_booking_id INT
        GENERATED ALWAYS AS (
            IF(payment_status = 'PENDING' AND md5 IS NOT NULL, booking_id, NULL)
        ) STORED,
    ADD CONSTRAINT uk_payments_live_qr UNIQUE (live_qr_booking_id);
