package com.portfolio.auth.service;

import com.portfolio.auth.entity.RefreshToken;
import com.portfolio.auth.entity.User;
import com.portfolio.auth.exception.AuthException;
import com.portfolio.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${security.jwt.refresh-token-ttl-seconds}")
    private long refreshTtlSeconds;

    /** Result of a successful rotation: the owning user plus a freshly minted raw refresh token. */
    public record RotatedToken(User user, String rawToken) {}

    /** Issues a new opaque refresh token, persisting only its SHA-256 hash. */
    @Transactional
    public String create(User user) {
        String raw = generateRawToken();
        RefreshToken token = RefreshToken.builder()
                .tokenHash(sha256Hex(raw))
                .user(user)
                .expiresAt(Instant.now().plusSeconds(refreshTtlSeconds))
                .revoked(false)
                .build();
        repository.save(token);
        return raw;
    }

    /**
     * Validates and rotates a refresh token.
     *
     * Single-use semantics: a valid token is revoked and replaced. Presenting an already-revoked
     * token is treated as a possible theft (the legitimate client already rotated it), so every
     * active token for that user is revoked defensively.
     */
    @Transactional
    public RotatedToken rotate(String rawToken) {
        RefreshToken stored = repository.findByTokenHash(sha256Hex(rawToken))
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (stored.isRevoked()) {
            repository.revokeAllForUser(stored.getUser());
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected; sessions revoked");
        }
        if (stored.isExpired()) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }

        stored.setRevoked(true);
        repository.save(stored);

        User user = stored.getUser();
        return new RotatedToken(user, create(user));
    }

    /** Revokes a refresh token on logout. Unknown/already-revoked tokens are treated as a no-op. */
    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(sha256Hex(rawToken)).ifPresent(token -> {
            token.setRevoked(true);
            repository.save(token);
        });
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
