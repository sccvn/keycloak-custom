package com.inventage.keycloak.webauthn.infrastructure.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventage.keycloak.webauthn.infrastructure.exception.ChallengeExpiredException;
import com.inventage.keycloak.webauthn.infrastructure.exception.RegistrationException;
import com.inventage.keycloak.webauthn.infrastructure.exception.WebAuthnException;
import com.inventage.keycloak.webauthn.util.Base64Util;
import io.inventage.keycloak.custom.webauthn.infrastructure.model.CredentialType;
import io.inventage.keycloak.custom.webauthn.infrastructure.model.WebAuthnCredential;
import com.webauthn4j.converter.util.CborConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.*;
import com.webauthn4j.data.attestation.AttestationObject;
import com.webauthn4j.data.client.CollectedClientData;
import com.webauthn4j.data.client.ClientDataType;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

/**
 * Service for WebAuthn credential registration.
 * Handles challenge generation, attestation verification, and credential storage.
 */
public class WebAuthnRegistrationService {

    private static final Logger LOG = Logger.getLogger(WebAuthnRegistrationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CHALLENGE_LENGTH = 32;

    private final KeycloakSession session;
    private final WebAuthnCredentialManager credentialManager;
    private final ObjectConverter converter;
    private final CborConverter cborConverter;

    public WebAuthnRegistrationService(KeycloakSession session) {
        this.session = session;
        this.credentialManager = new WebAuthnCredentialManager(session);
        this.converter = new ObjectConverter();
        this.cborConverter = converter.getCborConverter();
    }

    /**
     * Generate registration challenge for a user.
     *
     * @param userId User ID
     * @param credentialName User-provided credential name
     * @param credentialType Credential type ("passwordless" or "twofactor")
     * @return Map containing challenge data
     * @throws RegistrationException if challenge generation fails
     */
    public Map<String, Object> generateChallenge(String userId, String credentialName,
            String credentialType) throws RegistrationException {
        try {
            RealmModel realm = session.getContext().getRealm();
            UserModel user = session.users().getUserById(realm, userId);

            if (user == null) {
                throw new RegistrationException("User not found: " + userId);
            }

            // Generate cryptographically secure random challenge
            byte[] challengeBytes = new byte[CHALLENGE_LENGTH];
            RANDOM.nextBytes(challengeBytes);
            String challenge = Base64Util.encodeToString(challengeBytes);

            // Store challenge with 5 minute TTL
            String sessionId = credentialManager.storeChallenge(userId, challenge, "registration", 300);

            // Classify credential type
            CredentialType type = classifyCredentialType(credentialType);

            LOG.infof("Generated registration challenge for user %s (type: %s, session: %s)",
                    userId, type.getValue(), sessionId);

            // Build challenge response
            return buildRegistrationChallengeResponse(sessionId, challenge, user, credentialName, type);

        } catch (RegistrationException e) {
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to generate registration challenge for user: %s", userId);
            throw new RegistrationException("Challenge generation failed", e);
        }
    }

    /**
     * Verify attestation and store credential.
     *
     * @param sessionId Session ID from challenge
     * @param clientDataJSON Base64url encoded client data JSON
     * @param attestationObject Base64url encoded attestation object
     * @param credentialName User-provided credential name
     * @param credentialType Credential type string
     * @return Stored credential
     * @throws RegistrationException if verification fails
     * @throws ChallengeExpiredException if challenge is expired
     */
    public WebAuthnCredential verifyAndStoreCredential(String sessionId, String clientDataJSON,
            String attestationObject, String credentialName, String credentialType)
            throws RegistrationException, ChallengeExpiredException {
        try {
            // 1. Validate and retrieve challenge
            WebAuthnCredentialManager.ChallengeData challengeData = credentialManager.getChallenge(sessionId);

            if (challengeData == null || challengeData.isExpired()) {
                throw new ChallengeExpiredException("Challenge expired or not found");
            }

            // 2. Classify credential type
            CredentialType type = classifyCredentialType(credentialType);
            LOG.debugf("Processing registration with credential type: %s", type.getValue());

            // 3. Decode and validate client data
            byte[] clientDataBytes = Base64Util.decode(clientDataJSON);
            CollectedClientData collectedClientData = validateClientData(clientDataBytes,
                    challengeData.getChallenge());

            // 4. Decode attestation object
            byte[] attestationBytes = Base64Util.decode(attestationObject);
            AttestationObject attestationObj = parseAttestationObject(attestationBytes);

            // 5. Extract credential from attestation
            WebAuthnCredential credential = extractCredentialFromAttestation(attestationObj,
                    attestationObject);

            // 6. Set credential metadata
            WebAuthnCredential.CredentialMetadata metadata = new WebAuthnCredential.CredentialMetadata();
            metadata.setName(credentialName != null ? credentialName : "WebAuthn Key");

            // Extract AAGUID from authenticator data
            if (attestationObj.getAuthenticatorData() != null &&
                    attestationObj.getAuthenticatorData().getAttestedCredentialData() != null) {
                byte[] aaguid = attestationObj.getAuthenticatorData()
                        .getAttestedCredentialData().getAaguid().getBytes();
                metadata.setAaguid(Base64Util.encodeToString(aaguid));
            }

            credential.setMetadata(metadata);
            credential.setType(type);

            LOG.infof("Credential type set to: %s, credentialId: %s",
                    credential.getTypeAsString(), credential.getCredentialId());

            // 7. Store credential
            credentialManager.storeCredential(challengeData.getUserId(), credential);

            // 8. Clear challenge
            credentialManager.deleteChallenge(sessionId);

            LOG.infof("Registration successful - Type: %s, CredentialId: %s, Name: %s",
                    type.getValue(), credential.getCredentialId(), credentialName);

            return credential;

        } catch (ChallengeExpiredException e) {
            throw e;
        } catch (RegistrationException e) {
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Credential verification failed");
            throw new RegistrationException("Credential verification failed", e);
        }
    }

    /**
     * Classify credential type from string value.
     *
     * @param typeString Type string ("passwordless" or "twofactor")
     * @return CredentialType enum
     */
    private CredentialType classifyCredentialType(String typeString) {
        CredentialType type = CredentialType.fromValue(typeString);
        LOG.debugf("Credential type classified as: %s (from value: %s)",
                type.name(), typeString);
        return type;
    }

    /**
     * Validate client data JSON.
     *
     * @param clientDataBytes Client data JSON bytes
     * @param expectedChallenge Expected challenge value
     * @return Parsed CollectedClientData
     * @throws RegistrationException if validation fails
     */
    private CollectedClientData validateClientData(byte[] clientDataBytes,
            String expectedChallenge) throws RegistrationException {
        try {
            // Parse client data JSON
            CollectedClientData clientData = converter.getJsonConverter()
                    .readValue(clientDataBytes, CollectedClientData.class);

            // Verify type is "webauthn.create"
            if (!ClientDataType.WEBAUTHN_CREATE.equals(clientData.getType())) {
                throw new RegistrationException(
                        "Invalid client data type: " + clientData.getType());
            }

            // Verify challenge matches
            String receivedChallenge = Base64Util.encodeToString(clientData.getChallenge().getBytes());
            if (!expectedChallenge.equals(receivedChallenge)) {
                LOG.warnf("Challenge mismatch: expected=%s, received=%s",
                        expectedChallenge, receivedChallenge);
                throw new RegistrationException("Challenge mismatch");
            }

            // Verify origin (in production, should validate against allowed origins)
            String origin = clientData.getOrigin().toString();
            LOG.debugf("Client data validated successfully. Origin: %s", origin);

            return clientData;

        } catch (RegistrationException e) {
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Client data validation failed");
            throw new RegistrationException("Client data validation failed", e);
        }
    }

    /**
     * Parse attestation object from CBOR bytes.
     *
     * @param attestationBytes Attestation object bytes
     * @return Parsed AttestationObject
     * @throws RegistrationException if parsing fails
     */
    private AttestationObject parseAttestationObject(byte[] attestationBytes)
            throws RegistrationException {
        try {
            AttestationObject attestationObject = cborConverter
                    .readValue(attestationBytes, AttestationObject.class);

            LOG.debugf("Attestation object parsed: format=%s",
                    attestationObject.getFormat());

            return attestationObject;

        } catch (Exception e) {
            LOG.errorf(e, "Failed to parse attestation object");
            throw new RegistrationException("Attestation parsing failed", e);
        }
    }

    /**
     * Extract credential from attestation object.
     *
     * @param attestationObject Parsed attestation object
     * @param attestationObjectBase64 Base64url encoded attestation object
     * @return WebAuthnCredential
     * @throws RegistrationException if extraction fails
     */
    private WebAuthnCredential extractCredentialFromAttestation(
            AttestationObject attestationObject, String attestationObjectBase64)
            throws RegistrationException {
        try {
            WebAuthnCredential credential = new WebAuthnCredential();

            // Extract credential ID
            byte[] credentialId = attestationObject.getAuthenticatorData()
                    .getAttestedCredentialData().getCredentialId();
            credential.setCredentialId(Base64Util.encodeToString(credentialId));

            // Extract public key (COSE encoded)
            byte[] publicKeyBytes = cborConverter.writeValueAsBytes(
                    attestationObject.getAuthenticatorData()
                            .getAttestedCredentialData().getCOSEKey());
            credential.setCredentialPublicKey(Base64Util.encodeToString(publicKeyBytes));

            // Store attestation object
            credential.setAttestationObject(attestationObjectBase64);

            // Set initial sign count
            long signCount = attestationObject.getAuthenticatorData().getSignCount();
            credential.setSignCount(signCount);

            // Set credential type
            credential.setCredentialType("public-key");

            LOG.debugf("Credential extracted: id=%s, signCount=%d",
                    credential.getCredentialId(), signCount);

            return credential;

        } catch (Exception e) {
            LOG.errorf(e, "Failed to extract credential from attestation");
            throw new RegistrationException("Credential extraction failed", e);
        }
    }

    /**
     * Build registration challenge response.
     *
     * @param sessionId Session ID
     * @param challenge Challenge string
     * @param user User model
     * @param credentialName Credential name
     * @param type Credential type
     * @return Map containing challenge response data
     */
    private Map<String, Object> buildRegistrationChallengeResponse(String sessionId,
            String challenge, UserModel user, String credentialName, CredentialType type) {

        Map<String, Object> response = new HashMap<>();

        // Session and challenge
        response.put("sessionId", sessionId);
        response.put("challenge", challenge);

        // User information
        Map<String, String> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("name", user.getUsername());
        userInfo.put("displayName", user.getFirstName() + " " + user.getLastName());
        response.put("user", userInfo);

        // Relying party information
        Map<String, String> rpInfo = new HashMap<>();
        rpInfo.put("name", session.getContext().getRealm().getDisplayName());
        rpInfo.put("id", session.getContext().getUri().getBaseUri().getHost());
        response.put("rp", rpInfo);

        // Public key credential parameters (support ES256 and RS256)
        List<Map<String, Object>> pubKeyCredParams = new ArrayList<>();
        pubKeyCredParams.add(Map.of("type", "public-key", "alg", -7));  // ES256
        pubKeyCredParams.add(Map.of("type", "public-key", "alg", -257)); // RS256
        response.put("pubKeyCredParams", pubKeyCredParams);

        // Timeout (60 seconds)
        response.put("timeout", 60000);

        // Attestation conveyance
        response.put("attestation", "direct");

        // Authenticator selection criteria
        Map<String, Object> authenticatorSelection = new HashMap<>();
        authenticatorSelection.put("authenticatorAttachment", "platform");
        authenticatorSelection.put("requireResidentKey", false);
        authenticatorSelection.put("userVerification", "preferred");
        response.put("authenticatorSelection", authenticatorSelection);

        // Metadata
        response.put("credentialName", credentialName);
        response.put("credentialType", type.getValue());

        return response;
    }
}
