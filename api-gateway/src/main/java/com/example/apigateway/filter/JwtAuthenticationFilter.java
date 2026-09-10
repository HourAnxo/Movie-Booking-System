package com.example.apigateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

/**
 * Validates the access token once, at the edge, so the downstream services
 * do not each have to. Previously only auth-service checked tokens and the
 * other services were reachable unauthenticated through the gateway.
 *
 * On success the caller's identity is forwarded as X-Auth-Username and
 * X-Auth-Role. Any inbound X-Auth-* header from the client is stripped
 * first — otherwise a caller could simply assert whoever they liked.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String USERNAME_HEADER = "X-Auth-Username";
    public static final String ROLE_HEADER = "X-Auth-Role";

    /**
     * The caller's user-service id — what Booking.userId refers to. Taken
     * from the signed token, never from the request, so a downstream
     * service can treat it as the caller's real identity.
     */
    public static final String USER_ID_HEADER = "X-Auth-UserId";

    private static final AntPathMatcher PATH_MATCHER =
            new AntPathMatcher();

    /** Reachable with no token at all. */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/actuator/**"
    );

    /** Catalogue browsing: open to read, token required to modify. */
    private static final List<String> CATALOGUE_PATHS = List.of(
            "/api/movies/**",
            "/api/theaters/**",
            "/api/screens/**",
            "/api/showtimes/**",
            "/api/seats/**"
    );

    /** Reachable only by an ADMIN, whatever the method. */
    private static final List<String> ADMIN_PATHS = List.of(
            "/api/admin/**",
            // Granting roles is itself an admin action, so it is guarded
            // here as well as by @PreAuthorize inside auth-service.
            "/api/auth/users/**"
    );

    private static final String ADMIN_ROLE = "ADMIN";

    private static final Set<String> SPOOFABLE_HEADERS = Set.of(
            USERNAME_HEADER.toLowerCase(),
            ROLE_HEADER.toLowerCase(),
            USER_ID_HEADER.toLowerCase()
    );

    private final SecretKey signingKey;

    public JwtAuthenticationFilter(
            @Value("${jwt.secret}") String secret
    ) {
        this.signingKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // Never trust identity headers that arrived from outside.
        HttpServletRequest sanitized = stripAuthHeaders(request);

        if (isPublic(sanitized)) {
            filterChain.doFilter(sanitized, response);
            return;
        }

        String authHeader = sanitized.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            reject(response, "Missing or malformed Authorization header");
            return;
        }

        try {

            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(authHeader.substring(7))
                    .getPayload();

            // A refresh token must not be usable as an access token.
            if (!"access".equals(claims.get("type", String.class))) {
                reject(response, "Access token required");
                return;
            }

            String role = claims.get("role", String.class);

            // Integer in the token; null for a pre-V3 account that has not
            // logged in again yet.
            Object userId = claims.get("userId");

            if (requiresAdmin(sanitized) && !ADMIN_ROLE.equals(role)) {
                deny(response);
                return;
            }

            filterChain.doFilter(
                    withIdentity(
                            sanitized,
                            claims.getSubject(),
                            role,
                            userId
                    ),
                    response
            );

        } catch (JwtException | IllegalArgumentException ex) {
            // Covers a bad signature, an expired token and a malformed one.
            reject(response, "Invalid or expired token");
        }
    }

    private boolean isPublic(HttpServletRequest request) {

        String path = request.getRequestURI();

        if (matchesAny(PUBLIC_PATHS, path)) {
            return true;
        }

        boolean readOnly =
                HttpMethod.GET.matches(request.getMethod())
                        || HttpMethod.OPTIONS.matches(request.getMethod());

        return readOnly && matchesAny(CATALOGUE_PATHS, path);
    }

    /**
     * Which requests need ADMIN rather than merely a valid token.
     *
     * Read access to the catalogue is public and stays public; it is the
     * WRITES that were the hole. Before this, any registered user could
     * DELETE every movie in the system, because "has a token" was the
     * only question ever asked.
     *
     * Note what is deliberately NOT here: the reserve and release calls
     * on /api/seats/** are made service-to-service through Eureka and
     * never traverse the gateway, so requiring ADMIN for seat writes
     * does not touch the booking saga.
     */
    private boolean requiresAdmin(HttpServletRequest request) {

        String path = request.getRequestURI();
        String method = request.getMethod();

        if (matchesAny(ADMIN_PATHS, path)) {
            return true;
        }

        boolean readOnly =
                HttpMethod.GET.matches(method)
                        || HttpMethod.OPTIONS.matches(method);

        // Changing the catalogue is operator work.
        if (!readOnly && matchesAny(CATALOGUE_PATHS, path)) {
            return true;
        }

        // Listing every user is a privacy question, not a booking one —
        // that is what admin-service is for. A single user by id stays
        // open to any authenticated caller.
        if (HttpMethod.GET.matches(method) && "/api/users".equals(path)) {
            return true;
        }

        return HttpMethod.DELETE.matches(method)
                && PATH_MATCHER.match("/api/users/**", path);
    }

    private static boolean matchesAny(List<String> patterns, String path) {
        return patterns.stream()
                .anyMatch(p -> PATH_MATCHER.match(p, path));
    }

    /**
     * Hides any client-supplied X-Auth-* header from everything downstream.
     */
    private HttpServletRequest stripAuthHeaders(
            HttpServletRequest request
    ) {

        return new HttpServletRequestWrapper(request) {

            @Override
            public String getHeader(String name) {
                if (isSpoofable(name)) {
                    return null;
                }
                return super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                if (isSpoofable(name)) {
                    return Collections.emptyEnumeration();
                }
                return super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                return Collections.enumeration(
                        Collections.list(super.getHeaderNames())
                                .stream()
                                .filter(n -> !isSpoofable(n))
                                .toList()
                );
            }
        };
    }

    private HttpServletRequest withIdentity(
            HttpServletRequest request,
            String username,
            String role,
            Object userId
    ) {

        return new HttpServletRequestWrapper(request) {

            @Override
            public String getHeader(String name) {
                if (USERNAME_HEADER.equalsIgnoreCase(name)) {
                    return username;
                }
                if (ROLE_HEADER.equalsIgnoreCase(name)) {
                    return role;
                }

                if (USER_ID_HEADER.equalsIgnoreCase(name)) {
                    return userId == null ? null : String.valueOf(userId);
                }
                return super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                if (USERNAME_HEADER.equalsIgnoreCase(name)) {
                    return Collections.enumeration(List.of(username));
                }
                if (ROLE_HEADER.equalsIgnoreCase(name) && role != null) {
                    return Collections.enumeration(List.of(role));
                }

                if (USER_ID_HEADER.equalsIgnoreCase(name) && userId != null) {
                    return Collections.enumeration(List.of(String.valueOf(userId)));
                }
                return super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                List<String> names = new java.util.ArrayList<>(
                        Collections.list(super.getHeaderNames())
                );
                names.add(USERNAME_HEADER);
                names.add(ROLE_HEADER);

                if (userId != null) {

                    names.add(USER_ID_HEADER);

                }
                return Collections.enumeration(names);
            }
        };
    }

    private static boolean isSpoofable(String name) {
        return name != null
                && SPOOFABLE_HEADERS.contains(name.toLowerCase());
    }

    /**
     * Authenticated, but not allowed. 403 rather than 401 — the caller
     * proved who they are and a different token will not help, so telling
     * them to re-authenticate would send them round a pointless loop.
     */
    private void deny(HttpServletResponse response) throws IOException {

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":403,\"error\":\"Forbidden\","
                        + "\"message\":\"Requires the ADMIN role\"}"
        );
    }

    private void reject(
            HttpServletResponse response,
            String message
    ) throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":401,\"error\":\"Unauthorized\","
                        + "\"message\":\"" + message + "\"}"
        );
    }
}
