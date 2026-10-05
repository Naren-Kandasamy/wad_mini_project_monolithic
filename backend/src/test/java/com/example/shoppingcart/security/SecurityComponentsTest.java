package com.example.shoppingcart.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityComponentsTest {

    @Test
    @DisplayName("KeycloakJwtAuthenticationConverter extracts realm roles and prefixes with ROLE_")
    void shouldExtractRealmRoles() {
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .subject("user-123")
                .claim("realm_access", Map.of("roles", List.of("USER", "ADMIN")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter();
        AbstractAuthenticationToken auth = converter.convert(jwt);

        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("user-123");

        Collection<String> authorities = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorities).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("KeycloakJwtAuthenticationConverter handles null or empty realm_access cleanly")
    void shouldHandleMissingRolesCleanly() {
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .subject("user-456")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter();
        AbstractAuthenticationToken auth = converter.convert(jwt);

        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("user-456");
        assertThat(auth.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("AudienceValidator succeeds when expected audience is present in aud claim")
    void shouldPassWhenExpectedAudiencePresent() {
        AudienceValidator validator = new AudienceValidator("shopping-cart-api");

        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .subject("user-123")
                .audience(List.of("shopping-cart-api", "account"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("AudienceValidator fails when expected audience is missing")
    void shouldFailWhenExpectedAudienceMissing() {
        AudienceValidator validator = new AudienceValidator("shopping-cart-api");

        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .subject("user-123")
                .audience(List.of("other-api"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isTrue();
        OAuth2Error error = result.getErrors().iterator().next();
        assertThat(error.getErrorCode()).isEqualTo("invalid_token");
        assertThat(error.getDescription()).contains("shopping-cart-api");
    }
}
