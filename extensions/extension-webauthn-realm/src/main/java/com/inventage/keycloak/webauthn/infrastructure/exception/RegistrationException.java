package com.inventage.keycloak.webauthn.infrastructure.exception;

/**
 * Thrown when registration validation fails.
 * Returns HTTP 400 (Bad Request) status code.
 */
public class RegistrationException extends WebAuthnException {

    /**
     * Creates a registration exception.
     *
     * @param message The error message
     */
    public RegistrationException(String message) {
        super("REGISTRATION_FAILED", message, 400);
    }

    /**
     * Creates a registration exception with cause.
     *
     * @param message The error message
     * @param cause The underlying cause of the exception
     */
    public RegistrationException(String message, Throwable cause) {
        super("REGISTRATION_FAILED", message, 400, cause);
    }
}
