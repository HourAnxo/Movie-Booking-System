package com.example.authservice.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequestDTO(

        @NotBlank(message = "is required")
        String refreshToken
) {
}
