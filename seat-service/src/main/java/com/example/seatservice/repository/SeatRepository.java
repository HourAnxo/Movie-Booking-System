package com.example.seatservice.repository;

import com.example.seatservice.entity.Seat;
import com.example.seatservice.entity.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
