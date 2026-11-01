package com.example.seatservice.controller;

import com.example.seatservice.dto.SeatPriceRequestDTO;
import com.example.seatservice.dto.SeatRequestDTO;
import com.example.seatservice.dto.SeatResponseDTO;
import com.example.seatservice.service.SeatService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/seats")
public class SeatController {

    private final SeatService seatService;

    public SeatController(SeatService seatService) {
        this.seatService = seatService;
    }

    // CREATE
    @PostMapping
    public ResponseEntity<SeatResponseDTO> createSeat(
            @Valid @RequestBody SeatRequestDTO request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(seatService.createSeat(request));
    }

    // GET ALL
    @GetMapping
    public ResponseEntity<List<SeatResponseDTO>> getAllSeats() {
        return ResponseEntity.ok(
                seatService.getAllSeats()
        );
    }

    // GET BY ID
    @GetMapping("/{id}")
    public ResponseEntity<SeatResponseDTO> getSeatById(
            @PathVariable Integer id
    ) {
        return ResponseEntity.ok(
                seatService.getSeatById(id)
        );
    }

    // GET BY SCREEN — ?showtimeId=N gives each seat's status for that show
    @GetMapping("/screen/{screenId}")
    public ResponseEntity<List<SeatResponseDTO>> getSeatsByScreenId(
            @PathVariable Integer screenId,
            @RequestParam(required = false) Integer showtimeId
    ) {
        return ResponseEntity.ok(
                seatService.getSeatsByScreenId(screenId, showtimeId)
        );
    }

    // GET AVAILABLE BY SCREEN — ?showtimeId=N for one show
    @GetMapping("/screen/{screenId}/available")
    public ResponseEntity<List<SeatResponseDTO>> getAvailableSeatsByScreenId(
            @PathVariable Integer screenId,
            @RequestParam(required = false) Integer showtimeId
    ) {
        return ResponseEntity.ok(
                seatService.getAvailableSeatsByScreenId(screenId, showtimeId)
        );
    }

    // UPDATE SEAT
    @PutMapping("/{id}")
    public ResponseEntity<SeatResponseDTO> updateSeat(
            @PathVariable Integer id,
            @Valid @RequestBody SeatRequestDTO request
    ) {
        return ResponseEntity.ok(
                seatService.updateSeat(id, request)
        );
    }

    // UPDATE SEAT PRICE
    @PutMapping("/{id}/price")
    public ResponseEntity<SeatResponseDTO> updateSeatPrice(
            @PathVariable Integer id,
            @Valid @RequestBody SeatPriceRequestDTO request
    ) {
        return ResponseEntity.ok(
                seatService.updateSeatPrice(id, request.price())
        );
    }

    // RESERVE for one showtime (internal: booking-service)
    @PutMapping("/{id}/reserve")
    public ResponseEntity<SeatResponseDTO> reserveSeat(
            @PathVariable Integer id,
            @RequestParam Integer showtimeId
    ) {
        return ResponseEntity.ok(
                seatService.reserveSeat(id, showtimeId)
        );
    }

    // RELEASE for one showtime (internal: booking-service)
    @PutMapping("/{id}/release")
    public ResponseEntity<SeatResponseDTO> releaseSeat(
            @PathVariable Integer id,
            @RequestParam Integer showtimeId
    ) {
        return ResponseEntity.ok(
                seatService.releaseSeat(id, showtimeId)
        );
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSeat(
            @PathVariable Integer id
    ) {
        seatService.deleteSeat(id);

        return ResponseEntity.noContent().build();
    }
}
