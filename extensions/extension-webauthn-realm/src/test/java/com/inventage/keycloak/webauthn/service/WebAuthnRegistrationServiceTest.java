package com.inventage.keycloak.webauthn.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import com.inventage.keycloak.webauthn.infrastructure.service.WebAuthnRegistrationService;

/**
 * TDD Unit Tests for WebAuthn Registration Service
 *
 * Test Coverage:
 * - Challenge generation and validation
 * - Credential verification and storage
 * - Credential type classification (passwordless vs 2FA)
 * - Error handling for expired challenges and invalid signatures
 * - User verification requirements
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebAuthn Registration Service Tests")
class WebAuthnRegistrationServiceTest {

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

    private WebAuthnRegistrationService registrationService;

    private static final String TEST_USER_ID = "test-user-123";
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_REALM_NAME = "test-realm";
    private static final String TEST_RP_ID = "example.com";
    private static final String TEST_RP_NAME = "Example Corp";

    @BeforeEach
    void setUp() {
        // Initialize service (will be implemented)
        registrationService = new WebAuthnRegistrationService(session);

        // Setup common mocks
        when(session.getContext()).thenReturn(mock(KeycloakContext.class));
        when(session.getContext().getRealm()).thenReturn(realm);
        when(session.users()).thenReturn(mock(UserProvider.class));
        when(realm.getName()).thenReturn(TEST_REALM_NAME);
        when(user.getId()).thenReturn(TEST_USER_ID);
        when(user.getUsername()).thenReturn(TEST_USERNAME);
        when(user.getEmail()).thenReturn("test@example.com");
    }

    @Test
    @DisplayName("Should generate valid challenge for existing user")
    void testGenerateChallenge_Success() {
        // Arrange
        Map<String, String> relyingPartyConfig = new HashMap<>();
        relyingPartyConfig.put("rpId", TEST_RP_ID);
        relyingPartyConfig.put("rpName", TEST_RP_NAME);
        relyingPartyConfig.put("attestation", "none");
        relyingPartyConfig.put("userVerification", "preferred");

        when(session.users().getUserById(realm, TEST_USER_ID)).thenReturn(user);

        // Act
        Map<String, Object> options = registrationService.generateChallenge(TEST_USERNAME, TEST_RP_ID, TEST_REALM_NAME);
        // registrationService.generateRegistrationOptions(
        //     TEST_USER_ID,
        //     relyingPartyConfig
        // );

        // Assert
        assertThat(options).isNotNull();
        assertThat(options).containsKeys("challenge", "rp", "user", "pubKeyCredParams", "timeout");

        // Verify challenge is base64url encoded and has correct length (32 bytes = 43 chars base64url)
        String challenge = (String) options.get("challenge");
        assertThat(challenge).isNotNull();
        assertThat(challenge.length()).isGreaterThanOrEqualTo(43);

        // Verify relying party info
        @SuppressWarnings("unchecked")
        Map<String, String> rp = (Map<String, String>) options.get("rp");
        assertThat(rp.get("id")).isEqualTo(TEST_RP_ID);
        assertThat(rp.get("name")).isEqualTo(TEST_RP_NAME);

        // Verify user info
        @SuppressWarnings("unchecked")
        Map<String, String> userInfo = (Map<String, String>) options.get("user");
        assertThat(userInfo.get("id")).isNotNull();
        assertThat(userInfo.get("name")).isEqualTo(TEST_USERNAME);
        assertThat(userInfo.get("displayName")).isEqualTo(TEST_USERNAME);

        // Verify public key credential parameters
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pubKeyCredParams = (List<Map<String, Object>>) options.get("pubKeyCredParams");
        assertThat(pubKeyCredParams).isNotEmpty();
        assertThat(pubKeyCredParams).anyMatch(param ->
            "public-key".equals(param.get("type")) &&
            Integer.valueOf(-7).equals(param.get("alg")) // ES256
        );

        // Verify timeout
        assertThat(options.get("timeout")).isEqualTo(60000); // 60 seconds

        // Verify attestation conveyance
        assertThat(options.get("attestation")).isEqualTo("none");

        // Verify user verification
        assertThat(options.get("userVerification")).isEqualTo("preferred");
    }

    @Test
    @DisplayName("Should throw exception when user not found")
    void testGenerateChallenge_UserNotFound() {
        // Arrange
        Map<String, String> relyingPartyConfig = new HashMap<>();
        relyingPartyConfig.put("rpId", TEST_RP_ID);
        relyingPartyConfig.put("rpName", TEST_RP_NAME);

        when(session.users().getUserById(realm, TEST_USER_ID)).thenReturn(null);

        // Act & Assert
        assertThatThrownBy(() ->
            registrationService.generateChallenge(TEST_USERNAME, TEST_RP_ID, TEST_REALM_NAME)
        )
        .isInstanceOf(WebAuthnException.class)
        .hasMessageContaining("User not found");
        // Deprecated:
        // assertThatThrownBy(() ->
        //     registrationService.generateRegistrationOptions(TEST_USER_ID, relyingPartyConfig)
        // )
        // .isInstanceOf(WebAuthnException.class)
        // .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("Should verify and store credential successfully for passwordless")
    void testVerifyAndStoreCredential_Success_Passwordless() {
        // Arrange
        String challenge = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("test-challenge-12345678901234567890".getBytes(StandardCharsets.UTF_8));
        String attestationObject = createMockAttestationObject();
        String clientDataJSON = createMockClientDataJSON(challenge);

        Map<String, String> registrationData = new HashMap<>();
        registrationData.put("attestationObject", attestationObject);
        registrationData.put("clientDataJSON", clientDataJSON);
        registrationData.put("credentialType", "passwordless");

        when(session.users().getUserById(realm, TEST_USER_ID)).thenReturn(user);
        when(user.credentialManager()).thenReturn(credentialManager);

        // Mock challenge storage (in real implementation would use auth session or cache)
        when(authSession.getAuthNote("webauthn_challenge")).thenReturn(challenge);
        when(authSession.getAuthNote("webauthn_challenge_timestamp"))
            .thenReturn(String.valueOf(System.currentTimeMillis()));

        // Act
        Map<String, Object> result = registrationService.verifyAndStoreCredential(
            TEST_USER_ID,
            registrationData,
            challenge
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.get("verified")).isEqualTo(true);
        assertThat(result.get("credentialId")).isNotNull();
        assertThat(result.get("credentialType")).isEqualTo("passwordless");

        // Verify credential was stored
        verify(credentialManager, times(1)).createStoredCredential(any(CredentialModel.class));
    }

    @Test
    @DisplayName("Should verify and store credential successfully for two-factor")
    void testVerifyAndStoreCredential_Success_TwoFactor() {
        // Arrange
        String challenge = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("test-challenge-2fa-12345678901234567".getBytes(StandardCharsets.UTF_8));
        String attestationObject = createMockAttestationObject();
        String clientDataJSON = createMockClientDataJSON(challenge);

        Map<String, String> registrationData = new HashMap<>();
        registrationData.put("attestationObject", attestationObject);
        registrationData.put("clientDataJSON", clientDataJSON);
        registrationData.put("credentialType", "twofactor");

        when(session.users().getUserById(realm, TEST_USER_ID)).thenReturn(user);
        when(user.credentialManager()).thenReturn(credentialManager);

        // Mock existing password credential to qualify for 2FA
        CredentialModel passwordCred = mock(CredentialModel.class);
        when(passwordCred.getType()).thenReturn("password");
        when(credentialManager.getStoredCredentialsStream()).thenReturn(
            java.util.stream.Stream.of(passwordCred)
        );

        // Act
        Map<String, Object> result = registrationService.verifyAndStoreCredential(
            TEST_USER_ID,
            registrationData,
            challenge
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.get("verified")).isEqualTo(true);
        assertThat(result.get("credentialType")).isEqualTo("twofactor");

        verify(credentialManager, times(1)).createStoredCredential(any(CredentialModel.class));
    }

    @Test
    @DisplayName("Should throw exception for expired challenge")
    void testVerifyAndStoreCredential_ExpiredChallenge() {
        // Arrange
        String challenge = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("expired-challenge-1234567890123456".getBytes(StandardCharsets.UTF_8));
        String attestationObject = createMockAttestationObject();
        String clientDataJSON = createMockClientDataJSON(challenge);

        Map<String, String> registrationData = new HashMap<>();
        registrationData.put("attestationObject", attestationObject);
        registrationData.put("clientDataJSON", clientDataJSON);
        registrationData.put("credentialType", "passwordless");

        when(session.users().getUserById(realm, TEST_USER_ID)).thenReturn(user);

        // Mock expired challenge (5 minutes ago)
        long expiredTimestamp = System.currentTimeMillis() - (5 * 60 * 1000);
        when(authSession.getAuthNote("webauthn_challenge")).thenReturn(challenge);
        when(authSession.getAuthNote("webauthn_challenge_timestamp"))
            .thenReturn(String.valueOf(expiredTimestamp));

        // Act & Assert
        assertThatThrownBy(() ->
            registrationService.verifyAndStoreCredential(TEST_USER_ID, registrationData, challenge)
        )
        .isInstanceOf(ChallengeExpiredException.class)
        .hasMessageContaining("Challenge has expired");
    }

    @Test
    @DisplayName("Should throw exception for invalid signature")
    void testVerifyAndStoreCredential_InvalidSignature() {
        // Arrange
        String challenge = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("valid-challenge-123456789012345678".getBytes(StandardCharsets.UTF_8));
        String invalidAttestationObject = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("invalid-attestation-data".getBytes(StandardCharsets.UTF_8));
        String clientDataJSON = createMockClientDataJSON(challenge);

        Map<String, String> registrationData = new HashMap<>();
        registrationData.put("attestationObject", invalidAttestationObject);
        registrationData.put("clientDataJSON", clientDataJSON);
        registrationData.put("credentialType", "passwordless");

        when(session.users().getUserById(realm, TEST_USER_ID)).thenReturn(user);
        when(authSession.getAuthNote("webauthn_challenge")).thenReturn(challenge);
        when(authSession.getAuthNote("webauthn_challenge_timestamp"))
            .thenReturn(String.valueOf(System.currentTimeMillis()));

        // Act & Assert
        assertThatThrownBy(() ->
            registrationService.verifyAndStoreCredential(TEST_USER_ID, registrationData, challenge)
        )
        .isInstanceOf(WebAuthnException.class)
        .hasMessageContaining("verification failed");
    }

    @Test
    @DisplayName("Should classify credential type as passwordless when no password exists")
    void testClassifyCredentialType_Passwordless() {
        // Arrange
        when(session.users().getUserById(realm, TEST_USER_ID)).thenReturn(user);
        when(user.credentialManager()).thenReturn(credentialManager);
        when(credentialManager.getStoredCredentialsStream()).thenReturn(java.util.stream.Stream.empty());

        // Act
        String credentialType = registrationService.classifyCredentialType(TEST_USER_ID, "auto");

        // Assert
        assertThat(credentialType).isEqualTo("passwordless");
    }

    @Test
    @DisplayName("Should classify credential type as two-factor when password exists")
    void testClassifyCredentialType_TwoFactor() {
        // Arrange
        CredentialModel passwordCred = mock(CredentialModel.class);
        when(passwordCred.getType()).thenReturn("password");

        when(session.users().getUserById(realm, TEST_USER_ID)).thenReturn(user);
        when(user.credentialManager()).thenReturn(credentialManager);
        when(credentialManager.getStoredCredentialsStream()).thenReturn(
            java.util.stream.Stream.of(passwordCred)
        );

        // Act
        String credentialType = registrationService.classifyCredentialType(TEST_USER_ID, "auto");

        // Assert
        assertThat(credentialType).isEqualTo("twofactor");
    }

    @Test
    @DisplayName("Should use default type when explicitly specified")
    void testClassifyCredentialType_DefaultType() {
        // Arrange
        when(session.users().getUserById(realm, TEST_USER_ID)).thenReturn(user);

        // Act
        String credentialType = registrationService.classifyCredentialType(TEST_USER_ID, "passwordless");

        // Assert
        assertThat(credentialType).isEqualTo("passwordless");

        // Verify no credential check was performed
        verify(user, never()).credentialManager();
    }

    @ParameterizedTest
    @ValueSource(strings = {"required", "preferred", "discouraged"})
    @DisplayName("Should support different user verification requirements")
    void testGenerateChallenge_UserVerificationOptions(String userVerification) {
        // Arrange
        Map<String, String> relyingPartyConfig = new HashMap<>();
        relyingPartyConfig.put("rpId", TEST_RP_ID);
        relyingPartyConfig.put("rpName", TEST_RP_NAME);
        relyingPartyConfig.put("userVerification", userVerification);

        when(session.users().getUserById(realm, TEST_USER_ID)).thenReturn(user);

        // Act
        Map<String, Object> options = registrationService.generateRegistrationOptions(
            TEST_USER_ID,
            relyingPartyConfig
        );

        // Assert
        assertThat(options.get("userVerification")).isEqualTo(userVerification);
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "indirect", "direct", "enterprise"})
    @DisplayName("Should support different attestation conveyance preferences")
    void testGenerateChallenge_AttestationOptions(String attestation) {
        // Arrange
        Map<String, String> relyingPartyConfig = new HashMap<>();
        relyingPartyConfig.put("rpId", TEST_RP_ID);
        relyingPartyConfig.put("rpName", TEST_RP_NAME);
        relyingPartyConfig.put("attestation", attestation);

        when(session.users().getUserById(realm, TEST_USER_ID)).thenReturn(user);

        // Act
        Map<String, Object> options = registrationService.generateRegistrationOptions(
            TEST_USER_ID,
            relyingPartyConfig
        );

        // Assert
        assertThat(options.get("attestation")).isEqualTo(attestation);
    }

    // Helper methods for creating mock WebAuthn data

    private String createMockAttestationObject() {
        // In real implementation, this would be a valid CBOR-encoded attestation object
        // For TDD, we use a mock that will be validated against actual WebAuthn4J verification
        byte[] mockData = "mock-attestation-object-with-auth-data-and-statement".getBytes(StandardCharsets.UTF_8);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mockData);
    }

    private String createMockClientDataJSON(String challenge) {
        // Create a mock client data JSON structure
        String clientData = String.format(
            "{\"type\":\"webauthn.create\",\"challenge\":\"%s\",\"origin\":\"https://%s\"}",
            challenge,
            TEST_RP_ID
        );
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            clientData.getBytes(StandardCharsets.UTF_8)
        );
    }
}
