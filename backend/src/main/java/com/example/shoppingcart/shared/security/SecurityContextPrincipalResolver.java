package com.example.shoppingcart.shared.security;

import com.example.shoppingcart.security.SecurityUtils;
import org.springframework.stereotype.Component;

/**
 * Server-authoritative principal resolver delegating directly to validated
 * Keycloak JWT token subject via {@link SecurityUtils}.
 */
@Component
public class SecurityContextPrincipalResolver {

    public String getCurrentUserId() {
        return SecurityUtils.getAuthenticatedUserId();
    }
}
