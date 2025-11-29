package io.inventage.keycloak.custom.webauthn.infrastructure.model;

/**
 * Credential type classification for WebAuthn credentials.
 * Determines how the credential is used during authentication.
 */
public enum CredentialType {
    /**
     * PASSWORDLESS: WebAuthn credential as primary authentication factor.
     * User does not need to provide a password; WebAuthn alone is sufficient.
     * Use case: Mobile apps, enterprise devices with biometric security
     */
    TYPE_PASSWORDLESS("passwordless"),

    /**
     * TWOFACTOR: WebAuthn credential as secondary authentication factor.
     * User must provide password + WebAuthn credential.
     * Use case: Additional security for high-value accounts, compliance requirements
     */
    TYPE_TWOFACTOR("twofactor");

    private final String value;

    CredentialType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Parse credential type from string value
     * @param value The string value to parse
     * @return CredentialType enum, defaults to TYPE_PASSWORDLESS if null or invalid
     */
    public static CredentialType fromValue(String value) {
        if (value == null) {
            return TYPE_PASSWORDLESS; // Default
        }
        for (CredentialType type : CredentialType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return TYPE_PASSWORDLESS;
    }
}
