package com.example.inventory.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class JwtAuthService {

    private final JwtDecoder jwtDecoder;

    public JwtAuthService(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    public AuthContext authenticate(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing Authorization header");
        }
        String token = extractToken(authorizationHeader);
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid/expired access token", e);
        }

        Long userId;
        try {
            userId = Long.parseLong(jwt.getSubject());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token subject", e);
        }
        List<String> roles = jwt.getClaimAsStringList("roles");
        return new AuthContext(userId, roles);
    }

    private static String extractToken(String authorizationHeader) {
        String value = authorizationHeader.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            value = value.substring("Bearer ".length()).trim();
        }
        if (value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Authorization header");
        }
        return value;
    }
}

