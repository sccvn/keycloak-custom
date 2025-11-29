package com.inventage.keycloak.webauthn.infrastructure.exception;

/**
 * Thrown when challenge has expired or is invalid.
 * Returns HTTP 400 (Bad Request) status code.
 */
public class ChallengeExpiredException extends WebAuthnException {

    /**
     * Creates a challenge expired exception.
     *
     * @param message The error message
     */
    public ChallengeExpiredException(String message) {
        super("CHALLENGE_EXPIRED", message, 400);
    }
}
