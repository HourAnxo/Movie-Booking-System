package com.example.authservice.service;

import com.example.authservice.client.UserServiceClient;
import com.example.authservice.dto.AuthResponseDTO;
import com.example.authservice.dto.CreateUserRequestDTO;
import com.example.authservice.dto.LoginRequestDTO;
import com.example.authservice.dto.LogoutRequestDTO;
import com.example.authservice.dto.LogoutResponseDTO;
import com.example.authservice.dto.ProfileResponseDTO;
import com.example.authservice.dto.RefreshTokenRequestDTO;
import com.example.authservice.dto.RegisterRequestDTO;
import com.example.authservice.dto.UpdateRoleRequestDTO;

import com.example.authservice.entity.Role;
import com.example.authservice.entity.User;
import com.example.authservice.exception.DuplicateResourceException;
import com.example.authservice.exception.InvalidCredentialsException;
import com.example.authservice.exception.ResourceNotFoundException;
import com.example.authservice.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log =
            LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserServiceClient userServiceClient;


    public AuthServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            UserServiceClient userServiceClient
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userServiceClient = userServiceClient;
    }


    // ================= REGISTER =================

    /**
     * Writes the credential row to auth_db, then asks user-service to
     * create the matching profile. @Transactional means a failure of the
     * profile call rolls the credential row back, so the two databases do
     * not drift apart — the caller sees 502 and can retry the whole
     * register.
     */
    @Override
    @Transactional
    public AuthResponseDTO register(RegisterRequestDTO request) {

        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException(
                    "Username already exists: " + request.username()
            );
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException(
                    "Email already exists: " + request.email()
            );
        }


        // Save authentication user in auth_db
        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(
                        passwordEncoder.encode(request.password())
                )
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .build();

        userRepository.save(user);


        // ===============================
        // CALL USER SERVICE
        // ===============================

        // Captured onto the managed entity — the surrounding @Transactional
        // flushes it at commit, so this stays one write and the existing
        // rollback-on-failure behaviour is unchanged.
        Integer profileId = userServiceClient.createUser(
                new CreateUserRequestDTO(
                        request.username(),
                        request.email(),
                        null
                )
        );

        user.setProfileId(profileId);


        // ===============================
        // GENERATE JWT TOKENS
        // ===============================

        String accessToken =
                jwtService.generateAccessToken(user);

        String refreshToken =
                jwtService.generateRefreshToken(user);


        return new AuthResponseDTO(
                accessToken,
                refreshToken,
                user.getUsername(),
                user.getProfileId(),
                user.getRole()
        );
    }


    // ================= LOGIN =================

    @Override
    public AuthResponseDTO login(LoginRequestDTO request) {

        User user = userRepository
                .findByUsername(request.username())
                .orElseThrow(() ->
                        new InvalidCredentialsException(
                                "Invalid username or password"
                        )
                );

        if (!passwordEncoder.matches(
                request.password(),
                user.getPassword()
        )) {
            throw new InvalidCredentialsException(
                    "Invalid username or password"
            );
        }

        backfillProfileId(user);

        String accessToken =
                jwtService.generateAccessToken(user);

        String refreshToken =
                jwtService.generateRefreshToken(user);

        return new AuthResponseDTO(
                accessToken,
                refreshToken,
                user.getUsername(),
                user.getProfileId(),
                user.getRole()
        );
    }


    // ================= REFRESH TOKEN =================

    @Override
    public AuthResponseDTO refreshToken(
            RefreshTokenRequestDTO request
    ) {

        User user = requireValidRefreshToken(
                request.refreshToken()
        );

        String newAccessToken =
                jwtService.generateAccessToken(user);

        return new AuthResponseDTO(
                newAccessToken,
                request.refreshToken(),
                user.getUsername(),
                user.getProfileId(),
                user.getRole()
        );
    }


    // ================= GET PROFILE =================

    @Override
    public ProfileResponseDTO getProfile(String username) {

        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found: " + username
                        )
                );

        return new ProfileResponseDTO(
                user.getUsername(),
                user.getProfileId(),
                user.getEmail(),
                user.getRole()
        );
    }


    // ================= LOGOUT =================

    @Override
    public LogoutResponseDTO logout(
            LogoutRequestDTO request
    ) {

        requireValidRefreshToken(request.refreshToken());

        return new LogoutResponseDTO(
                "Logged out successfully"
        );
    }


    // ================= UPDATE ROLE =================

    /**
     * Changes a user's role. The authorization check is a @PreAuthorize
     * on the controller method — this method assumes the caller has
     * already been shown to be an admin.
     *
     * Takes effect on the target's NEXT request against auth-service,
     * because its JwtAuthenticationFilter reads the role from the row
     * rather than the token claim. It does NOT take effect at the
     * gateway until their access token expires, since the gateway has
     * only the claim to go on. A demotion is therefore up to one access
     * token lifetime (15 minutes) behind at the edge.
     */
    @Override
    @Transactional
    public ProfileResponseDTO updateRole(
            String username,
            UpdateRoleRequestDTO request
    ) {

        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found: " + username
                        )
                );

        user.setRole(request.role());

        User saved = userRepository.save(user);

        return new ProfileResponseDTO(
                saved.getUsername(),
                saved.getProfileId(),
                saved.getEmail(),
                saved.getRole()
        );
    }


    /**
     * Gives an older account the profile link that V3 added, on its next
     * login, using user-service's lookup-by-email. Doing it here rather
     * than as a cross-database UPDATE in the migration keeps auth_db from
     * ever reading user_db's tables, which is the whole point of the two
     * services owning separate schemas.
     *
     * Never throws. A profile that cannot be found or a user-service that
     * is briefly down must not stop someone signing in — the caller simply
     * gets a token with no userId claim, exactly as before, and the next
     * login tries again.
     */
    // Not @Transactional: this is called from login() on the same bean,
    // so the proxy is bypassed and the annotation would be a lie.
    // repository.save() carries its own transaction, which is all this
    // single-row update needs.
    private void backfillProfileId(User user) {

        if (user.getProfileId() != null) {
            return;
        }

        try {

            Integer profileId =
                    userServiceClient.findProfileIdByEmail(user.getEmail());

            if (profileId != null) {
                user.setProfileId(profileId);
                userRepository.save(user);
            }

        } catch (RuntimeException ex) {
            log.warn(
                    "Could not backfill profile id for {}: {}",
                    user.getUsername(),
                    ex.getMessage()
            );
        }
    }


    // ================= SHARED =================

    /**
     * Validates that the token is a well-formed, unexpired refresh token
     * belonging to a user that still exists, and returns that user.
     */
    private User requireValidRefreshToken(String refreshToken) {

        if (!jwtService.isRefreshToken(refreshToken)) {
            throw new InvalidCredentialsException(
                    "Invalid refresh token"
            );
        }

        String username =
                jwtService.extractUsername(refreshToken);

        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() ->
                        new InvalidCredentialsException(
                                "Invalid refresh token"
                        )
                );

        if (!jwtService.isTokenValid(refreshToken, user)) {
            throw new InvalidCredentialsException(
                    "Invalid or expired refresh token"
            );
        }

        return user;
    }
}
