package com.example.authservice.dto;

import com.example.authservice.entity.Role;

public record AuthResponseDTO(
        String accessToken,
        String refreshToken,
        String username,
        Integer userId,
        Role role
) {
}