package com.inventage.keycloak.webauthn.util;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * TDD Unit Tests for Base64 Utility
 *
 * Test Coverage:
 * - Base64URL encoding/decoding
 * - Standard Base64 encoding/decoding
 * - Padding handling
 * - Binary data encoding
 * - Error handling for invalid input
 */
@DisplayName("Base64 Utility Tests")
class Base64UtilTest {

    @Test
    @DisplayName("Should encode string to Base64URL without padding")
    void testEncodeBase64Url_String() {
        // Arrange
        String input = "Hello, WebAuthn!";

        // Act
        String encoded = Base64Util.encode(input);

        // Assert
        assertThat(encoded).isNotNull();
        assertThat(encoded).doesNotContain("="); // No padding
        assertThat(encoded).doesNotContain("+"); // URL-safe
        assertThat(encoded).doesNotContain("/"); // URL-safe

        // Verify can decode back
        byte[] decoded = Base64Util.decode(encoded);
        assertThat(new String(decoded, StandardCharsets.UTF_8)).isEqualTo(input);
    }

    @Test
    @DisplayName("Should decode Base64URL string")
    void testDecodeBase64Url_Valid() {
        // Arrange
        String original = "WebAuthn Challenge 123";
        String encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(original.getBytes(StandardCharsets.UTF_8));

        // Act
        byte[] decoded = Base64Util.decode(encoded);

        // Assert
        assertThat(decoded).isNotNull();
        assertThat(new String(decoded, StandardCharsets.UTF_8)).isEqualTo(original);
    }

    @Test
    @DisplayName("Should handle binary data encoding")
    void testEncodeBase64Url_BinaryData() {
        // Arrange - create binary data with various byte values
        byte[] binaryData = new byte[256];
        for (int i = 0; i < 256; i++) {
            binaryData[i] = (byte) i;
        }

        // Act
        String encoded = Base64Util.encodeToString(binaryData);

        // Assert
        assertThat(encoded).isNotNull();
        assertThat(encoded.length()).isGreaterThan(0);

        // Verify round trip
        byte[] decoded = Base64Util.decode(encoded);
        assertThat(decoded).isEqualTo(binaryData);
    }

    @Test
    @DisplayName("Should encode to URL-safe Base64 without padding")
    void testEncodeBase64_WithPadding() {
        // Arrange
        String input = "Test";

        // Act
        String encoded = Base64Util.encodeToString(input.getBytes(StandardCharsets.UTF_8));

        // Assert
        assertThat(encoded).isNotNull();
        assertThat(encoded).doesNotContain("="); // No padding (URL-safe)
        assertThat(encoded).doesNotContain("+"); // URL-safe
        assertThat(encoded).doesNotContain("/"); // URL-safe

        byte[] decoded = Base64Util.decode(encoded);
        assertThat(new String(decoded, StandardCharsets.UTF_8)).isEqualTo(input);
    }

    @Test
    @DisplayName("Should decode standard Base64 with padding")
    void testDecodeBase64_WithPadding() {
        // Arrange
        String encoded = "VGVzdA=="; // "Test" in Base64

        // Act
        byte[] decoded = Base64Util.decode(encoded);

        // Assert
        assertThat(new String(decoded, StandardCharsets.UTF_8)).isEqualTo("Test");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "A",
        "AB",
        "ABC",
        "ABCD",
        "Hello World",
        "The quick brown fox jumps over the lazy dog"
    })
    @DisplayName("Should handle different input lengths")
    void testEncodeBase64Url_VariousLengths(String input) {
        // Act
        String encoded = Base64Util.encodeToString(input.getBytes(StandardCharsets.UTF_8));
        byte[] decoded = Base64Util.decode(encoded);

        // Assert
        assertThat(new String(decoded, StandardCharsets.UTF_8)).isEqualTo(input);
    }

    @Test
    @DisplayName("Should handle empty byte array")
    void testEncodeBase64Url_EmptyArray() {
        // Arrange
        byte[] empty = new byte[0];

        // Act
        String encoded = Base64Util.encodeToString(empty);

        // Assert
        assertThat(encoded).isEmpty();

        byte[] decoded = Base64Util.decode(encoded);
        assertThat(decoded).isEmpty();
    }

    @Test
    @DisplayName("Should throw exception for null input on encode")
    void testEncodeBase64Url_NullInput() {
        // Act & Assert
        assertThatThrownBy(() -> Base64Util.encode(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Input cannot be null");
    }

    @Test
    @DisplayName("Should throw exception for null input on decode")
    void testDecodeBase64Url_NullInput() {
        // Act & Assert
        assertThatThrownBy(() -> Base64Util.decode(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Input cannot be null");
    }

    @Test
    @DisplayName("Should throw exception for invalid Base64URL string")
    void testDecodeBase64Url_InvalidInput() {
        // Arrange
        String invalidBase64 = "This is not valid Base64!!!";

        // Act & Assert
        assertThatThrownBy(() -> Base64Util.decode(invalidBase64))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should encode string and verify URL-safe encoding")
    void testConvertBase64ToBase64Url() {
        // Arrange - data that would produce + and / in standard Base64
        String input = "Hello?World>";

        // Act
        String encoded = Base64Util.encode(input);

        // Assert - verify it uses URL-safe characters
        assertThat(encoded).doesNotContain("+"); // URL-safe encoding
        assertThat(encoded).doesNotContain("/"); // URL-safe encoding
        assertThat(encoded).doesNotContain("=");  // No padding

        // Verify round-trip
        String decoded = Base64Util.decodeToString(encoded);
        assertThat(decoded).isEqualTo(input);
    }

    @Test
    @DisplayName("Should decode Base64URL with URL-safe characters")
    void testConvertBase64UrlToBase64() {
        // Arrange
        String base64Url = "SGVsbG8tV29ybGQ_"; // Base64URL encoded string
        String expected = "Hello-World?"; // Expected decoded output

        // Act
        String decoded = Base64Util.decodeToString(base64Url);

        // Assert
        assertThat(decoded).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should correctly encode WebAuthn challenge")
    void testEncodeBase64Url_WebAuthnChallenge() {
        // Arrange - typical 32-byte challenge
        byte[] challenge = new byte[32];
        for (int i = 0; i < 32; i++) {
            challenge[i] = (byte) (i * 8);
        }

        // Act
        String encoded = Base64Util.encodeToString(challenge);

        // Assert
        assertThat(encoded.length()).isEqualTo(43); // 32 bytes = 43 chars base64url
        assertThat(encoded).doesNotContain("=");

        byte[] decoded = Base64Util.decode(encoded);
        assertThat(decoded).hasSize(32);
        assertThat(decoded).isEqualTo(challenge);
    }

    @Test
    @DisplayName("Should handle special characters in URL-safe encoding")
    void testEncodeBase64Url_SpecialCharacters() {
        // Arrange - data that would produce + and / in standard Base64
        byte[] data = new byte[]{(byte) 0xFB, (byte) 0xFF, (byte) 0xBF, (byte) 0xFF};

        // Act
        String encoded = Base64Util.encodeToString(data);

        // Assert
        assertThat(encoded).doesNotContain("+");
        assertThat(encoded).doesNotContain("/");
        assertThat(encoded).matches("[A-Za-z0-9_-]+"); // Only URL-safe characters

        byte[] decoded = Base64Util.decode(encoded);
        assertThat(decoded).isEqualTo(data);
    }
}
