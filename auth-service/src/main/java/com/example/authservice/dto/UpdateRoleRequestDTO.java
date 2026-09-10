package com.example.authservice.dto;

import com.example.authservice.entity.Role;

import jakarta.validation.constraints.NotNull;

/**
 * An unknown role name is rejected by Jackson before validation runs, so
 * the closed set is enforced at the very edge — "SUPERADMIN" is a 400, not
 * an account that quietly holds an authority nothing grants.
 */
public record UpdateRoleRequestDTO(

        @NotNull(message = "is required")
        Role role
) {
}
