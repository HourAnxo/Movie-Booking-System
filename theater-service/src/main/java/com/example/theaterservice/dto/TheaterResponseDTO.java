package com.example.theaterservice.dto;

import java.time.LocalDateTime;

public record TheaterResponseDTO(
        Integer theaterId,
        String name,
        String location,
        String address,
        LocalDateTime createdAt
) {
}