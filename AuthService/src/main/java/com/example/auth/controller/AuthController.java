package com.example.auth.controller;

import com.example.auth.dto.request.LoginRequest;
import com.example.auth.dto.request.RegisterRequest;
import com.example.auth.dto.request.RefreshRequest;
import com.example.auth.dto.response.AuthTokensResponse;
import com.example.auth.model.AuthUser;
import com.example.auth.model.RefreshToken;
import com.example.auth.repo.AuthUserRepository;
import com.example.auth.security.TokenService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthUserRepository authUserRepository;
    private final TokenService tokenService;

    public AuthController(
            AuthUserRepository authUserRepository,
            TokenService tokenService
    ) {
        this.authUserRepository = authUserRepository;
        this.tokenService = tokenService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody RegisterRequest request) {
        String passwordHash = tokenService.encodePassword(request.getPassword());

        AuthUser user = new AuthUser();
        user.setEmail(request.getEmail().toLowerCase().trim());
        user.setName(request.getName().trim());
        user.setPasswordHash(passwordHash);
        user.setRole(request.getRole());
        user.setActive(true);

        try {
            authUserRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Email already exists", e);
        }
    }

    @PostMapping("/login")
    public AuthTokensResponse login(@Valid @RequestBody LoginRequest request) {
        AuthUser user = authUserRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!user.isActive() || !tokenService.verifyPassword(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        String accessToken = tokenService.createAccessToken(user);
        String refreshToken = tokenService.createAndStoreRefreshTokenOpaque(user, Instant.now());

        return new AuthTokensResponse(accessToken, refreshToken);
    }

    @PostMapping("/refresh")
    public AuthTokensResponse refresh(@Valid @RequestBody RefreshRequest request) {
        String refreshTokenValue = request.getRefreshToken();
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw new IllegalArgumentException("refreshToken is required");
        }

        RefreshToken token = tokenService.findRefreshTokenOrNull(refreshTokenValue);
        if (token == null || token.isRevoked() || token.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Invalid refreshToken");
        }

        // Rotate: revoke old refresh token, issue new opaque refresh token
        AuthUser user = token.getUser();
        String newRefreshToken = tokenService.rotateRefreshTokenAndGetNew(refreshTokenValue, token);

        String newAccessToken = tokenService.createAccessToken(user);
        return new AuthTokensResponse(newAccessToken, newRefreshToken);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        tokenService.revokeRefreshTokenAndClearCache(request.getRefreshToken());
    }
}

