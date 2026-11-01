
package com.example.seatservice.service;

import com.example.seatservice.dto.SeatRequestDTO;
import com.example.seatservice.dto.SeatResponseDTO;
import com.example.seatservice.entity.Seat;
import com.example.seatservice.entity.SeatStatus;
import com.example.seatservice.exception.ResourceNotFoundException;
import com.example.seatservice.exception.SeatNotAvailableException;
import com.example.seatservice.repository.SeatRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class SeatServiceImpl implements SeatService {

    private final SeatRepository seatRepository;

    // Default prices per seat type
    private static final Map<String, BigDecimal> DEFAULT_PRICES = Map.of(
            "STANDARD", new BigDecimal("5.00"),
            "VIP", new BigDecimal("10.00"),
            "COUPLE", new BigDecimal("15.00")
    );

    public SeatServiceImpl(SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
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
    public List<SeatResponseDTO> getSeatsByScreenId(Integer screenId) {

        return seatRepository.findByScreenId(screenId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // GET AVAILABLE BY SCREEN
    @Override
    public List<SeatResponseDTO> getAvailableSeatsByScreenId(
            Integer screenId
    ) {

        return seatRepository
                .findByScreenIdAndStatus(screenId, SeatStatus.AVAILABLE)
                .stream()
                .map(this::mapToResponse)
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
    @Override
    @Transactional
    public SeatResponseDTO reserveSeat(Integer id) {

        findSeatById(id);

        int updated = seatRepository.compareAndSetStatus(
                id,
                SeatStatus.AVAILABLE,
                SeatStatus.BOOKED
        );

        if (updated == 0) {
            throw new SeatNotAvailableException(
                    "Seat " + id + " is not available"
            );
        }

        return mapToResponse(findSeatById(id));
    }

    // RELEASE
    @Override
    @Transactional
    public SeatResponseDTO releaseSeat(Integer id) {

        findSeatById(id);

        seatRepository.compareAndSetStatus(
                id,
                SeatStatus.BOOKED,
                SeatStatus.AVAILABLE
        );

        return mapToResponse(findSeatById(id));
    }

    // DELETE
    @Override
    public void deleteSeat(Integer id) {

        seatRepository.delete(findSeatById(id));
    }

    // FIND SEAT BY ID
    private Seat findSeatById(Integer id) {

        return seatRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Seat", id)
                );
    }

    // MAP ENTITY TO RESPONSE DTO
    private SeatResponseDTO mapToResponse(Seat seat) {

        return new SeatResponseDTO(
                seat.getSeatId(),
                seat.getScreenId(),
                seat.getSeatNumber(),
                seat.getSeatType(),
                seat.getStatus(),
                seat.getPrice(),
                seat.getCreatedAt()
        );
    }
}
