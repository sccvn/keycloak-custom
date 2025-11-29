package com.inventage.keycloak.webauthn.util;

import java.util.Base64;

/**
 * Utility for base64url encoding/decoding.
 * Uses URL-safe Base64 encoding without padding as required by WebAuthn specification.
 */
public class Base64Util {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    /**
     * Private constructor to prevent instantiation.
     */
    private Base64Util() {
        // Utility class
    }

    /**
     * Encode bytes to base64url string.
     *
     * @param data The byte array to encode
     * @return Base64url encoded string
     */
    public static String encodeToString(byte[] data) {
        return ENCODER.encodeToString(data);
    }

    /**
     * Decode base64url string to bytes.
     *
     * @param data The base64url encoded string
     * @return Decoded byte array
     */
    public static byte[] decode(String data) {
        return DECODER.decode(data);
    }

    /**
     * Convert string to base64url encoded string.
     *
     * @param data The string to encode
     * @return Base64url encoded string
     */
    public static String encode(String data) {
        return encodeToString(data.getBytes());
    }

    /**
     * Decode base64url string and convert to string.
     *
     * @param data The base64url encoded string
     * @return Decoded string
     */
    public static String decodeToString(String data) {
        return new String(decode(data));
    }
}
