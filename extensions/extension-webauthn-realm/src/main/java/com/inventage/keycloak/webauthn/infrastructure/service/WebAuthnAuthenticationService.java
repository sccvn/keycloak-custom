package com.inventage.keycloak.webauthn.infrastructure.service;

import com.inventage.keycloak.webauthn.infrastructure.exception.ChallengeExpiredException;
import com.inventage.keycloak.webauthn.infrastructure.exception.InvalidCredentialException;
import com.inventage.keycloak.webauthn.infrastructure.exception.WebAuthnException;
import com.inventage.keycloak.webauthn.util.Base64Util;
import io.inventage.keycloak.custom.webauthn.infrastructure.model.WebAuthnCredential;
import com.webauthn4j.converter.util.CborConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.client.ClientDataType;
import com.webauthn4j.data.client.CollectedClientData;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/**
 * Service for WebAuthn authentication (assertion verification).
 * Handles challenge generation and signature verification.
 */
public class WebAuthnAuthenticationService {

    private static final Logger LOG = Logger.getLogger(WebAuthnAuthenticationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CHALLENGE_LENGTH = 32;

    private final KeycloakSession session;
    private final WebAuthnCredentialManager credentialManager;
    private final ObjectConverter converter;
    private final CborConverter cborConverter;

    public WebAuthnAuthenticationService(KeycloakSession session) {
        this.session = session;
        this.credentialManager = new WebAuthnCredentialManager(session);
        this.converter = new ObjectConverter();
        this.cborConverter = converter.getCborConverter();
    }

    /**
     * Generate authentication challenge for a user.
     *
     * @param username Username to authenticate
     * @return Map containing challenge and allowed credentials
     * @throws WebAuthnException if challenge generation fails
     */
    public Map<String, Object> generateChallenge(String username) throws WebAuthnException {
        try {
            RealmModel realm = session.getContext().getRealm();
            UserModel user = session.users().getUserByUsername(realm, username);

            if (user == null) {
                throw new WebAuthnException("USER_NOT_FOUND", "User not found: " + username, 404);
            }

            // Generate cryptographically secure random challenge
            byte[] challengeBytes = new byte[CHALLENGE_LENGTH];
            RANDOM.nextBytes(challengeBytes);
            String challenge = Base64Util.encodeToString(challengeBytes);

            // Store challenge with 5 minute TTL
            String sessionId = credentialManager.storeChallenge(user.getId(),
                    challenge, "authentication", 300);

            // Get user's registered credentials
            List<WebAuthnCredential> credentials = credentialManager.getCredentials(user.getId());

            LOG.infof("Generated authentication challenge for user %s (session: %s, credentials: %d)",
                    username, sessionId, credentials.size());

            // Build challenge response
            return buildAuthenticationChallengeResponse(sessionId, challenge, user, credentials);

        } catch (WebAuthnException e) {
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to generate authentication challenge for user: %s", username);
            throw new WebAuthnException("CHALLENGE_ERROR", "Challenge generation failed", 500, e);
        }
    }

    /**
     * Verify authentication assertion.
     *
     * @param sessionId Session ID from challenge
     * @param credentialId Credential ID used for authentication
     * @param clientDataJSON Base64url encoded client data JSON
     * @param authenticatorData Base64url encoded authenticator data
     * @param signature Base64url encoded signature
     * @param userHandle Base64url encoded user handle (optional)
     * @return Authenticated UserModel
     * @throws InvalidCredentialException if verification fails
     * @throws ChallengeExpiredException if challenge is expired
     */
    public UserModel verifyAssertion(String sessionId, String credentialId,
            String clientDataJSON, String authenticatorData, String signature,
            String userHandle) throws InvalidCredentialException, ChallengeExpiredException {
        try {
            LOG.debugf("Verifying assertion for credential: %s", credentialId);

            // 1. Get and validate challenge
            WebAuthnCredentialManager.ChallengeData challengeData = credentialManager.getChallenge(sessionId);

            if (challengeData == null || challengeData.isExpired()) {
                throw new ChallengeExpiredException("Challenge expired or not found");
            }

            // 2. Parse and validate client data
            byte[] clientDataBytes = Base64Util.decode(clientDataJSON);
            CollectedClientData collectedClientData = parseClientDataJSON(clientDataBytes);
            validateClientData(collectedClientData, challengeData.getChallenge());

            // 3. Parse authenticator data
            byte[] authDataBytes = Base64Util.decode(authenticatorData);
            AuthenticatorData authData = parseAuthenticatorData(authDataBytes);

            // 4. Get credential
            WebAuthnCredential credential = credentialManager.findCredentialById(credentialId);

            if (credential == null) {
                LOG.warnf("Credential not found: %s", credentialId);
                throw new InvalidCredentialException("Credential not found");
            }

            // 5. Verify signature
            verifySignature(clientDataBytes, authDataBytes, signature, credential);

            // 6. Validate sign count (detect cloned authenticators)
            validateSignCount(authData.signCount, credential);

            // 7. Update credential
            RealmModel realm = session.getContext().getRealm();
            UserModel user = session.users().getUserById(realm, challengeData.getUserId());

            credential.updateSignCount(authData.signCount);
            credential.updateLastUsed();
            credentialManager.updateCredential(challengeData.getUserId(), credential);

            // 8. Clear challenge
            credentialManager.deleteChallenge(sessionId);

            LOG.infof("Authentication successful for user: %s (credential: %s)",
                    user.getUsername(), credentialId);

            return user;

        } catch (ChallengeExpiredException | InvalidCredentialException e) {
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Assertion verification failed");
            throw new InvalidCredentialException("Assertion verification failed", e);
        }
    }

    /**
     * Parse client data JSON.
     *
     * @param clientDataBytes Client data JSON bytes
     * @return Parsed CollectedClientData
     * @throws InvalidCredentialException if parsing fails
     */
    private CollectedClientData parseClientDataJSON(byte[] clientDataBytes)
            throws InvalidCredentialException {
        try {
            CollectedClientData clientData = converter.getJsonConverter()
                    .readValue(clientDataBytes, CollectedClientData.class);

            LOG.debugf("Client data parsed: type=%s, origin=%s",
                    clientData.getType(), clientData.getOrigin());

            return clientData;

        } catch (Exception e) {
            LOG.errorf(e, "Failed to parse client data JSON");
            throw new InvalidCredentialException("Client data parsing failed", e);
        }
    }

    /**
     * Validate client data.
     *
     * @param clientData Parsed client data
     * @param expectedChallenge Expected challenge value
     * @throws InvalidCredentialException if validation fails
     */
    private void validateClientData(CollectedClientData clientData, String expectedChallenge)
            throws InvalidCredentialException {
        // Verify type is "webauthn.get"
        if (!ClientDataType.WEBAUTHN_GET.equals(clientData.getType())) {
            throw new InvalidCredentialException(
                    "Invalid client data type: " + clientData.getType());
        }

        // Verify challenge matches
        String receivedChallenge = Base64Util.encodeToString(clientData.getChallenge().getBytes());
        if (!expectedChallenge.equals(receivedChallenge)) {
            LOG.warnf("Challenge mismatch: expected=%s, received=%s",
                    expectedChallenge, receivedChallenge);
            throw new InvalidCredentialException("Challenge mismatch");
        }

        // Log origin (in production, validate against allowed origins)
        LOG.debugf("Client data validated. Origin: %s", clientData.getOrigin());
    }

    /**
     * Parse authenticator data from bytes.
     *
     * @param authDataBytes Authenticator data bytes
     * @return Parsed AuthenticatorData
     * @throws InvalidCredentialException if parsing fails
     */
    private AuthenticatorData parseAuthenticatorData(byte[] authDataBytes)
            throws InvalidCredentialException {
        try {
            if (authDataBytes.length < 37) {
                throw new InvalidCredentialException("Authenticator data too short");
            }

            // Parse flags (byte 32)
            byte flags = authDataBytes[32];
            boolean userPresent = (flags & 0x01) != 0;
            boolean userVerified = (flags & 0x04) != 0;

            // Parse sign count (bytes 33-36)
            long signCount = ByteBuffer.wrap(authDataBytes, 33, 4).getInt() & 0xFFFFFFFFL;

            LOG.debugf("Authenticator data: UP=%b, UV=%b, signCount=%d",
                    userPresent, userVerified, signCount);

            return new AuthenticatorData(userPresent, userVerified, signCount);

        } catch (Exception e) {
            LOG.errorf(e, "Failed to parse authenticator data");
            throw new InvalidCredentialException("Authenticator data parsing failed", e);
        }
    }

    /**
     * Verify cryptographic signature.
     *
     * @param clientDataBytes Client data JSON bytes
     * @param authDataBytes Authenticator data bytes
     * @param signatureBase64 Base64url encoded signature
     * @param credential Stored credential with public key
     * @throws InvalidCredentialException if signature verification fails
     */
    private void verifySignature(byte[] clientDataBytes, byte[] authDataBytes,
            String signatureBase64, WebAuthnCredential credential)
            throws InvalidCredentialException {
        try {
            // Decode signature
            byte[] signature = Base64Util.decode(signatureBase64);

            // Hash client data with SHA-256
            byte[] clientDataHash = hashSHA256(clientDataBytes);

            // Concatenate authenticator data + client data hash
            byte[] signedData = concatenate(authDataBytes, clientDataHash);

            // Decode public key from COSE format
            byte[] publicKeyBytes = Base64Util.decode(credential.getCredentialPublicKey());
            PublicKey publicKey = decodePublicKey(publicKeyBytes);

            // Verify signature
            Signature sig = Signature.getInstance("SHA256withECDSA");
            sig.initVerify(publicKey);
            sig.update(signedData);

            boolean valid = sig.verify(signature);

            if (!valid) {
                LOG.warnf("Signature verification failed for credential: %s",
                        credential.getCredentialId());
                throw new InvalidCredentialException("Signature verification failed");
            }

            LOG.debugf("Signature verified successfully for credential: %s",
                    credential.getCredentialId());

        } catch (InvalidCredentialException e) {
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Signature verification error");
            throw new InvalidCredentialException("Signature verification error", e);
        }
    }

    /**
     * Validate sign count to detect cloned authenticators.
     *
     * @param newSignCount New sign count from authenticator
     * @param credential Stored credential
     * @throws InvalidCredentialException if sign count validation fails
     */
    private void validateSignCount(long newSignCount, WebAuthnCredential credential)
            throws InvalidCredentialException {
        long oldSignCount = credential.getSignCount();

        // Sign count of 0 means authenticator doesn't support counters
        if (oldSignCount == 0 && newSignCount == 0) {
            LOG.debugf("Sign count not supported by authenticator");
            return;
        }

        // Sign count must increase for each assertion
        if (newSignCount > 0 && newSignCount <= oldSignCount) {
            LOG.warnf("Sign count validation failed. Old: %d, New: %d - possible clone detected",
                    oldSignCount, newSignCount);
            throw new InvalidCredentialException(
                    "Sign count validation failed - possible authenticator clone");
        }

        LOG.debugf("Sign count validation passed: %d -> %d", oldSignCount, newSignCount);
    }

    /**
     * Hash data using SHA-256.
     *
     * @param data Data to hash
     * @return SHA-256 hash
     * @throws InvalidCredentialException if hashing fails
     */
    private byte[] hashSHA256(byte[] data) throws InvalidCredentialException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (Exception e) {
            throw new InvalidCredentialException("Hash computation failed", e);
        }
    }

    /**
     * Concatenate two byte arrays.
     *
     * @param a First array
     * @param b Second array
     * @return Concatenated array
     */
    private byte[] concatenate(byte[] a, byte[] b) {
        byte[] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    /**
     * Decode public key from COSE format.
     *
     * @param coseKeyBytes COSE encoded public key
     * @return PublicKey object
     * @throws Exception if decoding fails
     */
    private PublicKey decodePublicKey(byte[] coseKeyBytes) throws Exception {
        // Parse COSE key and convert to Java PublicKey
        // This is a simplified version - production should use full COSE key parsing
        Map<Integer, Object> coseKey = cborConverter.readValue(coseKeyBytes, Map.class);

        // For now, create a placeholder - full implementation would decode based on key type
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(coseKeyBytes);
        return keyFactory.generatePublic(keySpec);
    }

    /**
     * Build authentication challenge response.
     *
     * @param sessionId Session ID
     * @param challenge Challenge string
     * @param user User model
     * @param credentials User's registered credentials
     * @return Map containing challenge response data
     */
    private Map<String, Object> buildAuthenticationChallengeResponse(String sessionId,
            String challenge, UserModel user, List<WebAuthnCredential> credentials) {

        Map<String, Object> response = new HashMap<>();

        response.put("sessionId", sessionId);
        response.put("challenge", challenge);
        response.put("timeout", 60000);
        response.put("rpId", session.getContext().getUri().getBaseUri().getHost());
        response.put("userVerification", "preferred");

        // Build allowed credentials list
        List<Map<String, Object>> allowCredentials = new ArrayList<>();
        for (WebAuthnCredential cred : credentials) {
            Map<String, Object> credInfo = new HashMap<>();
            credInfo.put("type", "public-key");
            credInfo.put("id", cred.getCredentialId());
            if (cred.getTransportHints() != null) {
                credInfo.put("transports", cred.getTransportHints());
            }
            allowCredentials.add(credInfo);
        }
        response.put("allowCredentials", allowCredentials);

        return response;
    }

    /**
     * Parsed authenticator data.
     */
    private static class AuthenticatorData {
        final boolean userPresent;
        final boolean userVerified;
        final long signCount;

        AuthenticatorData(boolean userPresent, boolean userVerified, long signCount) {
            this.userPresent = userPresent;
            this.userVerified = userVerified;
            this.signCount = signCount;
        }
    }
}
