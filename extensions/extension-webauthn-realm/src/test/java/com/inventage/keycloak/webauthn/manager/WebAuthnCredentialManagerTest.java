package com.inventage.keycloak.webauthn.manager;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.inventage.keycloak.webauthn.infrastructure.exception.WebAuthnException;
import com.inventage.keycloak.webauthn.infrastructure.service.WebAuthnCredentialManager;
import io.inventage.keycloak.custom.webauthn.infrastructure.model.WebAuthnCredential;

/**
 * TDD Unit Tests for WebAuthn Credential Manager
 *
 * Test Coverage:
 * - Credential storage and retrieval
 * - Challenge generation and validation
 * - Credential deletion
 * - Credential updates
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebAuthn Credential Manager Tests")
class WebAuthnCredentialManagerTest {

    @Mock
    private KeycloakSession session;

    @Mock
    private RealmModel realm;

    @Mock
    private UserModel user;

    private WebAuthnCredentialManager webAuthnCredentialManager;

    private static final String TEST_USER_ID = "cred-user-123";
    private static final String TEST_CREDENTIAL_ID = "webauthn-cred-456";
    private static final String TEST_CREDENTIAL_TYPE = "webauthn";

    @BeforeEach
    void setUp() {
        webAuthnCredentialManager = new WebAuthnCredentialManager(session);

        // Setup common mocks - LENIENT MODE for optional mocks
        lenient().when(session.getContext()).thenReturn(mock(KeycloakContext.class));
        lenient().when(session.getContext().getRealm()).thenReturn(realm);
        lenient().when(session.users()).thenReturn(mock(UserProvider.class));
        lenient().when(user.getId()).thenReturn(TEST_USER_ID);
    }

    @Test
    @DisplayName("Should store WebAuthn credential successfully")
    void testStoreCredential_Success() {
        // Arrange
        WebAuthnCredential credential = new WebAuthnCredential();
        credential.setCredentialId(TEST_CREDENTIAL_ID);
        credential.setCredentialPublicKey("mock-public-key-base64");
        credential.setSignCount(0);
        credential.setCredentialType("passwordless");

        WebAuthnCredential.CredentialMetadata metadata = new WebAuthnCredential.CredentialMetadata();
        metadata.setName("Yubikey 5");
        credential.setMetadata(metadata);

        // Mock user attributes and getUserById
        when(user.getAttributes()).thenReturn(new HashMap<>());
        when(session.users().getUserById(realm, TEST_USER_ID)).thenReturn(user);

        // Act & Assert
        assertThatNoException().isThrownBy(() ->
            webAuthnCredentialManager.storeCredential(TEST_USER_ID, credential)
        );

        verify(user, times(1)).setSingleAttribute(any(String.class), any(String.class));
    }

    @Test
    @DisplayName("Should throw exception when storing credential for non-existent user")
    void testStoreCredential_UserNotFound() {
        // Arrange
        WebAuthnCredential credential = new WebAuthnCredential();
        credential.setCredentialId(TEST_CREDENTIAL_ID);

        when(session.users().getUserById(realm, TEST_USER_ID)).thenReturn(null);

        // Act & Assert
        assertThatThrownBy(() ->
            webAuthnCredentialManager.storeCredential(TEST_USER_ID, credential)
        )
        .isInstanceOf(WebAuthnException.class)
        .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("Should throw exception when storing duplicate credential ID")
    void testStoreCredential_DuplicateId() {
        // Arrange
        WebAuthnCredential credential = new WebAuthnCredential();
        credential.setCredentialId(TEST_CREDENTIAL_ID);
        credential.setCredentialPublicKey("mock-public-key");

        // Mock existing credentials with same ID
        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put("webauthn.credentials", List.of(
            "[{\"credentialId\":\"" + TEST_CREDENTIAL_ID + "\",\"credentialPublicKey\":\"test\"}]"
        ));
        when(user.getAttributes()).thenReturn(attrs);
        when(session.users().getUserById(realm, TEST_USER_ID)).thenReturn(user);

        // Act & Assert
        assertThatThrownBy(() ->
            webAuthnCredentialManager.storeCredential(TEST_USER_ID, credential)
        )
        .isInstanceOf(WebAuthnException.class)
        .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Should delete credential for a user")
    void testDeleteCredential_Success() {
        // Arrange
        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put("webauthn.credentials", List.of(
            "[{\"credentialId\":\"" + TEST_CREDENTIAL_ID + "\",\"credentialPublicKey\":\"test\"}]"
        ));
        when(user.getAttributes()).thenReturn(attrs);
        when(session.users().getUserById(realm, TEST_USER_ID)).thenReturn(user);

        // Act & Assert
        assertThatNoException().isThrownBy(() ->
            webAuthnCredentialManager.deleteCredential(TEST_USER_ID, TEST_CREDENTIAL_ID)
        );

        // When credentials list becomes empty after deletion, removeAttribute is called
        verify(user, times(1)).removeAttribute("webauthn.credentials");
    }

    @Test
    @DisplayName("Should throw exception when deleting non-existent credential")
    void testDeleteCredential_NotFound() {
        // Arrange
        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put("webauthn.credentials", List.of("[]"));
        when(user.getAttributes()).thenReturn(attrs);
        when(session.users().getUserById(realm, TEST_USER_ID)).thenReturn(user);

        // Act & Assert
        assertThatThrownBy(() ->
            webAuthnCredentialManager.deleteCredential(TEST_USER_ID, TEST_CREDENTIAL_ID)
        )
        .isInstanceOf(WebAuthnException.class)
        .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("Should store and retrieve challenge with TTL")
    void testStoreChallenge_Success() {
        // Arrange
        String challenge = "test-challenge-12345678901234567890";

        // Act
        String sessionId = webAuthnCredentialManager.storeChallenge(TEST_USER_ID, challenge, "registration");

        // Assert
        assertThat(sessionId).isNotNull();
        assertThat(sessionId).isNotEmpty();

        WebAuthnCredentialManager.ChallengeData retrievedChallenge = webAuthnCredentialManager.getChallenge(sessionId);
        assertThat(retrievedChallenge).isNotNull();
        assertThat(retrievedChallenge.getChallenge()).isEqualTo(challenge);
        assertThat(retrievedChallenge.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(retrievedChallenge.getType()).isEqualTo("registration");
    }

    @Test
    @DisplayName("Should return null for non-existent challenge")
    void testGetChallenge_NotFound() {
        // Act
        WebAuthnCredentialManager.ChallengeData result = webAuthnCredentialManager.getChallenge("non-existent");

        // Assert
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should delete challenge")
    void testDeleteChallenge_Success() {
        // Arrange
        String challenge = "test-challenge-delete";
        String sessionId = webAuthnCredentialManager.storeChallenge(TEST_USER_ID, challenge, "registration");

        // Verify it exists
        assertThat(webAuthnCredentialManager.getChallenge(sessionId)).isNotNull();

        // Act
        webAuthnCredentialManager.deleteChallenge(sessionId);

        // Assert
        assertThat(webAuthnCredentialManager.getChallenge(sessionId)).isNull();
    }

    @Test
    @DisplayName("Should update credential successfully")
    void testUpdateCredential_Success() {
        // Arrange
        WebAuthnCredential credential = new WebAuthnCredential();
        credential.setCredentialId(TEST_CREDENTIAL_ID);
        credential.setCredentialPublicKey("updated-public-key");
        credential.setSignCount(50);

        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put("webauthn.credentials", List.of(
            "[{\"credentialId\":\"" + TEST_CREDENTIAL_ID + "\",\"credentialPublicKey\":\"old-key\",\"signCount\":0}]"
        ));
        when(user.getAttributes()).thenReturn(attrs);
        when(session.users().getUserById(realm, TEST_USER_ID)).thenReturn(user);

        // Act & Assert
        assertThatNoException().isThrownBy(() ->
            webAuthnCredentialManager.updateCredential(TEST_USER_ID, credential)
        );

        verify(user, times(1)).setSingleAttribute(any(String.class), any(String.class));
    }
}
