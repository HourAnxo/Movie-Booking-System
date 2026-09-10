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

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SeatServiceImpl implements SeatService {

    private final SeatRepository seatRepository;

    public SeatServiceImpl(
            SeatRepository seatRepository
    ) {
        this.seatRepository = seatRepository;
    }

    // CREATE
    @Override
    public SeatResponseDTO createSeat(
            SeatRequestDTO request
    ) {

        Seat seat = Seat.builder()
                .screenId(request.screenId())
                .seatNumber(request.seatNumber())
                .seatType(request.seatType())
                .status(
                        request.status() != null
                                ? request.status()
                                : SeatStatus.AVAILABLE
                )
                .createdAt(LocalDateTime.now())
                .build();

        Seat savedSeat = seatRepository.save(seat);

        return mapToResponse(savedSeat);
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
    public SeatResponseDTO getSeatById(
            Integer id
    ) {

        return mapToResponse(findSeatById(id));
    }

    // GET BY SCREEN
    @Override
    public List<SeatResponseDTO> getSeatsByScreenId(
            Integer screenId
    ) {

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
                .findByScreenIdAndStatus(
                        screenId,
                        SeatStatus.AVAILABLE
                )
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // UPDATE
    @Override
    public SeatResponseDTO updateSeat(
            Integer id,
            SeatRequestDTO request
    ) {

        Seat seat = findSeatById(id);

        seat.setScreenId(request.screenId());
        seat.setSeatNumber(request.seatNumber());
        seat.setSeatType(request.seatType());

        if (request.status() != null) {
            seat.setStatus(request.status());
        }

        Seat updatedSeat = seatRepository.save(seat);

        return mapToResponse(updatedSeat);
    }

    // RESERVE
    @Override
    @Transactional
    public SeatResponseDTO reserveSeat(
            Integer id
    ) {

        // Confirm the seat exists so a missing seat is a 404, not a 409.
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
    public SeatResponseDTO releaseSeat(
            Integer id
    ) {

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
    public void deleteSeat(
            Integer id
    ) {

        seatRepository.delete(findSeatById(id));
    }

    private Seat findSeatById(
            Integer id
    ) {

        return seatRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Seat", id)
                );
    }

    // MAP ENTITY TO RESPONSE DTO
    private SeatResponseDTO mapToResponse(
            Seat seat
    ) {

        return new SeatResponseDTO(
                seat.getSeatId(),
                seat.getScreenId(),
                seat.getSeatNumber(),
                seat.getSeatType(),
                seat.getStatus(),
                seat.getCreatedAt()
        );
    }
}
