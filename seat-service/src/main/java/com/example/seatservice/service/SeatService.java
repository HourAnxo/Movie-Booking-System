
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

    // GET BY SCREEN. With a showtimeId each seat's status is its status for
    // that show; without one it is the stored seat status (the layout view).
    List<SeatResponseDTO> getSeatsByScreenId(
            Integer screenId,
            Integer showtimeId
    );

    // GET AVAILABLE BY SCREEN (for one showtime when showtimeId is given)
    List<SeatResponseDTO> getAvailableSeatsByScreenId(
            Integer screenId,
            Integer showtimeId
    );

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

    // RESERVE the seat for one showtime (409 if taken or blocked)
    SeatResponseDTO reserveSeat(Integer id, Integer showtimeId);

    // RELEASE the seat for one showtime (idempotent)
    SeatResponseDTO releaseSeat(Integer id, Integer showtimeId);

    // DELETE
    void deleteSeat(Integer id);
}
