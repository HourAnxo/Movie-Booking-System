package com.example.theaterservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TheaterRequestDTO(

        @NotBlank(message = "is required")
        @Size(max = 100, message = "must be at most 100 characters")
        String name,

        @NotBlank(message = "is required")
        @Size(max = 100, message = "must be at most 100 characters")
        String location,

        // Nullable in the theaters table.
        @Size(max = 255, message = "must be at most 255 characters")
        String address
) {
}
