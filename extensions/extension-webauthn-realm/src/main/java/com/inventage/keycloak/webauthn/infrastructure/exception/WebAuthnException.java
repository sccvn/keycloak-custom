package com.inventage.keycloak.webauthn.infrastructure.exception;

import org.jboss.logging.Logger;

/**
 * Base exception for WebAuthn operations.
 * Provides error code and HTTP status code for proper error handling.
 */
public class WebAuthnException extends Exception {

    private static final Logger LOG = Logger.getLogger(WebAuthnException.class);
    private final String errorCode;
    private final int httpStatusCode;

    /**
     * Creates a WebAuthn exception with error code and message.
     * HTTP status code defaults to 400.
     *
     * @param errorCode The error code identifying the error type
     * @param message The error message
     */
    public WebAuthnException(String errorCode, String message) {
        this(errorCode, message, 400, null);
    }

    /**
     * Creates a WebAuthn exception with error code, message, and HTTP status.
     *
     * @param errorCode The error code identifying the error type
     * @param message The error message
     * @param httpStatusCode The HTTP status code for the error response
     */
    public WebAuthnException(String errorCode, String message, int httpStatusCode) {
        this(errorCode, message, httpStatusCode, null);
    }

    /**
     * Creates a WebAuthn exception with error code, message, HTTP status, and cause.
     *
     * @param errorCode The error code identifying the error type
     * @param message The error message
     * @param httpStatusCode The HTTP status code for the error response
     * @param cause The underlying cause of the exception
     */
    public WebAuthnException(String errorCode, String message, int httpStatusCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatusCode = httpStatusCode;
    }

    /**
     * Gets the error code.
     *
     * @return The error code
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Gets the HTTP status code.
     *
     * @return The HTTP status code
     */
    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    /**
     * Log exception with error code and HTTP status.
     * Uses JBoss Logger for structured logging.
     */
    public void log() {
        LOG.errorf("[%s] HTTP %d: %s", errorCode, httpStatusCode, getMessage());
    }
}
