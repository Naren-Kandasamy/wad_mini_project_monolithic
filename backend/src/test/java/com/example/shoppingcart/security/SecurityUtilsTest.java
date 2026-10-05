package com.example.shoppingcart.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Extracts authenticated user ID from JwtAuthenticationToken subject")
    void shouldExtractUserIdFromJwtSubject() {
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .subject("verified-user-uuid-99")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, Collections.emptyList(), jwt.getSubject());
        SecurityContextHolder.getContext().setAuthentication(auth);

        String currentUserId = SecurityUtils.getAuthenticatedUserId();
        assertThat(currentUserId).isEqualTo("verified-user-uuid-99");
    }

    @Test
    @DisplayName("Throws AccessDeniedException when SecurityContext is unauthenticated")
    void shouldThrowWhenUnauthenticated() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(SecurityUtils::getAuthenticatedUserId)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("No authenticated principal");
    }
}
