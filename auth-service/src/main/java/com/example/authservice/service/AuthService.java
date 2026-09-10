package com.example.authservice.service;

import com.example.authservice.dto.AuthResponseDTO;
import com.example.authservice.dto.LoginRequestDTO;
import com.example.authservice.dto.LogoutRequestDTO;
import com.example.authservice.dto.LogoutResponseDTO;
import com.example.authservice.dto.ProfileResponseDTO;
import com.example.authservice.dto.RefreshTokenRequestDTO;
import com.example.authservice.dto.RegisterRequestDTO;
import com.example.authservice.dto.UpdateRoleRequestDTO;

public interface AuthService {

    AuthResponseDTO register(RegisterRequestDTO request);

    AuthResponseDTO login(LoginRequestDTO request);

    AuthResponseDTO refreshToken(
            RefreshTokenRequestDTO request
    );

    ProfileResponseDTO getProfile(String username);

    LogoutResponseDTO logout(
            LogoutRequestDTO request
    );

    /**
     * Promotes or demotes a user. Admin-only — the check lives on the
     * controller method, not here.
     */
    ProfileResponseDTO updateRole(
            String username,
            UpdateRoleRequestDTO request
    );
}
