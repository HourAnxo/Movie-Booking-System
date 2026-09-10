package com.example.authservice.service;

import com.example.authservice.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;


    // ================= SECRET KEY =================

    private SecretKey getSigningKey() {

        return Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
    }


    // ================= GENERATE ACCESS TOKEN =================

    public String generateAccessToken(User user) {

        return Jwts.builder()
                .subject(user.getUsername())

                .claim("role", user.getRole().name())

                // What Booking.userId refers to. The gateway forwards it as
                // X-Auth-UserId so downstream services get the caller's
                // identity without another lookup.
                .claim("userId", user.getProfileId())
                .claim("type", "access")

                .issuedAt(new Date())

                .expiration(
                        new Date(
                                System.currentTimeMillis()
                                        + accessTokenExpiration
                        )
                )

                .signWith(getSigningKey())

                .compact();
    }


    // ================= GENERATE REFRESH TOKEN =================

    public String generateRefreshToken(User user) {

        return Jwts.builder()
                .subject(user.getUsername())

                .claim("type", "refresh")

                .issuedAt(new Date())

                .expiration(
                        new Date(
                                System.currentTimeMillis()
                                        + refreshTokenExpiration
                        )
                )

                .signWith(getSigningKey())

                .compact();
    }


    // ================= EXTRACT USERNAME =================

    public String extractUsername(String token) {

        return extractAllClaims(token)
                .getSubject();
    }


    // ================= EXTRACT TOKEN TYPE =================

    public String extractTokenType(String token) {

        return extractAllClaims(token)
                .get("type", String.class);
    }


    // ================= CHECK ACCESS TOKEN =================

    public boolean isAccessToken(String token) {

        return "access".equals(
                extractTokenType(token)
        );
    }


    // ================= CHECK REFRESH TOKEN =================

    public boolean isRefreshToken(String token) {

        return "refresh".equals(
                extractTokenType(token)
        );
    }


    // ================= VALIDATE TOKEN =================

    public boolean isTokenValid(
            String token,
            User user
    ) {

        try {

            String username =
                    extractUsername(token);

            return username.equals(user.getUsername())
                    && !isTokenExpired(token);

        } catch (Exception e) {

            return false;
        }
    }


    // ================= CHECK EXPIRATION =================

    private boolean isTokenExpired(String token) {

        return extractAllClaims(token)
                .getExpiration()
                .before(new Date());
    }


    // ================= EXTRACT ALL CLAIMS =================

    private Claims extractAllClaims(String token) {

        return Jwts.parser()

                .verifyWith(getSigningKey())

                .build()

                .parseSignedClaims(token)

                .getPayload();
    }
}