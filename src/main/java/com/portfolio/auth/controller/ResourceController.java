package com.portfolio.auth.controller;

import com.portfolio.auth.dto.UserResponse;
import com.portfolio.auth.entity.User;
import com.portfolio.auth.exception.AuthException;
import com.portfolio.auth.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Protected resources demonstrating the resource-server side: any valid access token can read /api/me,
 * while /api/admin/** requires the ADMIN role (enforced in SecurityConfig).
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Resources", description = "Endpoints protected by the JWT resource server")
public class ResourceController {

    private final UserRepository userRepository;

    @GetMapping("/api/me")
    @Operation(summary = "Return the authenticated user's profile")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        User user = userRepository.findByUsername(jwt.getSubject())
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "User not found"));
        return UserResponse.from(user);
    }

    @GetMapping("/api/admin/overview")
    @Operation(summary = "Admin-only endpoint demonstrating role-based access control")
    public Map<String, Object> adminOverview() {
        return Map.of(
                "message", "You have ADMIN access",
                "totalUsers", userRepository.count());
    }
}
