package com.inventage.keycloak.webauthn.fixtures;

import org.keycloak.credential.CredentialModel;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.when;

/**
 * Test fixtures for WebAuthn unit and integration tests
 *
 * Provides reusable mock data and helper methods for:
 * - WebAuthn credentials
 * - Challenges
 * - Attestation objects
 * - Client data JSON
 * - Authenticator data
 * - Signatures
 */
public class WebAuthnTestFixtures {

    private static final SecureRandom RANDOM = new SecureRandom();

    // Test Data Constants
    public static final String TEST_RP_ID = "test.example.com";
    public static final String TEST_RP_NAME = "Test Application";
    public static final String TEST_USER_ID = "test-user-123";
    public static final String TEST_USERNAME = "testuser";
    public static final String TEST_USER_EMAIL = "testuser@example.com";
    public static final String TEST_CREDENTIAL_ID = "test-credential-id-456";
    public static final String TEST_DEVICE_LABEL = "Test Authenticator";

    // Challenge Generation

    /**
     * Generate a random challenge for testing
     *
     * @param length Challenge length in bytes (default: 32)
     * @return Base64URL-encoded challenge
     */
    public static String generateChallenge(int length) {
        byte[] challenge = new byte[length];
        RANDOM.nextBytes(challenge);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(challenge);
    }

    public static String generateChallenge() {
        return generateChallenge(32);
    }

    // Mock Credential Creation

    /**
     * Create a mock WebAuthn credential for testing
     *
     * @param credentialId Credential identifier
     * @param userLabel User-friendly label
     * @return Mocked CredentialModel
     */
    public static CredentialModel createMockCredential(String credentialId, String userLabel) {
        CredentialModel credential = Mockito.mock(CredentialModel.class);
        when(credential.getId()).thenReturn(credentialId);
        when(credential.getType()).thenReturn("webauthn");
        when(credential.getUserLabel()).thenReturn(userLabel);
        when(credential.getCreatedDate()).thenReturn(System.currentTimeMillis());

        String credentialData = String.format(
            "{\"credentialId\":\"%s\",\"publicKey\":\"%s\",\"signatureCount\":0,\"aaguid\":\"00000000-0000-0000-0000-000000000000\"}",
            credentialId,
            generateMockPublicKey()
        );
        when(credential.getCredentialData()).thenReturn(credentialData);

        return credential;
    }

    public static CredentialModel createMockCredential(String credentialId) {
        return createMockCredential(credentialId, TEST_DEVICE_LABEL);
    }

    public static CredentialModel createMockCredential() {
        return createMockCredential(TEST_CREDENTIAL_ID, TEST_DEVICE_LABEL);
    }

    // WebAuthn Data Structures

    /**
     * Create mock attestation object for registration
     *
     * @return Base64URL-encoded attestation object
     */
    public static String createMockAttestationObject() {
        // Simplified mock - in real implementation would be CBOR-encoded
        Map<String, Object> attestation = new HashMap<>();
        attestation.put("fmt", "none");
        attestation.put("attStmt", new HashMap<>());
        attestation.put("authData", createMockAuthenticatorDataBytes(0));

        // Encode as base64url for testing
        String json = String.format(
            "{\"fmt\":\"none\",\"attStmt\":{},\"authData\":\"%s\"}",
            Base64.getUrlEncoder().withoutPadding().encodeToString(createMockAuthenticatorDataBytes(0))
        );

        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            json.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Create mock client data JSON
     *
     * @param challenge The challenge from server
     * @param type Operation type (webauthn.create or webauthn.get)
     * @return Base64URL-encoded client data JSON
     */
    public static String createMockClientDataJSON(String challenge, String type) {
        String clientData = String.format(
            "{\"type\":\"%s\",\"challenge\":\"%s\",\"origin\":\"https://%s\",\"crossOrigin\":false}",
            type,
            challenge,
            TEST_RP_ID
        );

        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            clientData.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Create mock authenticator data
     *
     * @param signatureCount Signature counter value
     * @return Base64URL-encoded authenticator data
     */
    public static String createMockAuthenticatorData(int signatureCount) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            createMockAuthenticatorDataBytes(signatureCount)
        );
    }

    /**
     * Create mock authenticator data as byte array
     *
     * Structure:
     * - rpIdHash (32 bytes)
     * - flags (1 byte)
     * - signCounter (4 bytes)
     *
     * @param signatureCount Signature counter value
     * @return Authenticator data bytes
     */
    public static byte[] createMockAuthenticatorDataBytes(int signatureCount) {
        byte[] authData = new byte[37]; // Minimum length without attestation

        // RP ID hash (32 bytes) - SHA-256 of RP ID
        byte[] rpIdHash = new byte[32];
        RANDOM.nextBytes(rpIdHash);
        System.arraycopy(rpIdHash, 0, authData, 0, 32);

        // Flags (1 byte)
        // Bit 0: User Present (UP)
        // Bit 2: User Verified (UV)
        byte flags = 0x05; // UP=1, UV=1
        authData[32] = flags;

        // Signature counter (4 bytes, big-endian)
        authData[33] = (byte) ((signatureCount >> 24) & 0xFF);
        authData[34] = (byte) ((signatureCount >> 16) & 0xFF);
        authData[35] = (byte) ((signatureCount >> 8) & 0xFF);
        authData[36] = (byte) (signatureCount & 0xFF);

        return authData;
    }

    /**
     * Create mock signature
     *
     * @return Base64URL-encoded signature
     */
    public static String createMockSignature() {
        // Mock ECDSA signature (64 bytes for ES256: 32 bytes R + 32 bytes S)
        byte[] signature = new byte[64];
        RANDOM.nextBytes(signature);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }

    /**
     * Generate mock public key
     *
     * @return Base64URL-encoded public key
     */
    public static String generateMockPublicKey() {
        // Mock COSE-encoded public key (simplified)
        byte[] publicKey = new byte[77]; // Typical size for ES256 public key
        RANDOM.nextBytes(publicKey);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey);
    }

    // Registration Options

    /**
     * Create mock registration options response
     *
     * @param challenge Challenge to include
     * @return Registration options map
     */
    public static Map<String, Object> createMockRegistrationOptions(String challenge) {
        Map<String, Object> options = new HashMap<>();
        options.put("challenge", challenge);

        Map<String, String> rp = new HashMap<>();
        rp.put("id", TEST_RP_ID);
        rp.put("name", TEST_RP_NAME);
        options.put("rp", rp);

        Map<String, String> user = new HashMap<>();
        user.put("id", encodeBase64Url(TEST_USER_ID));
        user.put("name", TEST_USERNAME);
        user.put("displayName", TEST_USERNAME);
        options.put("user", user);

        options.put("pubKeyCredParams", createPubKeyCredParams());
        options.put("timeout", 60000);
        options.put("attestation", "none");
        options.put("userVerification", "preferred");

        return options;
    }

    /**
     * Create mock authentication options response
     *
     * @param challenge Challenge to include
     * @return Authentication options map
     */
    public static Map<String, Object> createMockAuthenticationOptions(String challenge) {
        Map<String, Object> options = new HashMap<>();
        options.put("challenge", challenge);
        options.put("rpId", TEST_RP_ID);
        options.put("timeout", 60000);
        options.put("userVerification", "preferred");
        options.put("allowCredentials", createAllowCredentials());

        return options;
    }

    // Helper Methods

    private static Object createPubKeyCredParams() {
        Map<String, Object>[] params = new Map[2];

        Map<String, Object> es256 = new HashMap<>();
        es256.put("type", "public-key");
        es256.put("alg", -7); // ES256
        params[0] = es256;

        Map<String, Object> rs256 = new HashMap<>();
        rs256.put("type", "public-key");
        rs256.put("alg", -257); // RS256
        params[1] = rs256;

        return params;
    }

    private static Object createAllowCredentials() {
        Map<String, Object> credential = new HashMap<>();
        credential.put("type", "public-key");
        credential.put("id", TEST_CREDENTIAL_ID);

        return new Map[]{credential};
    }

    /**
     * Encode string to Base64URL
     *
     * @param data String to encode
     * @return Base64URL-encoded string
     */
    public static String encodeBase64Url(String data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            data.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Decode Base64URL string
     *
     * @param encoded Base64URL-encoded string
     * @return Decoded bytes
     */
    public static byte[] decodeBase64Url(String encoded) {
        return Base64.getUrlDecoder().decode(encoded);
    }

    /**
     * Create complete registration data for testing
     *
     * @param challenge Challenge from server
     * @return Registration data map
     */
    public static Map<String, String> createRegistrationData(String challenge) {
        Map<String, String> data = new HashMap<>();
        data.put("credentialId", TEST_CREDENTIAL_ID);
        data.put("attestationObject", createMockAttestationObject());
        data.put("clientDataJSON", createMockClientDataJSON(challenge, "webauthn.create"));
        data.put("credentialType", "passwordless");
        return data;
    }

    /**
     * Create complete assertion data for testing
     *
     * @param challenge Challenge from server
     * @param signatureCount Signature counter value
     * @return Assertion data map
     */
    public static Map<String, String> createAssertionData(String challenge, int signatureCount) {
        Map<String, String> data = new HashMap<>();
        data.put("credentialId", TEST_CREDENTIAL_ID);
        data.put("authenticatorData", createMockAuthenticatorData(signatureCount));
        data.put("clientDataJSON", createMockClientDataJSON(challenge, "webauthn.get"));
        data.put("signature", createMockSignature());
        data.put("userHandle", encodeBase64Url(TEST_USER_ID));
        return data;
    }

    /**
     * Create relying party configuration
     *
     * @return RP configuration map
     */
    public static Map<String, String> createRelyingPartyConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("rpId", TEST_RP_ID);
        config.put("rpName", TEST_RP_NAME);
        config.put("attestation", "none");
        config.put("userVerification", "preferred");
        config.put("timeout", "60000");
        return config;
    }

    /**
     * Create credential metadata
     *
     * @param userLabel User-friendly label for the credential
     * @return Metadata map
     */
    public static Map<String, String> createCredentialMetadata(String userLabel) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("userLabel", userLabel);
        metadata.put("transports", "usb,nfc,ble");
        metadata.put("authenticatorAttachment", "cross-platform");
        return metadata;
    }

    public static Map<String, String> createCredentialMetadata() {
        return createCredentialMetadata(TEST_DEVICE_LABEL);
    }
}
