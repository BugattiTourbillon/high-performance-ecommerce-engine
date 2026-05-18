package com.ecommerce.engine.dto.auth;

public record AuthResponse(
    String accessToken,
    String username,
    String role
) {
}
