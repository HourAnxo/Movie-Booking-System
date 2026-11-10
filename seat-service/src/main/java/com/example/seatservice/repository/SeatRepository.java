package com.example.seatservice.repository;

import com.example.seatservice.entity.Seat;
import com.example.seatservice.entity.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SeatRepository
        extends JpaRepository<Seat, Integer> {

    List<Seat> findByScreenId(Integer screenId);

    List<Seat> findByScreenIdAndStatus(
            Integer screenId,
            SeatStatus status
    );

    /**
     * Conditional status change, executed as a single atomic UPDATE:
     * {@code ... WHERE seat_id = ? AND status = ?}.
     *
     * This is what stops two concurrent bookings from taking the same
     * seat. A read-then-write in Java would let both callers see
     * AVAILABLE before either writes. Returns the number of rows
     * changed — 0 means the seat was not in the expected state and the
     * caller lost the race.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Seat s
               SET s.status = :toStatus
             WHERE s.seatId = :seatId
               AND s.status = :fromStatus
            """)
    int compareAndSetStatus(
            @Param("seatId") Integer seatId,
            @Param("fromStatus") SeatStatus fromStatus,
            @Param("toStatus") SeatStatus toStatus
    );

    /**
     * Changes only the price column. A load-then-save would write every
     * column back, including a stale status, and could undo a concurrent
     * reserve (BOOKED -> AVAILABLE).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Seat s
               SET s.price = :price
             WHERE s.seatId = :seatId
            """)
    int updatePrice(
            @Param("seatId") Integer seatId,
            @Param("price") java.math.BigDecimal price
    );

    /**
     * Deletes the seat only if no live booking holds it, in one statement.
     * A reservation row exists exactly while its booking is PENDING or
     * CONFIRMED (cancel deletes it), and a pre-V4 booking shows up as status
     * BOOKED. Either way the seat must stay, or the booking would point at a
     * seat that no longer exists (and the FK cascade would silently drop its
     * reservation).
     *
     * Never split this into "check for reservations, then delete": a
     * reserve landing in between would be cascaded away. Returns 0 when the
     * seat is missing or still held; the caller tells the two apart.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            DELETE FROM seats
             WHERE seat_id = :seatId
               AND status <> 'BOOKED'
               AND NOT EXISTS (
                     SELECT 1 FROM seat_reservations r
                      WHERE r.seat_id = :seatId
                   )
            """, nativeQuery = true)
    int deleteIfUnreserved(@Param("seatId") Integer seatId);
}
