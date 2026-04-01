package com.example.auth.security;

import com.example.auth.model.AuthUser;
import com.example.auth.model.RefreshToken;
import com.example.auth.model.Role;
import com.example.auth.repo.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;

    @Value("${auth.jwt.access-token-expiration-seconds}")
    private long accessTokenExpirationSeconds;

    @Value("${auth.refresh-token.expiration-seconds}")
    private long refreshTokenExpirationSeconds;

    public TokenService(
            JwtEncoder jwtEncoder,
            JwtDecoder jwtDecoder,
            RefreshTokenRepository refreshTokenRepository,
            StringRedisTemplate redisTemplate,
            PasswordEncoder passwordEncoder
    ) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.redisTemplate = redisTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    public String createAccessToken(AuthUser user) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(accessTokenExpirationSeconds);

        List<String> roles = List.of(user.getRole().name());

        String jti = UUID.randomUUID().toString();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("auth-service")
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(exp)
                .id(jti)
                .claim("roles", roles)
                .build();

        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }

    public String rotateRefreshTokenAndGetNew(String refreshToken, RefreshToken oldToken) {
        // oldToken đã được validate ở tầng controller/service
        Instant now = Instant.now();

        oldToken.setRevoked(true);
        oldToken.setRevokedAt(now);
        refreshTokenRepository.save(oldToken);

        // Xoá cache mapping cũ để tránh replay
        String oldHash = oldToken.getTokenHash();
        redisTemplate.delete(refreshTokenKeyByHash(oldHash));

        // Tạo refresh token mới
        AuthUser user = oldToken.getUser();
        return createAndStoreRefreshTokenOpaque(user, now);
    }

    public String createAndStoreRefreshTokenOpaque(AuthUser user, Instant now) {
        String tokenValue = generateOpaqueToken();
        String tokenHash = sha256Hex(tokenValue);
        String tokenId = UUID.randomUUID().toString();

        Instant exp = now.plusSeconds(refreshTokenExpirationSeconds);

        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenId(tokenId);
        entity.setTokenHash(tokenHash);
        entity.setExpiresAt(exp);
        entity.setRevoked(false);

        refreshTokenRepository.save(entity);

        // Redis cache mapping hash -> tokenId
        long ttlSeconds = Math.max(1, refreshTokenExpirationSeconds);
        redisTemplate.opsForValue().set(refreshTokenKeyByHash(tokenHash), tokenId, ttlSeconds, TimeUnit.SECONDS);

        return tokenValue;
    }

    public RefreshToken findRefreshTokenOrNull(String opaqueRefreshToken) {
        String tokenHash = sha256Hex(opaqueRefreshToken);
        String cachedTokenId = redisTemplate.opsForValue().get(refreshTokenKeyByHash(tokenHash));

        // Cache miss vẫn fallback DB theo hash để an toàn
        return refreshTokenRepository.findByTokenHash(tokenHash).orElse(null);
    }

    public void revokeRefreshTokenAndClearCache(String opaqueRefreshToken) {
        String tokenHash = sha256Hex(opaqueRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.setRevoked(true);
                token.setRevokedAt(Instant.now());
                refreshTokenRepository.save(token);
            }
            redisTemplate.delete(refreshTokenKeyByHash(tokenHash));
        });
    }

    public Map<String, Object> decodeClaims(String bearerAccessToken) {
        String token = bearerAccessToken;
        if (token.startsWith("Bearer ")) {
            token = token.substring("Bearer ".length()).trim();
        }
        return jwtDecoder.decode(token).getClaims();
    }

    public boolean verifyPassword(String rawPassword, String passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash);
    }

    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    private static String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash token", e);
        }
    }

    private static String refreshTokenKeyByHash(String tokenHash) {
        return "auth:refresh:hash:" + tokenHash;
    }
}

