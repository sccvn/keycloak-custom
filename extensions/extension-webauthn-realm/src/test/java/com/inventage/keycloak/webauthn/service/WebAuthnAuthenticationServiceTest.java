package com.inventage.keycloak.webauthn.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.credential.CredentialModel;
import org.keycloak.credential.UserCredentialManager;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.inventage.keycloak.webauthn.infrastructure.exception.ChallengeExpiredException;
import com.inventage.keycloak.webauthn.infrastructure.exception.WebAuthnException;
import com.inventage.keycloak.webauthn.infrastructure.service.WebAuthnAuthenticationService;

/**
 * TDD Unit Tests for WebAuthn Authentication Service
 *
 * Test Coverage:
 * - Challenge generation with existing credentials
 * - Assertion verification with valid signatures
 * - Clone detection via signature counter
 * - Expired challenge handling
 * - Invalid signature detection
 * - Credential not found scenarios
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebAuthn Authentication Service Tests")
class WebAuthnAuthenticationServiceTest {

    @Mock
    private KeycloakSession session;

    @Mock
    private RealmModel realm;

    @Mock
    private UserModel user;

    @Mock
    private AuthenticationSessionModel authSession;

    @Mock
    private UserCredentialManager credentialManager;

    private WebAuthnAuthenticationService authenticationService;

    private static final String TEST_USER_ID = "test-user-456";
    private static final String TEST_USERNAME = "authuser";
    private static final String TEST_REALM_NAME = "auth-realm";
    private static final String TEST_RP_ID = "auth.example.com";
    private static final String TEST_CREDENTIAL_ID = "credential-id-789";

    @BeforeEach
    void setUp() {
        // Initialize service (will be implemented)
        authenticationService = new WebAuthnAuthenticationService(session);

        // Setup common mocks
        when(session.getContext()).thenReturn(mock(KeycloakContext.class));
        when(session.getContext().getRealm()).thenReturn(realm);
        when(session.users()).thenReturn(mock(UserProvider.class));
        when(realm.getName()).thenReturn(TEST_REALM_NAME);
        when(user.getId()).thenReturn(TEST_USER_ID);
        when(user.getUsername()).thenReturn(TEST_USERNAME);
        when(user.credentialManager()).thenReturn(credentialManager);
    }

    @Test
    @DisplayName("Should generate challenge with existing credentials")
    void testGenerateChallenge_WithCredentials() {
        // Arrange
        CredentialModel credential1 = createMockWebAuthnCredential("cred-001", "Yubikey 5");
        CredentialModel credential2 = createMockWebAuthnCredential("cred-002", "TouchID");

        when(session.users().getUserById(realm, TEST_USER_ID)).thenReturn(user);
        when(credentialManager.getStoredCredentialsStream())
            .thenReturn(Stream.of(credential1, credential2));

        Map<String, String> relyingPartyConfig = new HashMap<>();
        relyingPartyConfig.put("rpId", TEST_RP_ID);
        relyingPartyConfig.put("userVerification", "preferred");
        relyingPartyConfig.put("timeout", "60000");

        // Act
        Map<String, Object> options = authenticationService.generateAuthenticationOptions(
            TEST_USER_ID,
            relyingPartyConfig
        );

        // Assert
        assertThat(options).isNotNull();
        assertThat(options).containsKeys("challenge", "rpId", "timeout", "userVerification", "allowCredentials");

        // Verify challenge
        String challenge = (String) options.get("challenge");
        assertThat(challenge).isNotNull();
        assertThat(challenge.length()).isGreaterThanOrEqualTo(43); // 32 bytes base64url

        // Verify RP ID
        assertThat(options.get("rpId")).isEqualTo(TEST_RP_ID);

        // Verify timeout
        assertThat(options.get("timeout")).isEqualTo(60000);

        // Verify user verification
        assertThat(options.get("userVerification")).isEqualTo("preferred");

        // Verify allowed credentials
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> allowCredentials = (List<Map<String, Object>>) options.get("allowCredentials");
        assertThat(allowCredentials).hasSize(2);
        assertThat(allowCredentials).anyMatch(cred ->
            "cred-001".equals(cred.get("id")) && "public-key".equals(cred.get("type"))
        );
        assertThat(allowCredentials).anyMatch(cred ->
            "cred-002".equals(cred.get("id")) && "public-key".equals(cred.get("type"))
        );
    }

    @Test
    @DisplayName("Should verify assertion with valid signature")
    void testVerifyAssertion_Valid() {
        // Arrange
        String challenge = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("auth-challenge-12345678901234567890".getBytes(StandardCharsets.UTF_8));

        Map<String, String> assertionData = new HashMap<>();
        assertionData.put("credentialId", TEST_CREDENTIAL_ID);
        assertionData.put("authenticatorData", createMockAuthenticatorData(100));
        assertionData.put("clientDataJSON", createMockClientDataJSON(challenge, "webauthn.get"));
        assertionData.put("signature", createMockSignature());
        assertionData.put("userHandle", encodeBase64Url(TEST_USER_ID));

        CredentialModel storedCredential = createMockWebAuthnCredential(TEST_CREDENTIAL_ID, "Test Device");
        storedCredential.setCredentialData("{\"publicKey\":\"mock-public-key-data\",\"signatureCount\":50}");

        when(session.users().getUserById(realm, TEST_USER_ID)).thenReturn(user);
        when(credentialManager.getStoredCredentialById(TEST_CREDENTIAL_ID)).thenReturn(storedCredential);

        // Mock challenge from session
        when(authSession.getAuthNote("webauthn_challenge")).thenReturn(challenge);
        when(authSession.getAuthNote("webauthn_challenge_timestamp"))
            .thenReturn(String.valueOf(System.currentTimeMillis()));

        // Act
        Map<String, Object> result = authenticationService.verifyAssertion(
            TEST_USER_ID,
            assertionData,
            challenge
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.get("verified")).isEqualTo(true);
        assertThat(result.get("userId")).isEqualTo(TEST_USER_ID);
        assertThat(result.get("credentialId")).isEqualTo(TEST_CREDENTIAL_ID);

        // Verify signature counter was updated
        verify(credentialManager, times(1)).updateStoredCredential(argThat(cred ->
            cred.getId().equals(TEST_CREDENTIAL_ID) &&
            cred.getCredentialData().contains("\"signatureCount\":100")
        ));
    }

    @Test
    @DisplayName("Should reject assertion with invalid signature")
    void testVerifyAssertion_InvalidSignature() {
        // Arrange
        String challenge = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("auth-challenge-invalid-sig-1234567890".getBytes(StandardCharsets.UTF_8));

        Map<String, String> assertionData = new HashMap<>();
        assertionData.put("credentialId", TEST_CREDENTIAL_ID);
        assertionData.put("authenticatorData", createMockAuthenticatorData(100));
        assertionData.put("clientDataJSON", createMockClientDataJSON(challenge, "webauthn.get"));
        assertionData.put("signature", "invalid-signature-data");
        assertionData.put("userHandle", encodeBase64Url(TEST_USER_ID));

        CredentialModel storedCredential = createMockWebAuthnCredential(TEST_CREDENTIAL_ID, "Test Device");
        storedCredential.setCredentialData("{\"publicKey\":\"mock-public-key-data\",\"signatureCount\":50}");

        when(session.users().getUserById(realm, TEST_USER_ID)).thenReturn(user);
        when(credentialManager.getStoredCredentialById(TEST_CREDENTIAL_ID)).thenReturn(storedCredential);
        when(authSession.getAuthNote("webauthn_challenge")).thenReturn(challenge);
        when(authSession.getAuthNote("webauthn_challenge_timestamp"))
            .thenReturn(String.valueOf(System.currentTimeMillis()));

        // Act & Assert
        assertThatThrownBy(() ->
            authenticationService.verifyAssertion(TEST_USER_ID, assertionData, challenge)
        )
        .isInstanceOf(WebAuthnException.class)
        .hasMessageContaining("Signature verification failed");

        // Verify credential was not updated
        verify(credentialManager, never()).updateStoredCredential(any());
    }

    @Test
    @DisplayName("Should detect cloned authenticator when signature count does not increase")
    void testVerifyAssertion_SignCountNotIncreased() {
        // Arrange - signature count decreased indicates cloning
        String challenge = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("auth-challenge-clone-detect-123456789".getBytes(StandardCharsets.UTF_8));

        Map<String, String> assertionData = new HashMap<>();
        assertionData.put("credentialId", TEST_CREDENTIAL_ID);
        assertionData.put("authenticatorData", createMockAuthenticatorData(40)); // Lower than stored
        assertionData.put("clientDataJSON", createMockClientDataJSON(challenge, "webauthn.get"));
        assertionData.put("signature", createMockSignature());
        assertionData.put("userHandle", encodeBase64Url(TEST_USER_ID));

        CredentialModel storedCredential = createMockWebAuthnCredential(TEST_CREDENTIAL_ID, "Test Device");
        storedCredential.setCredentialData("{\"publicKey\":\"mock-public-key-data\",\"signatureCount\":100}");

        when(session.users().getUserById(realm, TEST_USER_ID)).thenReturn(user);
        when(credentialManager.getStoredCredentialById(TEST_CREDENTIAL_ID)).thenReturn(storedCredential);
        when(authSession.getAuthNote("webauthn_challenge")).thenReturn(challenge);
        when(authSession.getAuthNote("webauthn_challenge_timestamp"))
            .thenReturn(String.valueOf(System.currentTimeMillis()));

        // Act & Assert
        assertThatThrownBy(() ->
            authenticationService.verifyAssertion(TEST_USER_ID, assertionData, challenge)
        )
        .isInstanceOf(WebAuthnException.class)
        .hasMessageContaining("Possible cloned authenticator detected");

        // Verify credential was not updated
        verify(credentialManager, never()).updateStoredCredential(any());
    }

    @Test
    @DisplayName("Should reject assertion with expired challenge")
    void testVerifyAssertion_ExpiredChallenge() {
        // Arrange
        String challenge = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("expired-auth-challenge-123456789012".getBytes(StandardCharsets.UTF_8));

        Map<String, String> assertionData = new HashMap<>();
        assertionData.put("credentialId", TEST_CREDENTIAL_ID);
        assertionData.put("authenticatorData", createMockAuthenticatorData(100));
        assertionData.put("clientDataJSON", createMockClientDataJSON(challenge, "webauthn.get"));
        assertionData.put("signature", createMockSignature());
        assertionData.put("userHandle", encodeBase64Url(TEST_USER_ID));

        when(session.users().getUserById(realm, TEST_USER_ID)).thenReturn(user);

        // Mock expired challenge (5 minutes ago)
        long expiredTimestamp = System.currentTimeMillis() - (5 * 60 * 1000);
        when(authSession.getAuthNote("webauthn_challenge")).thenReturn(challenge);
        when(authSession.getAuthNote("webauthn_challenge_timestamp"))
            .thenReturn(String.valueOf(expiredTimestamp));

        // Act & Assert
        assertThatThrownBy(() ->
            authenticationService.verifyAssertion(TEST_USER_ID, assertionData, challenge)
        )
        .isInstanceOf(ChallengeExpiredException.class)
        .hasMessageContaining("Challenge has expired");
    }

    @Test
    @DisplayName("Should throw exception when credential not found")
    void testVerifyAssertion_CredentialNotFound() {
        // Arrange
        String challenge = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("valid-challenge-no-cred-12345678901".getBytes(StandardCharsets.UTF_8));

        Map<String, String> assertionData = new HashMap<>();
        assertionData.put("credentialId", "non-existent-credential-id");
        assertionData.put("authenticatorData", createMockAuthenticatorData(100));
        assertionData.put("clientDataJSON", createMockClientDataJSON(challenge, "webauthn.get"));
        assertionData.put("signature", createMockSignature());
        assertionData.put("userHandle", encodeBase64Url(TEST_USER_ID));

        when(session.users().getUserById(realm, TEST_USER_ID)).thenReturn(user);
        when(credentialManager.getStoredCredentialById("non-existent-credential-id")).thenReturn(null);
        when(authSession.getAuthNote("webauthn_challenge")).thenReturn(challenge);
        when(authSession.getAuthNote("webauthn_challenge_timestamp"))
            .thenReturn(String.valueOf(System.currentTimeMillis()));

        // Act & Assert
        assertThatThrownBy(() ->
            authenticationService.verifyAssertion(TEST_USER_ID, assertionData, challenge)
        )
        .isInstanceOf(WebAuthnException.class)
        .hasMessageContaining("Credential not found");
    }

    @Test
    @DisplayName("Should handle empty credential list gracefully")
    void testGenerateChallenge_NoCredentials() {
        // Arrange
        when(session.users().getUserById(realm, TEST_USER_ID)).thenReturn(user);
        when(credentialManager.getStoredCredentialsStream()).thenReturn(Stream.empty());

        Map<String, String> relyingPartyConfig = new HashMap<>();
        relyingPartyConfig.put("rpId", TEST_RP_ID);

        // Act
        Map<String, Object> options = authenticationService.generateAuthenticationOptions(
            TEST_USER_ID,
            relyingPartyConfig
        );

        // Assert
        assertThat(options).isNotNull();
        assertThat(options.get("challenge")).isNotNull();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> allowCredentials = (List<Map<String, Object>>) options.get("allowCredentials");
        assertThat(allowCredentials).isEmpty();
    }

    // Helper methods

    private CredentialModel createMockWebAuthnCredential(String credentialId, String userLabel) {
        CredentialModel credential = mock(CredentialModel.class);
        when(credential.getId()).thenReturn(credentialId);
        when(credential.getType()).thenReturn("webauthn");
        when(credential.getUserLabel()).thenReturn(userLabel);
        when(credential.getCreatedDate()).thenReturn(System.currentTimeMillis());
        return credential;
    }

    private String createMockAuthenticatorData(int signatureCount) {
        // Mock authenticator data structure:
        // rpIdHash (32 bytes) + flags (1 byte) + signCounter (4 bytes)
        byte[] rpIdHash = new byte[32];
        Arrays.fill(rpIdHash, (byte) 0x01);

        byte flags = 0x01; // User present flag

        byte[] counter = new byte[4];
        counter[0] = (byte) ((signatureCount >> 24) & 0xFF);
        counter[1] = (byte) ((signatureCount >> 16) & 0xFF);
        counter[2] = (byte) ((signatureCount >> 8) & 0xFF);
        counter[3] = (byte) (signatureCount & 0xFF);

        byte[] authData = new byte[37];
        System.arraycopy(rpIdHash, 0, authData, 0, 32);
        authData[32] = flags;
        System.arraycopy(counter, 0, authData, 33, 4);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(authData);
    }

    private String createMockClientDataJSON(String challenge, String type) {
        String clientData = String.format(
            "{\"type\":\"%s\",\"challenge\":\"%s\",\"origin\":\"https://%s\"}",
            type,
            challenge,
            TEST_RP_ID
        );
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            clientData.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String createMockSignature() {
        // Mock signature (in real implementation verified against public key)
        byte[] mockSignature = "mock-ecdsa-signature-r-and-s-values-123456789012345678901234567890".getBytes(StandardCharsets.UTF_8);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mockSignature);
    }

    private String encodeBase64Url(String data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            data.getBytes(StandardCharsets.UTF_8)
        );
    }
}
