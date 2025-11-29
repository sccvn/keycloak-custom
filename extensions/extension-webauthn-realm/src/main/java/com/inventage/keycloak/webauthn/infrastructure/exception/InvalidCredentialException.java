package com.inventage.keycloak.webauthn.infrastructure.exception;

/**
 * Thrown when credential verification fails.
 * Returns HTTP 401 (Unauthorized) status code.
 */
public class InvalidCredentialException extends WebAuthnException {

    /**
     * Creates an invalid credential exception.
     *
     * @param message The error message
     */
    public InvalidCredentialException(String message) {
        super("INVALID_CREDENTIAL", message, 401);
    }

    /**
     * Creates an invalid credential exception with cause.
     *
     * @param message The error message
     * @param cause The underlying cause of the exception
     */
    public InvalidCredentialException(String message, Throwable cause) {
        super("INVALID_CREDENTIAL", message, 401, cause);
    }
}
