package com.example.shoppingcart.shared.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.context.request.WebRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final WebRequest webRequest = mock(WebRequest.class);

    @Test
    @DisplayName("Handles AccessDeniedException with 403 Forbidden ProblemDetail")
    void shouldHandleAccessDeniedException() {
        AccessDeniedException ex = new AccessDeniedException("Access is denied");
        ResponseEntity<ProblemDetail> response = handler.handleAccessDenied(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ProblemDetail problem = response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(403);
        assertThat(problem.getTitle()).isEqualTo("Forbidden");
        assertThat(problem.getDetail()).isEqualTo("Access is denied");
    }

    @Test
    @DisplayName("Handles AuthenticationException with 401 Unauthorized ProblemDetail")
    void shouldHandleAuthenticationException() {
        AuthenticationException ex = new AuthenticationException("Full authentication is required") {};
        ResponseEntity<ProblemDetail> response = handler.handleAuthenticationException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ProblemDetail problem = response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(401);
        assertThat(problem.getTitle()).isEqualTo("Unauthorized");
    }

    @Test
    @DisplayName("Handles ResourceNotFoundException with 404 Not Found ProblemDetail")
    void shouldHandleResourceNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Order not found");
        ResponseEntity<ProblemDetail> response = handler.handleResourceNotFound(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ProblemDetail problem = response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getTitle()).isEqualTo("Not Found");
        assertThat(problem.getDetail()).isEqualTo("Order not found");
    }

    @Test
    @DisplayName("Handles ConflictException with 409 Conflict ProblemDetail")
    void shouldHandleConflictException() {
        ConflictException ex = new ConflictException("Cart version conflict");
        ResponseEntity<ProblemDetail> response = handler.handleConflict(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ProblemDetail problem = response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getTitle()).isEqualTo("Conflict");
        assertThat(problem.getDetail()).isEqualTo("Cart version conflict");
    }

    @Test
    @DisplayName("Handles unhandled generic Exception without exposing stack traces")
    void shouldHandleGenericExceptionSafely() {
        Exception ex = new RuntimeException("Sensitive database credentials error inside stack trace");
        ResponseEntity<ProblemDetail> response = handler.handleGeneralException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ProblemDetail problem = response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getTitle()).isEqualTo("Internal Server Error");
        assertThat(problem.getDetail()).doesNotContain("credentials");
        assertThat(problem.getDetail()).isEqualTo("An unexpected server error occurred.");
    }
}
