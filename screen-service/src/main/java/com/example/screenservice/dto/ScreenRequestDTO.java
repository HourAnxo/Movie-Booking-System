package com.example.screenservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ScreenRequestDTO(

        // No foreign key to theaters — this is another service's id and
        // nothing here confirms it exists. The shape is all we can check.
        @NotNull(message = "is required")
        @Positive(message = "must be a positive id")
        Integer theaterId,

        @NotBlank(message = "is required")
        @Size(max = 100, message = "must be at most 100 characters")
        String name,

        @Size(max = 50, message = "must be at most 50 characters")
        String screenType,

        @NotNull(message = "is required")
        @Positive(message = "must be greater than zero")
        Integer capacity
) {
}
