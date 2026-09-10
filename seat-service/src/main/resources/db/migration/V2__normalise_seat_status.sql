-- SeatStatus is now an enum (AVAILABLE, BOOKED, BLOCKED). A legacy
-- 'UNAVAILABLE' value exists in seat_db and would fail to deserialise.
UPDATE seats SET status = 'BLOCKED' WHERE status = 'UNAVAILABLE';

-- A screen cannot have two seats with the same number. Without this the
-- reserve path can still be defeated by duplicate rows for one physical seat.
ALTER TABLE seats
    ADD CONSTRAINT uq_seats_screen_seat_number
        UNIQUE (screen_id, seat_number);
