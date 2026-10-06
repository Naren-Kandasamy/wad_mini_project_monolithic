package com.example.shoppingcart.auth.controller;

import com.example.shoppingcart.auth.dto.RegisterRequest;
import com.example.shoppingcart.auth.service.KeycloakRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final KeycloakRegistrationService registrationService;

    public AuthController(KeycloakRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        boolean createdInKeycloak = registrationService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "User registration accepted",
                "username", request.username(),
                "createdInKeycloak", createdInKeycloak
        ));
    }
}
