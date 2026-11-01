package com.example.seatservice.repository;

import com.example.seatservice.entity.SeatReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SeatReservationRepository
        extends JpaRepository<SeatReservation, Integer> {

    /**
     * Holds the seat for the showtime in one statement. The row is inserted
     * only if the seat exists and is AVAILABLE (not BLOCKED, not a legacy
     * BOOKED), so 0 means "this seat cannot be sold".
     *
     * Two callers racing for the same (showtime, seat) are separated by the
     * UNIQUE key: the loser gets a DataIntegrityViolationException. Never
     * replace this with "check for a row, then save": both callers would see
     * no row before either inserts.
     *
     * Runs in its own transaction on purpose. A duplicate-key failure inside
     * a caller's transaction would mark it rollback-only, and the caller
     * could no longer turn it into a clean 409.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO seat_reservations (showtime_id, seat_id, created_at)
            SELECT :showtimeId, s.seat_id, NOW()
              FROM seats s
             WHERE s.seat_id = :seatId
               AND s.status = 'AVAILABLE'
            """, nativeQuery = true)
    int reserve(
            @Param("seatId") Integer seatId,
            @Param("showtimeId") Integer showtimeId
    );

    /**
     * Frees the seat for that showtime. Deleting a row that is not there is
     * a no-op, which keeps release idempotent.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM SeatReservation r
             WHERE r.seatId = :seatId
               AND r.showtimeId = :showtimeId
            """)
    int release(
            @Param("seatId") Integer seatId,
            @Param("showtimeId") Integer showtimeId
    );

    boolean existsBySeatIdAndShowtimeId(Integer seatId, Integer showtimeId);

    @Query("""
            SELECT r.seatId FROM SeatReservation r
             WHERE r.showtimeId = :showtimeId
            """)
    List<Integer> findSeatIdsByShowtimeId(
            @Param("showtimeId") Integer showtimeId
    );
}
