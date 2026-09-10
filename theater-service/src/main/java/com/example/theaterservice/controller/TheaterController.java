package com.example.theaterservice.controller;

import com.example.theaterservice.dto.TheaterRequestDTO;
import com.example.theaterservice.dto.TheaterResponseDTO;
import com.example.theaterservice.service.TheaterService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/theaters")
public class TheaterController {

    private final TheaterService theaterService;

    public TheaterController(
            TheaterService theaterService
    ) {
        this.theaterService = theaterService;
    }

    // CREATE
    @PostMapping
    public ResponseEntity<TheaterResponseDTO> createTheater(
            @Valid @RequestBody TheaterRequestDTO request
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        theaterService.createTheater(request)
                );
    }

    // GET ALL
    @GetMapping
    public ResponseEntity<List<TheaterResponseDTO>>
    getAllTheaters() {

        return ResponseEntity.ok(
                theaterService.getAllTheaters()
        );
    }

    // GET BY ID
    @GetMapping("/{id}")
    public ResponseEntity<TheaterResponseDTO>
    getTheaterById(
            @PathVariable Integer id
    ) {

        return ResponseEntity.ok(
                theaterService.getTheaterById(id)
        );
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<TheaterResponseDTO>
    updateTheater(
            @PathVariable Integer id,
            @Valid @RequestBody TheaterRequestDTO request
    ) {

        return ResponseEntity.ok(
                theaterService.updateTheater(id, request)
        );
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTheater(
            @PathVariable Integer id
    ) {

        theaterService.deleteTheater(id);

        return ResponseEntity.noContent().build();
    }
}
