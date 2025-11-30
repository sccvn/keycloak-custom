package com.inventage.keycloak.webauthn.manager;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.inventage.keycloak.webauthn.infrastructure.exception.WebAuthnException;
import com.inventage.keycloak.webauthn.infrastructure.service.WebAuthnCredentialManager;

/**
 * TDD Unit Tests for WebAuthn Credential Manager
 *
 * Test Coverage:
 * - Credential creation and storage
 * - Credential retrieval by user and ID
 * - Credential updates (signature counter)
 * - Credential deletion
 * - Multiple credentials per user
 * - Credential metadata management
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

    @Mock
    private UserCredentialManager credentialManager;

    private WebAuthnCredentialManager webAuthnCredentialManager;

    private static final String TEST_USER_ID = "cred-user-123";
    private static final String TEST_CREDENTIAL_ID = "webauthn-cred-456";
    private static final String TEST_CREDENTIAL_TYPE = "webauthn";

    @BeforeEach
    void setUp() {
        // Initialize manager (will be implemented)
        webAuthnCredentialManager = new WebAuthnCredentialManager(session);

        // Setup common mocks
        when(session.getContext()).thenReturn(mock(KeycloakContext.class));
        when(session.getContext().getRealm()).thenReturn(realm);
        when(session.users()).thenReturn(mock(UserProvider.class));
        when(session.users().getUserById(realm, TEST_USER_ID)).thenReturn(user);
        when(user.getId()).thenReturn(TEST_USER_ID);
        when(user.credentialManager()).thenReturn(credentialManager);
    }

    @Test
    @DisplayName("Should create and store WebAuthn credential")
    void testCreateCredential_Success() {
        // Arrange
        Map<String, Object> credentialData = new HashMap<>();
        credentialData.put("credentialId", TEST_CREDENTIAL_ID);
        credentialData.put("publicKey", "mock-public-key-base64");
        credentialData.put("signatureCount", 0);
        credentialData.put("aaguid", "00000000-0000-0000-0000-000000000000");
        credentialData.put("credentialType", "passwordless");

        Map<String, String> metadata = new HashMap<>();
        metadata.put("userLabel", "Yubikey 5");
        metadata.put("transports", "usb,nfc");

        ArgumentCaptor<CredentialModel> credentialCaptor = ArgumentCaptor.forClass(CredentialModel.class);
        when(credentialManager.createStoredCredential(credentialCaptor.capture())).thenReturn(true);

        // Act
        boolean result = webAuthnCredentialManager.createCredential(
            TEST_USER_ID,
            credentialData,
            metadata
        );

        // Assert
        assertThat(result).isTrue();

        CredentialModel captured = credentialCaptor.getValue();
        assertThat(captured.getType()).isEqualTo(TEST_CREDENTIAL_TYPE);
        assertThat(captured.getUserLabel()).isEqualTo("Yubikey 5");
        assertThat(captured.getCredentialData()).contains("publicKey");
        assertThat(captured.getCredentialData()).contains("signatureCount");

        verify(credentialManager, times(1)).createStoredCredential(any(CredentialModel.class));
    }

    @Test
    @DisplayName("Should retrieve credential by ID")
    void testGetCredentialById_Found() {
        // Arrange
        CredentialModel mockCredential = createMockCredential(TEST_CREDENTIAL_ID, "Test Device");

        when(credentialManager.getStoredCredentialById(TEST_CREDENTIAL_ID)).thenReturn(mockCredential);

        // Act
        Optional<CredentialModel> result = webAuthnCredentialManager.getCredentialById(
            TEST_USER_ID,
            TEST_CREDENTIAL_ID
        );

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(TEST_CREDENTIAL_ID);
        assertThat(result.get().getUserLabel()).isEqualTo("Test Device");

        verify(credentialManager, times(1)).getStoredCredentialById(TEST_CREDENTIAL_ID);
    }

    @Test
    @DisplayName("Should return empty when credential not found")
    void testGetCredentialById_NotFound() {
        // Arrange
        when(credentialManager.getStoredCredentialById(TEST_CREDENTIAL_ID)).thenReturn(null);

        // Act
        Optional<CredentialModel> result = webAuthnCredentialManager.getCredentialById(
            TEST_USER_ID,
            TEST_CREDENTIAL_ID
        );

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should retrieve all credentials for user")
    void testGetAllCredentials_MultipleCredentials() {
        // Arrange
        CredentialModel cred1 = createMockCredential("cred-001", "Yubikey 5");
        CredentialModel cred2 = createMockCredential("cred-002", "TouchID");
        CredentialModel cred3 = createMockCredential("cred-003", "Windows Hello");
        CredentialModel passwordCred = mock(CredentialModel.class);
        when(passwordCred.getType()).thenReturn("password");

        when(credentialManager.getStoredCredentialsStream())
            .thenReturn(Stream.of(cred1, passwordCred, cred2, cred3));

        // Act
        List<CredentialModel> result = webAuthnCredentialManager.getAllCredentials(TEST_USER_ID);

        // Assert
        assertThat(result).hasSize(3);
        assertThat(result).extracting(CredentialModel::getId)
            .containsExactlyInAnyOrder("cred-001", "cred-002", "cred-003");
        assertThat(result).allMatch(cred -> TEST_CREDENTIAL_TYPE.equals(cred.getType()));
    }

    @Test
    @DisplayName("Should update credential signature counter")
    void testUpdateSignatureCounter_Success() {
        // Arrange
        CredentialModel existingCredential = createMockCredential(TEST_CREDENTIAL_ID, "Test Device");
        existingCredential.setCredentialData("{\"publicKey\":\"test-key\",\"signatureCount\":50}");

        when(credentialManager.getStoredCredentialById(TEST_CREDENTIAL_ID)).thenReturn(existingCredential);

        ArgumentCaptor<CredentialModel> credentialCaptor = ArgumentCaptor.forClass(CredentialModel.class);
        when(credentialManager.updateStoredCredential(credentialCaptor.capture())).thenReturn(true);

        // Act
        boolean result = webAuthnCredentialManager.updateSignatureCounter(
            TEST_USER_ID,
            TEST_CREDENTIAL_ID,
            100
        );

        // Assert
        assertThat(result).isTrue();

        CredentialModel captured = credentialCaptor.getValue();
        assertThat(captured.getCredentialData()).contains("\"signatureCount\":100");

        verify(credentialManager, times(1)).updateStoredCredential(any(CredentialModel.class));
    }

    @Test
    @DisplayName("Should delete credential by ID")
    void testDeleteCredential_Success() {
        // Arrange
        when(credentialManager.removeStoredCredentialById(TEST_CREDENTIAL_ID)).thenReturn(true);

        // Act
        boolean result = webAuthnCredentialManager.deleteCredential(
            TEST_USER_ID,
            TEST_CREDENTIAL_ID
        );

        // Assert
        assertThat(result).isTrue();
        verify(credentialManager, times(1)).removeStoredCredentialById(TEST_CREDENTIAL_ID);
    }

    @Test
    @DisplayName("Should delete all WebAuthn credentials for user")
    void testDeleteAllCredentials_Success() {
        // Arrange
        CredentialModel cred1 = createMockCredential("cred-001", "Device 1");
        CredentialModel cred2 = createMockCredential("cred-002", "Device 2");

        when(credentialManager.getStoredCredentialsStream())
            .thenReturn(Stream.of(cred1, cred2));
        when(credentialManager.removeStoredCredentialById(anyString())).thenReturn(true);

        // Act
        int deletedCount = webAuthnCredentialManager.deleteAllCredentials(TEST_USER_ID);

        // Assert
        assertThat(deletedCount).isEqualTo(2);
        verify(credentialManager, times(1)).removeStoredCredentialById("cred-001");
        verify(credentialManager, times(1)).removeStoredCredentialById("cred-002");
    }

    @Test
    @DisplayName("Should update credential metadata")
    void testUpdateMetadata_Success() {
        // Arrange
        CredentialModel existingCredential = createMockCredential(TEST_CREDENTIAL_ID, "Old Label");

        when(credentialManager.getStoredCredentialById(TEST_CREDENTIAL_ID)).thenReturn(existingCredential);

        Map<String, String> newMetadata = new HashMap<>();
        newMetadata.put("userLabel", "New Label");

        ArgumentCaptor<CredentialModel> credentialCaptor = ArgumentCaptor.forClass(CredentialModel.class);
        when(credentialManager.updateStoredCredential(credentialCaptor.capture())).thenReturn(true);

        // Act
        boolean result = webAuthnCredentialManager.updateMetadata(
            TEST_USER_ID,
            TEST_CREDENTIAL_ID,
            newMetadata
        );

        // Assert
        assertThat(result).isTrue();

        CredentialModel captured = credentialCaptor.getValue();
        assertThat(captured.getUserLabel()).isEqualTo("New Label");

        verify(credentialManager, times(1)).updateStoredCredential(any(CredentialModel.class));
    }

    @Test
    @DisplayName("Should count WebAuthn credentials for user")
    void testCountCredentials() {
        // Arrange
        CredentialModel cred1 = createMockCredential("cred-001", "Device 1");
        CredentialModel cred2 = createMockCredential("cred-002", "Device 2");
        CredentialModel cred3 = createMockCredential("cred-003", "Device 3");
        CredentialModel passwordCred = mock(CredentialModel.class);
        when(passwordCred.getType()).thenReturn("password");

        when(credentialManager.getStoredCredentialsStream())
            .thenReturn(Stream.of(cred1, passwordCred, cred2, cred3));

        // Act
        long count = webAuthnCredentialManager.countCredentials(TEST_USER_ID);

        // Assert
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("Should check if user has WebAuthn credentials")
    void testHasCredentials() {
        // Arrange
        CredentialModel cred1 = createMockCredential("cred-001", "Device 1");

        when(credentialManager.getStoredCredentialsStream())
            .thenReturn(Stream.of(cred1));

        // Act
        boolean hasCredentials = webAuthnCredentialManager.hasCredentials(TEST_USER_ID);

        // Assert
        assertThat(hasCredentials).isTrue();
    }

    @Test
    @DisplayName("Should return false when user has no WebAuthn credentials")
    void testHasCredentials_None() {
        // Arrange
        CredentialModel passwordCred = mock(CredentialModel.class);
        when(passwordCred.getType()).thenReturn("password");

        when(credentialManager.getStoredCredentialsStream())
            .thenReturn(Stream.of(passwordCred));

        // Act
        boolean hasCredentials = webAuthnCredentialManager.hasCredentials(TEST_USER_ID);

        // Assert
        assertThat(hasCredentials).isFalse();
    }

    @Test
    @DisplayName("Should throw exception when creating duplicate credential ID")
    void testCreateCredential_DuplicateId() {
        // Arrange
        CredentialModel existingCredential = createMockCredential(TEST_CREDENTIAL_ID, "Existing");

        when(credentialManager.getStoredCredentialById(TEST_CREDENTIAL_ID)).thenReturn(existingCredential);

        Map<String, Object> credentialData = new HashMap<>();
        credentialData.put("credentialId", TEST_CREDENTIAL_ID);
        credentialData.put("publicKey", "mock-public-key");

        Map<String, String> metadata = new HashMap<>();
        metadata.put("userLabel", "Duplicate");

        // Act & Assert
        assertThatThrownBy(() ->
            webAuthnCredentialManager.createCredential(TEST_USER_ID, credentialData, metadata)
        )
        .isInstanceOf(WebAuthnException.class)
        .hasMessageContaining("Credential already exists");
    }

    @Test
    @DisplayName("Should retrieve credentials sorted by creation date")
    void testGetAllCredentials_SortedByDate() {
        // Arrange
        CredentialModel cred1 = createMockCredential("cred-001", "Device 1");
        when(cred1.getCreatedDate()).thenReturn(1000L);

        CredentialModel cred2 = createMockCredential("cred-002", "Device 2");
        when(cred2.getCreatedDate()).thenReturn(3000L);

        CredentialModel cred3 = createMockCredential("cred-003", "Device 3");
        when(cred3.getCreatedDate()).thenReturn(2000L);

        when(credentialManager.getStoredCredentialsStream())
            .thenReturn(Stream.of(cred1, cred2, cred3));

        // Act
        List<CredentialModel> result = webAuthnCredentialManager.getAllCredentials(TEST_USER_ID);

        // Assert
        assertThat(result).hasSize(3);
        assertThat(result).extracting(CredentialModel::getId)
            .containsExactly("cred-002", "cred-003", "cred-001"); // Newest first
    }

    // Helper methods

    private CredentialModel createMockCredential(String credentialId, String userLabel) {
        CredentialModel credential = mock(CredentialModel.class);
        when(credential.getId()).thenReturn(credentialId);
        when(credential.getType()).thenReturn(TEST_CREDENTIAL_TYPE);
        when(credential.getUserLabel()).thenReturn(userLabel);
        when(credential.getCreatedDate()).thenReturn(System.currentTimeMillis());
        when(credential.getCredentialData()).thenReturn(
            "{\"publicKey\":\"test-key\",\"signatureCount\":0}"
        );
        return credential;
    }
}
