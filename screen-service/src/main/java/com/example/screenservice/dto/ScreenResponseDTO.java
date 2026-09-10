package com.example.screenservice.dto;

import java.time.LocalDateTime;

public record ScreenResponseDTO(
        Integer screenId,
        Integer theaterId,
        String name,
        String screenType,
        Integer capacity,
        LocalDateTime createdAt
) {
}