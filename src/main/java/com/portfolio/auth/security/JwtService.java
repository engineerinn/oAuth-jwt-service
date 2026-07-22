package com.portfolio.auth.security;

import com.portfolio.auth.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Issues short-lived, RS256-signed access tokens using Spring Security's native JWT encoder.
 * Roles are emitted in a "roles" claim and mapped to authorities on the resource-server side.
 */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final String issuer;
    private final long accessTtlSeconds;

    public JwtService(JwtEncoder encoder,
                      @Value("${security.jwt.issuer}") String issuer,
                      @Value("${security.jwt.access-token-ttl-seconds}") long accessTtlSeconds) {
        this.encoder = encoder;
        this.issuer = issuer;
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(accessTtlSeconds))
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("roles", List.of(user.getRole().name()))
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }
}
