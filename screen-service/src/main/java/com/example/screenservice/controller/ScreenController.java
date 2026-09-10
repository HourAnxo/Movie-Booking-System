package com.example.screenservice.controller;

import com.example.screenservice.dto.ScreenRequestDTO;
import com.example.screenservice.dto.ScreenResponseDTO;
import com.example.screenservice.service.ScreenService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/screens")
public class ScreenController {

    private final ScreenService screenService;

    public ScreenController(ScreenService screenService) {
        this.screenService = screenService;
    }

    // ================= CREATE =================

    @PostMapping
    public ResponseEntity<ScreenResponseDTO> createScreen(
            @Valid @RequestBody ScreenRequestDTO request
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(screenService.createScreen(request));
    }


    // ================= GET ALL =================

    @GetMapping
    public ResponseEntity<List<ScreenResponseDTO>>
    getAllScreens() {

        return ResponseEntity.ok(
                screenService.getAllScreens()
        );
    }


    // ================= GET BY ID =================

    @GetMapping("/{id}")
    public ResponseEntity<ScreenResponseDTO> getScreenById(
            @PathVariable Integer id
    ) {

        return ResponseEntity.ok(
                screenService.getScreenById(id)
        );
    }


    // ================= UPDATE =================

    @PutMapping("/{id}")
    public ResponseEntity<ScreenResponseDTO> updateScreen(
            @PathVariable Integer id,
            @Valid @RequestBody ScreenRequestDTO request
    ) {

        return ResponseEntity.ok(
                screenService.updateScreen(id, request)
        );
    }


    // ================= DELETE =================

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteScreen(
            @PathVariable Integer id
    ) {

        screenService.deleteScreen(id);

        return ResponseEntity.noContent().build();
    }
}
