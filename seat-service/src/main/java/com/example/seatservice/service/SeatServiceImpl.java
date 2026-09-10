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

    // Default prices per seat type — used when admin does not provide a price
    private static final Map<String, BigDecimal> DEFAULT_PRICES = Map.of(
            "STANDARD", new BigDecimal("5.00"),
            "VIP",      new BigDecimal("10.00"),
            "COUPLE",   new BigDecimal("15.00")
    );

    public SeatServiceImpl(SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
    }

    /**
     * Resolve the price for a seat:
     * 1. Use the price from the request if provided and positive
     * 2. Fall back to the default for the seat type
     * 3. Fall back to 5.00 if the type is unknown
     */
    private BigDecimal resolvePrice(String seatType, BigDecimal requestedPrice) {
        if (requestedPrice != null && requestedPrice.compareTo(BigDecimal.ZERO) > 0) {
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
    public List<SeatResponseDTO> getAvailableSeatsByScreenId(Integer screenId) {
        return seatRepository
                .findByScreenIdAndStatus(screenId, SeatStatus.AVAILABLE)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // UPDATE
    @Override
    public SeatResponseDTO updateSeat(Integer id, SeatRequestDTO request) {

        Seat seat = findSeatById(id);

        seat.setScreenId(request.screenId());
        seat.setSeatNumber(request.seatNumber());
        seat.setSeatType(request.seatType());

        if (request.status() != null) {
            seat.setStatus(request.status());
        }

        // Update price if provided, otherwise keep existing price
        if (request.price() != null && request.price().compareTo(BigDecimal.ZERO) > 0) {
            seat.setPrice(request.price());
        }

        return mapToResponse(seatRepository.save(seat));
    }

    // RESERVE
    @Override
    @Transactional
    public SeatResponseDTO reserveSeat(Integer id) {

        // Confirm the seat exists so a missing seat is a 404, not a 409.
        findSeatById(id);

        int updated = seatRepository.compareAndSetStatus(
                id,
                SeatStatus.AVAILABLE,
                SeatStatus.BOOKED
        );

        if (updated == 0) {
            throw new SeatNotAvailableException("Seat " + id + " is not available");
        }

        return mapToResponse(findSeatById(id));
    }

    // RELEASE
    @Override
    @Transactional
    public SeatResponseDTO releaseSeat(Integer id) {

        findSeatById(id);

        // Idempotent on purpose: releasing an already-AVAILABLE seat is a
        // no-op rather than an error, so a retried compensating call from
        // booking-service cannot fail.
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

    private Seat findSeatById(Integer id) {
        return seatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Seat", id));
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