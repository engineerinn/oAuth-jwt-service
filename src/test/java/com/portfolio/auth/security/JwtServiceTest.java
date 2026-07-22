package com.portfolio.auth.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.portfolio.auth.entity.Role;
import com.portfolio.auth.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.*;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that JwtService issues a well-formed RS256 access token whose signature validates
 * against the public key and whose claims carry the subject and roles. No Spring context or DB.
 */
class JwtServiceTest {

    private JwtService jwtService;
    private JwtDecoder decoder;
    private static final long TTL = 900L;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAKey jwk = new RSAKey.Builder(publicKey).privateKey(privateKey)
                .keyID(UUID.randomUUID().toString()).build();

        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        this.decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        this.jwtService = new JwtService(encoder, "portfolio-auth-service", TTL);
    }

    @Test
    void generatesTokenWithSubjectAndRoles() {
        User user = User.builder()
                .id(42L)
                .username("nadia")
                .email("nadia@example.com")
                .password("hashed")
                .role(Role.ADMIN)
                .enabled(true)
                .build();

        String token = jwtService.generateAccessToken(user);
        Jwt decoded = decoder.decode(token);

        assertThat(decoded.getSubject()).isEqualTo("nadia");
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("ADMIN");
        assertThat(((Number) decoded.getClaim("uid")).longValue()).isEqualTo(42L);
        assertThat(decoded.getIssuer().toString()).isEqualTo("portfolio-auth-service");

        Instant expiresAt = decoded.getExpiresAt();
        Instant issuedAt = decoded.getIssuedAt();
        assertThat(expiresAt).isNotNull();
        assertThat(issuedAt).isNotNull();
        assertThat(expiresAt.getEpochSecond() - issuedAt.getEpochSecond()).isEqualTo(TTL);
    }
}
