package com.example.authservice.controller;

import com.example.authservice.dto.*;
import com.example.authservice.service.AuthService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // ================= REGISTER =================

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(
            @Valid @RequestBody RegisterRequestDTO request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(authService.register(request));
    }


    // ================= LOGIN =================

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(
            @Valid @RequestBody LoginRequestDTO request
    ) {
        return ResponseEntity.ok(
                authService.login(request)
        );
    }


    // ================= REFRESH TOKEN =================

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDTO> refreshToken(
            @Valid @RequestBody RefreshTokenRequestDTO request
    ) {
        return ResponseEntity.ok(
                authService.refreshToken(request)
        );
    }


    // ================= GET PROFILE =================

    @GetMapping("/profile")
    public ResponseEntity<ProfileResponseDTO> getProfile(
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                authService.getProfile(authentication.getName())
        );
    }


    // ================= LOGOUT =================

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponseDTO> logout(
            @Valid @RequestBody LogoutRequestDTO request
    ) {

        return ResponseEntity.ok(
                authService.logout(request)
        );
    }

    // ================= UPDATE ROLE (ADMIN ONLY) =================

    /**
     * The only place in the system where a role is granted.
     *
     * @PreAuthorize is the second line of defence: the gateway already
     * refuses non-admins on /api/auth/users/**, but auth-service is the
     * service that must not be wrong about this, and it validates tokens
     * itself. Its filter derives the authority from the persisted row, so
     * a revoked admin cannot promote anyone using a token issued while
     * they still held the role.
     *
     * The FIRST admin cannot be made here — nobody is one yet. Promote
     * one directly in auth_db, once:
     *
     *   UPDATE users SET role = 'ADMIN' WHERE username = 'someone';
     */
    @PutMapping("/users/{username}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProfileResponseDTO> updateRole(
            @PathVariable String username,
            @Valid @RequestBody UpdateRoleRequestDTO request
    ) {

        return ResponseEntity.ok(
                authService.updateRole(username, request)
        );
    }
}
