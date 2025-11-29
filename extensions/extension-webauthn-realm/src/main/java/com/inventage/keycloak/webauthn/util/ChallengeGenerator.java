package com.inventage.keycloak.webauthn.util;

import java.security.SecureRandom;

/**
 * Generates random challenges for WebAuthn operations.
 * Uses SecureRandom for cryptographically secure random number generation.
 */
public class ChallengeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int DEFAULT_LENGTH = 32;

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
     */
    public static byte[] generate(int length) {
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
