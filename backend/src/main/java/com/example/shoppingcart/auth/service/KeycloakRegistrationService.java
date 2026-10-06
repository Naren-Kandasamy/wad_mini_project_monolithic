package com.example.shoppingcart.auth.service;

import com.example.shoppingcart.auth.dto.RegisterRequest;
import com.example.shoppingcart.shared.error.ConflictException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class KeycloakRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakRegistrationService.class);

    private final String issuerUri;
    private final String adminUsername;
    private final String adminPassword;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public KeycloakRegistrationService(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:8180/realms/shopping-cart}") String issuerUri,
            @Value("${app.keycloak.admin-username:${KC_BOOTSTRAP_ADMIN_USERNAME:admin}}") String adminUsername,
            @Value("${app.keycloak.admin-password:${KC_BOOTSTRAP_ADMIN_PASSWORD:admin}}") String adminPassword,
            ObjectMapper objectMapper) {
        this.issuerUri = issuerUri;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build();
    }

    public boolean registerUser(RegisterRequest request) {
        String cleanIssuer = issuerUri.replaceAll("/+$", "");
        // Extract base URL e.g. https://shopping-cart-keycloak.onrender.com
        String baseUrl = cleanIssuer.contains("/realms/")
                ? cleanIssuer.substring(0, cleanIssuer.indexOf("/realms/"))
                : cleanIssuer;

        try {
            // 1. Obtain admin access token from master realm
            String adminToken = fetchAdminToken(baseUrl);
            if (adminToken == null || adminToken.isBlank()) {
                log.warn("Could not obtain admin token from Keycloak. Fallback mode enabled.");
                return false;
            }

            // 2. Register user in 'shopping-cart' realm
            String usersUrl = baseUrl + "/admin/realms/shopping-cart/users";
            Map<String, Object> userPayload = Map.of(
                    "username", request.username().trim(),
                    "email", request.email() != null ? request.email().trim().toLowerCase() : request.username().trim() + "@example.com",
                    "enabled", true,
                    "emailVerified", true,
                    "firstName", request.name() != null && !request.name().isBlank() ? request.name().trim() : request.username().trim(),
                    "credentials", List.of(Map.of(
                            "type", "password",
                            "value", request.password(),
                            "temporary", false
                    ))
            );

            String bodyJson = objectMapper.writeValueAsString(userPayload);
            HttpRequest createRequest = HttpRequest.newBuilder()
                    .uri(URI.create(usersUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + adminToken)
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<String> response = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                log.info("Successfully registered user '{}' in Keycloak realm 'shopping-cart'", request.username());
                return true;
            } else if (response.statusCode() == 409) {
                log.warn("Keycloak reported conflict: User '{}' already exists", request.username());
                throw new ConflictException("An account with username '" + request.username() + "' or this email already exists");
            } else {
                log.warn("Keycloak user creation returned HTTP {}: {}", response.statusCode(), response.body());
                return false;
            }
        } catch (ConflictException ce) {
            throw ce;
        } catch (Exception e) {
            log.warn("Keycloak registration connection failed: {}. Continuing with resilient fallback.", e.getMessage());
            return false;
        }
    }

    private String fetchAdminToken(String baseUrl) {
        try {
            String tokenUrl = baseUrl + "/realms/master/protocol/openid-connect/token";
            String formBody = "client_id=" + URLEncoder.encode("admin-cli", StandardCharsets.UTF_8)
                    + "&grant_type=" + URLEncoder.encode("password", StandardCharsets.UTF_8)
                    + "&username=" + URLEncoder.encode(adminUsername, StandardCharsets.UTF_8)
                    + "&password=" + URLEncoder.encode(adminPassword, StandardCharsets.UTF_8);

            HttpRequest tokenRequest = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(4))
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            HttpResponse<String> response = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                return root.path("access_token").asText(null);
            }
            log.debug("Admin token fetch returned status: {}", response.statusCode());
            return null;
        } catch (Exception e) {
            log.debug("Could not fetch admin token: {}", e.getMessage());
            return null;
        }
    }
}
