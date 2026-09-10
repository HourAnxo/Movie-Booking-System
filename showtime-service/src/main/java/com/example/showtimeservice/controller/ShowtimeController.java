package com.example.showtimeservice.controller;

import com.example.showtimeservice.dto.ShowtimeRequestDTO;
import com.example.showtimeservice.dto.ShowtimeResponseDTO;
import com.example.showtimeservice.service.ShowtimeService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/showtimes")
public class ShowtimeController {

    private final ShowtimeService showtimeService;

    public ShowtimeController(
            ShowtimeService showtimeService
    ) {
        this.showtimeService = showtimeService;
    }

    // CREATE
    @PostMapping
    public ResponseEntity<ShowtimeResponseDTO> createShowtime(
            @Valid @RequestBody ShowtimeRequestDTO request
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        showtimeService.createShowtime(request)
                );
    }

    // GET ALL
    @GetMapping
    public ResponseEntity<List<ShowtimeResponseDTO>>
    getAllShowtimes() {

        return ResponseEntity.ok(
                showtimeService.getAllShowtimes()
        );
    }

    // GET BY ID
    @GetMapping("/{id}")
    public ResponseEntity<ShowtimeResponseDTO> getShowtimeById(
            @PathVariable Integer id
    ) {

        return ResponseEntity.ok(
                showtimeService.getShowtimeById(id)
        );
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<ShowtimeResponseDTO> updateShowtime(
            @PathVariable Integer id,
            @Valid @RequestBody ShowtimeRequestDTO request
    ) {

        return ResponseEntity.ok(
                showtimeService.updateShowtime(id, request)
        );
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShowtime(
            @PathVariable Integer id
    ) {

        showtimeService.deleteShowtime(id);

        return ResponseEntity.noContent().build();
    }
}
