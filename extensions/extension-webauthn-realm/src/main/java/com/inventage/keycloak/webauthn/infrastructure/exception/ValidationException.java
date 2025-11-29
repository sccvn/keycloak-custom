package com.inventage.keycloak.webauthn.infrastructure.exception;

/**
 * Thrown when request validation fails.
 * Returns HTTP 400 (Bad Request) status code.
 */
public class ValidationException extends WebAuthnException {

    /**
     * Creates a validation exception.
     *
     * @param message The error message
     */
    public ValidationException(String message) {
        super("VALIDATION_FAILED", message, 400);
    }
}
