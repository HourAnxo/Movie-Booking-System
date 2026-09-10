package com.example.authservice.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Presence only. Constraining length or shape here would leak the
 * registration rules to unauthenticated callers, and a credential that no
 * longer meets current policy must still be able to log in.
 */
public record LoginRequestDTO(

        @NotBlank(message = "is required")
        String username,

        @NotBlank(message = "is required")
        String password
) {
}
