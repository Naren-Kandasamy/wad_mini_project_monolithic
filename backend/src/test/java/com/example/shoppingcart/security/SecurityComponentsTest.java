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

    @Test
    @DisplayName("HybridJwtDecoder decodes demo tokens successfully")
    void hybridJwtDecoderDecodesDemoTokens() {
        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
        org.springframework.security.oauth2.jwt.JwtDecoder dummyDelegate = token -> null;
        HybridJwtDecoder hybridJwtDecoder = new HybridJwtDecoder(dummyDelegate, mapper);

        String payload = java.util.Base64.getUrlEncoder().encodeToString(
                "{\"sub\":\"alice\",\"email\":\"alice@test.com\",\"roles\":[\"USER\"]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String demoToken = "demo.token." + payload;

        Jwt decoded = hybridJwtDecoder.decode(demoToken);
        assertThat(decoded).isNotNull();
        assertThat(decoded.getSubject()).isEqualTo("alice");
        assertThat(decoded.getClaimAsString("email")).isEqualTo("alice@test.com");
    }

    @Test
    @DisplayName("HybridJwtDecoder provides resilient fallback when IdP delegate throws exception")
    void hybridJwtDecoderFallbackWhenDelegateFails() {
        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
        org.springframework.security.oauth2.jwt.JwtDecoder failingDelegate = token -> {
            throw new org.springframework.security.oauth2.jwt.JwtException("IdP Read timed out");
        };

        HybridJwtDecoder hybridJwtDecoder = new HybridJwtDecoder(failingDelegate, mapper);

        String payloadB64 = java.util.Base64.getUrlEncoder().encodeToString(
                "{\"sub\":\"bob\",\"preferred_username\":\"bob\",\"realm_access\":{\"roles\":[\"USER\"]}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String fakeJwt = "eyJhbGciOiJSUzI1NiJ9." + payloadB64 + ".fakeSignature";

        Jwt decoded = hybridJwtDecoder.decode(fakeJwt);
        assertThat(decoded).isNotNull();
        assertThat(decoded.getSubject()).isEqualTo("bob");
        Map<String, Object> realmAccess = decoded.getClaim("realm_access");
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) realmAccess.get("roles");
        assertThat(roles).contains("USER");
    }
}
