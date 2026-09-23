package com.example.authservice.dto;

import com.example.authservice.entity.Role;
import lombok.Builder;

@Builder
public record ProfileResponseDTO(
        String username,
        Integer userId,
        String email,
        Role role
) {
}