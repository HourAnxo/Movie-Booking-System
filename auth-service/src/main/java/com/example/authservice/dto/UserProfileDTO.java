package com.example.authservice.dto;

/**
 * The slice of user-service's response auth-service actually needs. Boot
 * disables FAIL_ON_UNKNOWN_PROPERTIES, so the other fields user-service
 * returns are ignored rather than breaking this on every change to them.
 */
public record UserProfileDTO(
        Integer userId
) {
}
