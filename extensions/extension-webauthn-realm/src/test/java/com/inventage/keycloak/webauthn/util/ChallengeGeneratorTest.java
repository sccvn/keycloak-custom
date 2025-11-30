package com.inventage.keycloak.webauthn.util;

import static org.assertj.core.api.Assertions.*;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * TDD Unit Tests for Challenge Generator
 *
 * Test Coverage:
 * - Challenge generation uniqueness
 * - Challenge length validation
 * - Cryptographic randomness
 * - Base64URL encoding compliance
 * - Thread safety for concurrent generation
 */
@DisplayName("Challenge Generator Tests")
class ChallengeGeneratorTest {

    private static final int DEFAULT_CHALLENGE_LENGTH = 32; // bytes
    private static final int MIN_CHALLENGE_LENGTH = 16; // bytes
    private static final int MAX_CHALLENGE_LENGTH = 64; // bytes

    @Test
    @DisplayName("Should generate challenge with default length")
    void testGenerateChallenge_DefaultLength() {
        // Act
        String challenge = ChallengeGenerator.generateBase64Url();

        // Assert
        assertThat(challenge).isNotNull();
        assertThat(challenge).isNotEmpty();

        // Decode to verify length
        byte[] decoded = Base64.getUrlDecoder().decode(challenge);
        assertThat(decoded).hasSize(DEFAULT_CHALLENGE_LENGTH);
    }

    @Test
    @DisplayName("Should generate challenge with custom length")
    void testGenerateChallenge_CustomLength() {
        // Arrange
        int customLength = 48;

        // Act
        byte[] challenge = ChallengeGenerator.generate(customLength);

        // Assert
        assertThat(challenge).isNotNull();
        assertThat(challenge).hasSize(customLength);
    }

    @Test
    @DisplayName("Should generate URL-safe Base64 encoded challenge")
    void testGenerateChallenge_UrlSafe() {
        // Act
        String challenge = ChallengeGenerator.generateBase64Url();

        // Assert
        assertThat(challenge).doesNotContain("+");
        assertThat(challenge).doesNotContain("/");
        assertThat(challenge).doesNotContain("="); // No padding
        assertThat(challenge).matches("[A-Za-z0-9_-]+");
    }

    @RepeatedTest(10)
    @DisplayName("Should generate unique challenges")
    void testGenerateChallenge_Uniqueness() {
        // Arrange
        Set<String> challenges = new HashSet<>();

        // Act
        for (int i = 0; i < 1000; i++) {
            challenges.add(ChallengeGenerator.generateBase64Url());
        }

        // Assert - all challenges should be unique
        assertThat(challenges).hasSize(1000);
    }

    @Test
    @DisplayName("Should use cryptographically secure random")
    void testGenerateChallenge_CryptographicallySecurity() {
        // Arrange
        String challenge1 = ChallengeGenerator.generateBase64Url();
        String challenge2 = ChallengeGenerator.generateBase64Url();

        // Assert - challenges should be different and unpredictable
        assertThat(challenge1).isNotEqualTo(challenge2);

        byte[] bytes1 = Base64.getUrlDecoder().decode(challenge1);
        byte[] bytes2 = Base64.getUrlDecoder().decode(challenge2);

        // Check for sufficient entropy (no repeated patterns)
        int matchingBytes = 0;
        for (int i = 0; i < bytes1.length; i++) {
            if (bytes1[i] == bytes2[i]) {
                matchingBytes++;
            }
        }

        // Less than 10% should match by chance
        assertThat(matchingBytes).isLessThan(bytes1.length / 10);
    }

    @Test
    @DisplayName("Should throw exception for invalid challenge length")
    void testGenerateChallenge_InvalidLength_TooSmall() {
        // Act & Assert
        assertThatThrownBy(() -> ChallengeGenerator.generate(8))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Challenge length must be between");
    }

    @Test
    @DisplayName("Should throw exception for challenge length too large")
    void testGenerateChallenge_InvalidLength_TooLarge() {
        // Act & Assert
        assertThatThrownBy(() -> ChallengeGenerator.generate(128))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Challenge length must be between");
    }

    @Test
    @DisplayName("Should accept minimum valid challenge length")
    void testGenerateChallenge_MinimumLength() {
        // Act
        byte[] challenge = ChallengeGenerator.generate(MIN_CHALLENGE_LENGTH);

        // Assert
        assertThat(challenge).hasSize(MIN_CHALLENGE_LENGTH);
    }

    @Test
    @DisplayName("Should accept maximum valid challenge length")
    void testGenerateChallenge_MaximumLength() {
        // Act
        byte[] challenge = ChallengeGenerator.generate(MAX_CHALLENGE_LENGTH);

        // Assert
        assertThat(challenge).hasSize(MAX_CHALLENGE_LENGTH);
    }

    @Test
    @DisplayName("Should generate challenges with high entropy")
    void testGenerateChallenge_HighEntropy() {
        // Act
        String challenge = ChallengeGenerator.generateBase64Url();
        byte[] bytes = Base64.getUrlDecoder().decode(challenge);

        // Assert - check for reasonable entropy
        // Count unique byte values
        Set<Byte> uniqueBytes = new HashSet<>();
        for (byte b : bytes) {
            uniqueBytes.add(b);
        }

        // Should have at least 50% unique byte values
        assertThat(uniqueBytes.size()).isGreaterThanOrEqualTo(bytes.length / 2);
    }

    @Test
    @DisplayName("Should generate valid WebAuthn challenge format")
    void testGenerateChallenge_WebAuthnFormat() {
        // Act
        String challenge = ChallengeGenerator.generateBase64Url();

        // Assert - WebAuthn spec requires challenges to be at least 16 bytes
        byte[] decoded = Base64.getUrlDecoder().decode(challenge);
        assertThat(decoded.length).isGreaterThanOrEqualTo(16);

        // Challenge should be base64url encoded
        assertThat(challenge).matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    @DisplayName("Should be thread-safe for concurrent generation")
    void testGenerateChallenge_ThreadSafety() throws InterruptedException {
        // Arrange
        int threadCount = 10;
        int challengesPerThread = 100;
        Set<String> allChallenges = new HashSet<>();

        Thread[] threads = new Thread[threadCount];

        // Act
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < challengesPerThread; j++) {
                    synchronized (allChallenges) {
                        allChallenges.add(ChallengeGenerator.generateBase64Url());
                    }
                }
            });
            threads[i].start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }

        // Assert - all challenges should be unique
        assertThat(allChallenges).hasSize(threadCount * challengesPerThread);
    }

    @Test
    @DisplayName("Should generate challenge with sufficient randomness distribution")
    void testGenerateChallenge_RandomnessDistribution() {
        // Act - generate multiple challenges
        int[] bitCounts = new int[8]; // Count bits set in each position

        for (int i = 0; i < 1000; i++) {
            byte[] challenge = ChallengeGenerator.generate();

            for (byte b : challenge) {
                for (int bit = 0; bit < 8; bit++) {
                    if ((b & (1 << bit)) != 0) {
                        bitCounts[bit]++;
                    }
                }
            }
        }

        // Assert - each bit position should be set roughly 50% of the time
        int totalBytes = 1000 * DEFAULT_CHALLENGE_LENGTH;
        for (int count : bitCounts) {
            double ratio = (double) count / totalBytes;
            assertThat(ratio).isBetween(0.45, 0.55); // Within 5% of 50%
        }
    }

    @Test
    @DisplayName("Should generate challenge compatible with WebAuthn specification")
    void testGenerateChallenge_WebAuthnSpecCompliance() {
        // Act
        String challenge = ChallengeGenerator.generateBase64Url();

        // Assert - WebAuthn spec compliance
        // 1. Must be base64url encoded
        assertThatCode(() -> Base64.getUrlDecoder().decode(challenge))
            .doesNotThrowAnyException();

        // 2. Should be at least 16 bytes (recommended 32)
        byte[] decoded = Base64.getUrlDecoder().decode(challenge);
        assertThat(decoded.length).isGreaterThanOrEqualTo(16);

        // 3. No padding characters
        assertThat(challenge).doesNotContain("=");

        // 4. URL-safe characters only
        assertThat(challenge).matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    @DisplayName("Should generate unique random challenges")
    void testGenerateChallengeId() {
        // Act
        String challengeId1 = ChallengeGenerator.generateBase64Url();
        String challengeId2 = ChallengeGenerator.generateBase64Url();

        // Assert
        assertThat(challengeId1).isNotNull();
        assertThat(challengeId2).isNotNull();
        assertThat(challengeId1).isNotEqualTo(challengeId2);

        // Both should be valid Base64URL encoded strings
        assertThat(challengeId1).matches("^[A-Za-z0-9_-]+$");
        assertThat(challengeId2).matches("^[A-Za-z0-9_-]+$");
    }
}
