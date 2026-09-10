package com.example.authservice.dto;

import com.example.authservice.entity.Role;

public record ProfileResponseDTO(
        String username,
        Integer userId,
        String email,
        Role role
) {
}