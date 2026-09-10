package com.example.seatservice.service;

import com.example.seatservice.dto.SeatRequestDTO;
import com.example.seatservice.dto.SeatResponseDTO;

import java.util.List;

public interface SeatService {

    SeatResponseDTO createSeat(
            SeatRequestDTO request
    );

    List<SeatResponseDTO> getAllSeats();

    SeatResponseDTO getSeatById(
            Integer id
    );

    List<SeatResponseDTO> getSeatsByScreenId(
            Integer screenId
    );

    List<SeatResponseDTO> getAvailableSeatsByScreenId(
            Integer screenId
    );

    SeatResponseDTO updateSeat(
            Integer id,
            SeatRequestDTO request
    );

    /**
     * AVAILABLE -> BOOKED. Called by booking-service when a booking is
     * created. Throws if the seat is not AVAILABLE.
     */
    SeatResponseDTO reserveSeat(
            Integer id
    );

    /**
     * BOOKED -> AVAILABLE. Called by booking-service when a booking is
     * cancelled, and as the compensating action when payment fails.
     */
    SeatResponseDTO releaseSeat(
            Integer id
    );

    void deleteSeat(
            Integer id
    );
}
