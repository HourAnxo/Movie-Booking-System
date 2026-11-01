-- Seats are booked per showtime, not per screen.
--
-- Until now a reservation flipped seats.status to BOOKED, so booking A5 for
-- the 2 PM show also took A5 away from the 8 PM show and every later show on
-- that screen. A reservation is now one row here, keyed by the showtime.
-- No row for (showtime, seat) means the seat is free for that show, so a new
-- showtime starts with every seat available and nothing has to be generated
-- or reset.
--
-- The UNIQUE key is the double-booking guard: of several concurrent inserts
-- for the same (showtime, seat) exactly one succeeds, the rest get a
-- duplicate-key error.
--
-- seats.status keeps AVAILABLE and BLOCKED (a broken seat, for every show).
-- BOOKED rows written before this migration stay as they are: they still
-- belong to a live booking, which frees them when it is cancelled.
CREATE TABLE seat_reservations (
    reservation_id INT AUTO_INCREMENT PRIMARY KEY,

    showtime_id    INT NOT NULL,

    seat_id        INT NOT NULL,

    created_at     DATETIME DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_seat_reservations_showtime_seat
        UNIQUE (showtime_id, seat_id),

    CONSTRAINT fk_seat_reservations_seat
        FOREIGN KEY (seat_id) REFERENCES seats (seat_id)
            ON DELETE CASCADE
);
