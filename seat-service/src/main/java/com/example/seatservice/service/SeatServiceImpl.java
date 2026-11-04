
package com.example.seatservice.service;

import com.example.seatservice.dto.SeatRequestDTO;
import com.example.seatservice.dto.SeatResponseDTO;
import com.example.seatservice.entity.Seat;
import com.example.seatservice.entity.SeatStatus;
import com.example.seatservice.exception.ResourceNotFoundException;
import com.example.seatservice.exception.SeatInUseException;
import com.example.seatservice.exception.SeatNotAvailableException;
import com.example.seatservice.repository.SeatRepository;
import com.example.seatservice.repository.SeatReservationRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SeatServiceImpl implements SeatService {

    private final SeatRepository seatRepository;
    private final SeatReservationRepository reservationRepository;

    // Default prices per seat type
    private static final Map<String, BigDecimal> DEFAULT_PRICES = Map.of(
            "STANDARD", new BigDecimal("5.00"),
            "VIP", new BigDecimal("10.00"),
            "COUPLE", new BigDecimal("15.00")
    );

    public SeatServiceImpl(
            SeatRepository seatRepository,
            SeatReservationRepository reservationRepository
    ) {
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
    }

    // RESOLVE DEFAULT PRICE
    private BigDecimal resolvePrice(
            String seatType,
            BigDecimal requestedPrice
    ) {
        if (requestedPrice != null
                && requestedPrice.compareTo(BigDecimal.ZERO) > 0) {
            return requestedPrice;
        }

        return DEFAULT_PRICES.getOrDefault(
                seatType != null ? seatType.toUpperCase() : "",
                new BigDecimal("5.00")
        );
    }

    // CREATE
    @Override
    public SeatResponseDTO createSeat(SeatRequestDTO request) {

        Seat seat = Seat.builder()
                .screenId(request.screenId())
                .seatNumber(request.seatNumber())
                .seatType(request.seatType())
                .status(
                        request.status() != null
                                ? request.status()
                                : SeatStatus.AVAILABLE
                )
                .price(resolvePrice(request.seatType(), request.price()))
                .createdAt(LocalDateTime.now())
                .build();

        return mapToResponse(seatRepository.save(seat));
    }

    // GET ALL
    @Override
    public List<SeatResponseDTO> getAllSeats() {

        return seatRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // GET BY ID
    @Override
    public SeatResponseDTO getSeatById(Integer id) {

        return mapToResponse(findSeatById(id));
    }

    // GET BY SCREEN
    @Override
    public List<SeatResponseDTO> getSeatsByScreenId(
            Integer screenId,
            Integer showtimeId
    ) {

        List<Seat> seats = seatRepository.findByScreenId(screenId);

        if (showtimeId == null) {
            return seats.stream().map(this::mapToResponse).toList();
        }

        // Availability is worked out on every read: a seat is BOOKED for
        // this show only if a reservation row exists for it. A new showtime
        // has no rows, so all its seats come back AVAILABLE.
        Set<Integer> reserved = new HashSet<>(
                reservationRepository.findSeatIdsByShowtimeId(showtimeId)
        );

        return seats.stream()
                .map(seat -> mapToResponse(
                        seat,
                        statusForShowtime(seat, reserved.contains(seat.getSeatId()))
                ))
                .toList();
    }

    // GET AVAILABLE BY SCREEN
    @Override
    public List<SeatResponseDTO> getAvailableSeatsByScreenId(
            Integer screenId,
            Integer showtimeId
    ) {

        if (showtimeId == null) {
            return seatRepository
                    .findByScreenIdAndStatus(screenId, SeatStatus.AVAILABLE)
                    .stream()
                    .map(this::mapToResponse)
                    .toList();
        }

        return getSeatsByScreenId(screenId, showtimeId)
                .stream()
                .filter(seat -> seat.status() == SeatStatus.AVAILABLE)
                .toList();
    }

    // UPDATE SEAT
    @Override
    @Transactional
    public SeatResponseDTO updateSeat(
            Integer id,
            SeatRequestDTO request
    ) {

        Seat seat = findSeatById(id);

        SeatStatus current = seat.getStatus();
        SeatStatus requested = request.status();
        boolean statusChange = requested != null && requested != current;

        // Status is owned by the booking saga. An edit may only move a seat
        // between AVAILABLE and BLOCKED; booking goes through reserve, and a
        // BOOKED seat is freed by cancelling its booking.
        if (statusChange && requested == SeatStatus.BOOKED) {
            throw new IllegalArgumentException(
                    "A seat is booked through a booking, not by editing it"
            );
        }
        if (statusChange && current == SeatStatus.BOOKED) {
            throw new SeatNotAvailableException(
                    "Seat " + id + " is booked; cancel its booking to free it"
            );
        }

        seat.setScreenId(request.screenId());
        seat.setSeatNumber(request.seatNumber());
        seat.setSeatType(request.seatType());

        // Keep existing price if no new price is provided
        if (request.price() != null
                && request.price().compareTo(BigDecimal.ZERO) > 0) {
            seat.setPrice(request.price());
        }

        // Status is never set on the entity: with @DynamicUpdate this save
        // leaves the status column out of the UPDATE entirely.
        seatRepository.save(seat);

        if (statusChange) {
            // Conditional, so a reserve that lands between our read and this
            // write wins instead of being overwritten.
            int updated = seatRepository.compareAndSetStatus(
                    id, current, requested
            );
            if (updated == 0) {
                throw new SeatNotAvailableException(
                    "Seat " + id + " changed status while being edited"
                );
            }
        }

        return mapToResponse(findSeatById(id));
    }

    // UPDATE SEAT PRICE — NEW ADMIN FEATURE
    @Override
    @Transactional
    public SeatResponseDTO updateSeatPrice(
            Integer id,
            BigDecimal price
    ) {

        if (price == null
                || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Price must be greater than zero"
            );
        }

        // 404 for a missing seat
        findSeatById(id);

        // Targeted UPDATE: never write status (or any other column) back
        seatRepository.updatePrice(id, price);

        return mapToResponse(findSeatById(id));
    }

    // RESERVE
    /**
     * Holds the seat for one showtime. The same seat stays free for every
     * other showtime on the screen.
     *
     * Not @Transactional: the insert runs in its own transaction so that a
     * duplicate key (another caller won the race) can be turned into a
     * clean 409 here instead of poisoning an outer transaction.
     */
    @Override
    public SeatResponseDTO reserveSeat(Integer id, Integer showtimeId) {

        requireShowtimeId(showtimeId);

        Seat seat = findSeatById(id);

        int inserted;
        try {
            inserted = reservationRepository.reserve(id, showtimeId);
        } catch (DataIntegrityViolationException alreadyTaken) {
            throw new SeatNotAvailableException(
                    "Seat " + id + " is already booked for showtime "
                            + showtimeId
            );
        }

        // No row inserted: the seat is BLOCKED (or a pre-V4 BOOKED seat).
        if (inserted == 0) {
            throw new SeatNotAvailableException(
                    "Seat " + id + " is not available"
            );
        }

        return mapToResponse(seat, SeatStatus.BOOKED);
    }

    // RELEASE
    /**
     * Frees the seat for one showtime. Idempotent: releasing a seat that is
     * not held for that show changes nothing.
     *
     * When no reservation row was removed, a seat still marked BOOKED is a
     * reservation made before V4 (status on the seat itself) and is freed
     * the old way. That is safe because nothing writes BOOKED any more and a
     * BOOKED seat cannot be reserved, so the only booking on that seat that
     * can release it is the one that holds it.
     */
    @Override
    @Transactional
    public SeatResponseDTO releaseSeat(Integer id, Integer showtimeId) {

        requireShowtimeId(showtimeId);

        findSeatById(id);

        int removed = reservationRepository.release(id, showtimeId);

        if (removed == 0) {
            seatRepository.compareAndSetStatus(
                    id,
                    SeatStatus.BOOKED,
                    SeatStatus.AVAILABLE
            );
        }

        Seat seat = findSeatById(id);

        return mapToResponse(seat, statusForShowtime(seat, false));
    }

    // DELETE
    @Override
    public void deleteSeat(Integer id) {

        // One conditional DELETE, so a reserve can't land between a check
        // and the delete. 0 rows: the seat is missing (404) or still held.
        if (seatRepository.deleteIfUnreserved(id) == 1) {
            return;
        }

        findSeatById(id); // throws 404 if it doesn't exist

        throw new SeatInUseException(
                "Seat " + id + " has an active booking and cannot be deleted."
                        + " Block it instead, or cancel the booking first"
        );
    }

    // FIND SEAT BY ID
    private Seat findSeatById(Integer id) {

        return seatRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Seat", id)
                );
    }

    private void requireShowtimeId(Integer showtimeId) {

        if (showtimeId == null || showtimeId <= 0) {
            throw new IllegalArgumentException(
                    "showtimeId must be a positive id"
            );
        }
    }

    // A seat's status for one show. BLOCKED applies to every show; a legacy
    // BOOKED seat (pre-V4) is still held; otherwise it depends on whether
    // that show has a reservation for it.
    private SeatStatus statusForShowtime(Seat seat, boolean reserved) {

        if (seat.getStatus() != SeatStatus.AVAILABLE) {
            return seat.getStatus();
        }

        return reserved ? SeatStatus.BOOKED : SeatStatus.AVAILABLE;
    }

    // MAP ENTITY TO RESPONSE DTO
    private SeatResponseDTO mapToResponse(Seat seat) {

        return mapToResponse(seat, seat.getStatus());
    }

    private SeatResponseDTO mapToResponse(Seat seat, SeatStatus status) {

        return new SeatResponseDTO(
                seat.getSeatId(),
                seat.getScreenId(),
                seat.getSeatNumber(),
                seat.getSeatType(),
                status,
                seat.getPrice(),
                seat.getCreatedAt()
        );
    }
}
