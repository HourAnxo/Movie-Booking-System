package com.example.authservice.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequestDTO(

        @NotBlank(message = "is required")
        String refreshToken
) {
}
