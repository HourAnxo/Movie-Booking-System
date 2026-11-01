
package com.example.seatservice.service;

import com.example.seatservice.dto.SeatRequestDTO;
import com.example.seatservice.dto.SeatResponseDTO;

import java.math.BigDecimal;
import java.util.List;

public interface SeatService {

    // CREATE
    SeatResponseDTO createSeat(SeatRequestDTO request);

    // GET ALL
    List<SeatResponseDTO> getAllSeats();

    // GET BY ID
    SeatResponseDTO getSeatById(Integer id);

    // GET BY SCREEN
    List<SeatResponseDTO> getSeatsByScreenId(Integer screenId);

    // GET AVAILABLE BY SCREEN
    List<SeatResponseDTO> getAvailableSeatsByScreenId(Integer screenId);

    // UPDATE SEAT
    SeatResponseDTO updateSeat(
            Integer id,
            SeatRequestDTO request
    );

    // UPDATE SEAT PRICE
    SeatResponseDTO updateSeatPrice(
            Integer id,
            BigDecimal price
    );

    // RESERVE: AVAILABLE -> BOOKED
    SeatResponseDTO reserveSeat(Integer id);

    // RELEASE: BOOKED -> AVAILABLE
    SeatResponseDTO releaseSeat(Integer id);

    // DELETE
    void deleteSeat(Integer id);
}
