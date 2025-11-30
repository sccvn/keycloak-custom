package com.inventage.keycloak.webauthn.util;

import java.security.SecureRandom;

/**
 * Generates random challenges for WebAuthn operations.
 * Uses SecureRandom for cryptographically secure random number generation.
 */
public class ChallengeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int DEFAULT_LENGTH = 32;
    private static final int MIN_LENGTH = 16;
    private static final int MAX_LENGTH = 64;

    /**
     * Private constructor to prevent instantiation.
     */
    private ChallengeGenerator() {
        // Utility class
    }

    /**
     * Generate random challenge with specified length.
     *
     * @param length Challenge length in bytes
     * @return Random challenge bytes
     * @throws IllegalArgumentException if length is not between 16 and 64 bytes
     */
    public static byte[] generate(int length) {
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new IllegalArgumentException(
                String.format("Challenge length must be between %d and %d bytes, got %d",
                    MIN_LENGTH, MAX_LENGTH, length));
        }
        byte[] challenge = new byte[length];
        RANDOM.nextBytes(challenge);
        return challenge;
    }

    /**
     * Generate default length challenge (32 bytes).
     *
     * @return Random challenge bytes (32 bytes)
     */
    public static byte[] generate() {
        return generate(DEFAULT_LENGTH);
    }

    /**
     * Generate challenge as base64url encoded string.
     * Uses default length of 32 bytes.
     *
     * @return Base64url encoded challenge string
     */
    public static String generateBase64Url() {
        return Base64Util.encodeToString(generate());
    }
}
