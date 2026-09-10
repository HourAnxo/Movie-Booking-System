package com.example.authservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sizes mirror the columns in auth_db.users and user_db.users — a value
 * that would be truncated or rejected by MySQL is refused as a 400 here
 * instead of surfacing as a 500 from the driver.
 */
public record RegisterRequestDTO(

        @NotBlank(message = "is required")
        @Size(max = 100, message = "must be at most 100 characters")
        String username,

        @NotBlank(message = "is required")
        @Email(message = "must be a valid email address")
        @Size(max = 150, message = "must be at most 150 characters")
        String email,

        // 72 is BCrypt's ceiling — bytes past it are silently ignored,
        // so a longer password would not mean what the caller thinks.
        @NotBlank(message = "is required")
        @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
        String password,

        @NotBlank(message = "is required")
        @Size(max = 100, message = "must be at most 100 characters")
        String name,

        @Size(max = 20, message = "must be at most 20 characters")
        String phone
) {
}
