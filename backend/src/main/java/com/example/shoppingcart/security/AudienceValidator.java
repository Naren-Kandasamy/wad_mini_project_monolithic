package com.example.shoppingcart.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.Assert;

import java.util.List;

public class AudienceValidator implements OAuth2TokenValidator<Jwt> {
    private final String expectedAudience;

    public AudienceValidator(String expectedAudience) {
        Assert.hasText(expectedAudience, "expectedAudience cannot be null or empty");
        this.expectedAudience = expectedAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        List<String> audiences = jwt.getAudience();
        if (audiences != null && audiences.contains(expectedAudience)) {
            return OAuth2TokenValidatorResult.success();
        }
        OAuth2Error error = new OAuth2Error(
                "invalid_token",
                String.format("The required audience '%s' is missing in token audience claim: %s", expectedAudience, audiences),
                null
        );
        return OAuth2TokenValidatorResult.failure(error);
    }
}
