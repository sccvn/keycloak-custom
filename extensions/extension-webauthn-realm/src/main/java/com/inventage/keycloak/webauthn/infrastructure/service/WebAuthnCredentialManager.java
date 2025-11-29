package com.inventage.keycloak.webauthn.infrastructure.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.inventage.keycloak.webauthn.infrastructure.exception.WebAuthnException;
import io.inventage.keycloak.custom.webauthn.infrastructure.model.WebAuthnCredential;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages WebAuthn credentials storage and retrieval.
 * Stores credentials as JSON in user attributes and challenges in memory cache.
 */
public class WebAuthnCredentialManager {

    private static final Logger LOG = Logger.getLogger(WebAuthnCredentialManager.class);
    private static final String CREDENTIAL_ATTRIBUTE_KEY = "webauthn.credentials";
    private static final int DEFAULT_CHALLENGE_TTL_SECONDS = 300; // 5 minutes

    private final KeycloakSession session;
    private final ObjectMapper objectMapper;

    // In-memory challenge cache with TTL
    private static final Map<String, ChallengeData> challengeCache = new ConcurrentHashMap<>();

    /**
     * Creates a new WebAuthnCredentialManager.
     *
     * @param session The Keycloak session
     */
    public WebAuthnCredentialManager(KeycloakSession session) {
        this.session = session;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Store a credential for a user.
     *
     * @param userId The user ID
     * @param credential The WebAuthn credential to store
     * @throws WebAuthnException if storage fails
     */
    public void storeCredential(String userId, WebAuthnCredential credential) throws WebAuthnException {
        try {
            RealmModel realm = session.getContext().getRealm();
            UserModel user = session.users().getUserById(realm, userId);

            if (user == null) {
                throw new WebAuthnException("USER_NOT_FOUND", "User not found: " + userId, 404);
            }

            // Get existing credentials
            List<WebAuthnCredential> credentials = getCredentials(userId);

            // Check for duplicate credential ID
            boolean exists = credentials.stream()
                    .anyMatch(c -> c.getCredentialId().equals(credential.getCredentialId()));

            if (exists) {
                throw new WebAuthnException("DUPLICATE_CREDENTIAL",
                        "Credential with this ID already exists", 409);
            }

            // Add new credential
            credentials.add(credential);

            // Serialize and store
            String json = objectMapper.writeValueAsString(credentials);
            user.setSingleAttribute(CREDENTIAL_ATTRIBUTE_KEY, json);

            LOG.infof("Stored credential for user %s: %s (type: %s)",
                    userId, credential.getCredentialId(), credential.getTypeAsString());

        } catch (WebAuthnException e) {
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to store credential for user: %s", userId);
            throw new WebAuthnException("STORAGE_ERROR", "Failed to store credential", 500, e);
        }
    }

    /**
     * Get all credentials for a user.
     *
     * @param userId The user ID
     * @return List of WebAuthn credentials (empty if none exist)
     * @throws WebAuthnException if retrieval fails
     */
    public List<WebAuthnCredential> getCredentials(String userId) throws WebAuthnException {
        try {
            RealmModel realm = session.getContext().getRealm();
            UserModel user = session.users().getUserById(realm, userId);

            if (user == null) {
                throw new WebAuthnException("USER_NOT_FOUND", "User not found: " + userId, 404);
            }

            List<String> attrs = user.getAttributes().get(CREDENTIAL_ATTRIBUTE_KEY);
            if (attrs == null || attrs.isEmpty()) {
                return new ArrayList<>();
            }

            String json = attrs.get(0);
            List<WebAuthnCredential> credentials = objectMapper.readValue(
                    json, new TypeReference<List<WebAuthnCredential>>() {});

            LOG.debugf("Retrieved %d credentials for user %s", credentials.size(), userId);
            return credentials;

        } catch (WebAuthnException e) {
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to retrieve credentials for user: %s", userId);
            throw new WebAuthnException("RETRIEVAL_ERROR", "Failed to retrieve credentials", 500, e);
        }
    }

    /**
     * Find a specific credential by ID across all users.
     *
     * @param credentialId The credential ID to find
     * @return The WebAuthn credential or null if not found
     * @throws WebAuthnException if search fails
     */
    public WebAuthnCredential findCredentialById(String credentialId) throws WebAuthnException {
        try {
            RealmModel realm = session.getContext().getRealm();

            // Search through all users with webauthn credentials
            List<UserModel> users = session.users()
                    .searchForUserByUserAttributeStream(realm, CREDENTIAL_ATTRIBUTE_KEY, null)
                    .toList();

            for (UserModel user : users) {
                List<WebAuthnCredential> credentials = getCredentials(user.getId());
                Optional<WebAuthnCredential> found = credentials.stream()
                        .filter(c -> c.getCredentialId().equals(credentialId))
                        .findFirst();

                if (found.isPresent()) {
                    LOG.debugf("Found credential %s for user %s", credentialId, user.getId());
                    return found.get();
                }
            }

            LOG.debugf("Credential not found: %s", credentialId);
            return null;

        } catch (Exception e) {
            LOG.errorf(e, "Failed to find credential: %s", credentialId);
            throw new WebAuthnException("SEARCH_ERROR", "Failed to find credential", 500, e);
        }
    }

    /**
     * Delete a credential for a user.
     *
     * @param userId The user ID
     * @param credentialId The credential ID to delete
     * @throws WebAuthnException if deletion fails
     */
    public void deleteCredential(String userId, String credentialId) throws WebAuthnException {
        try {
            List<WebAuthnCredential> credentials = getCredentials(userId);

            boolean removed = credentials.removeIf(c -> c.getCredentialId().equals(credentialId));

            if (!removed) {
                throw new WebAuthnException("CREDENTIAL_NOT_FOUND",
                        "Credential not found: " + credentialId, 404);
            }

            // Update stored credentials
            RealmModel realm = session.getContext().getRealm();
            UserModel user = session.users().getUserById(realm, userId);

            if (credentials.isEmpty()) {
                user.removeAttribute(CREDENTIAL_ATTRIBUTE_KEY);
            } else {
                String json = objectMapper.writeValueAsString(credentials);
                user.setSingleAttribute(CREDENTIAL_ATTRIBUTE_KEY, json);
            }

            LOG.infof("Deleted credential %s for user %s", credentialId, userId);

        } catch (WebAuthnException e) {
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to delete credential for user: %s", userId);
            throw new WebAuthnException("DELETE_ERROR", "Failed to delete credential", 500, e);
        }
    }

    /**
     * Update an existing credential.
     *
     * @param userId The user ID
     * @param credential The updated credential
     * @throws WebAuthnException if update fails
     */
    public void updateCredential(String userId, WebAuthnCredential credential) throws WebAuthnException {
        try {
            List<WebAuthnCredential> credentials = getCredentials(userId);

            // Find and replace the credential
            boolean updated = false;
            for (int i = 0; i < credentials.size(); i++) {
                if (credentials.get(i).getCredentialId().equals(credential.getCredentialId())) {
                    credentials.set(i, credential);
                    updated = true;
                    break;
                }
            }

            if (!updated) {
                throw new WebAuthnException("CREDENTIAL_NOT_FOUND",
                        "Credential not found: " + credential.getCredentialId(), 404);
            }

            // Store updated list
            RealmModel realm = session.getContext().getRealm();
            UserModel user = session.users().getUserById(realm, userId);
            String json = objectMapper.writeValueAsString(credentials);
            user.setSingleAttribute(CREDENTIAL_ATTRIBUTE_KEY, json);

            LOG.debugf("Updated credential %s for user %s", credential.getCredentialId(), userId);

        } catch (WebAuthnException e) {
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to update credential for user: %s", userId);
            throw new WebAuthnException("UPDATE_ERROR", "Failed to update credential", 500, e);
        }
    }

    /**
     * Store a challenge with TTL.
     *
     * @param userId The user ID
     * @param challenge The challenge string
     * @param type The challenge type (registration or authentication)
     * @param ttlSeconds Time to live in seconds
     * @return Session ID for the challenge
     */
    public String storeChallenge(String userId, String challenge, String type, int ttlSeconds) {
        String sessionId = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);

        ChallengeData data = new ChallengeData(userId, challenge, type, expiresAt);
        challengeCache.put(sessionId, data);

        LOG.debugf("Stored challenge: session=%s, type=%s, userId=%s, ttl=%ds",
                sessionId, type, userId, ttlSeconds);

        // Clean up expired challenges periodically
        cleanupExpiredChallenges();

        return sessionId;
    }

    /**
     * Store a challenge with default TTL.
     *
     * @param userId The user ID
     * @param challenge The challenge string
     * @param type The challenge type
     * @return Session ID for the challenge
     */
    public String storeChallenge(String userId, String challenge, String type) {
        return storeChallenge(userId, challenge, type, DEFAULT_CHALLENGE_TTL_SECONDS);
    }

    /**
     * Get a challenge by session ID.
     *
     * @param sessionId The session ID
     * @return The challenge data or null if not found/expired
     */
    public ChallengeData getChallenge(String sessionId) {
        ChallengeData data = challengeCache.get(sessionId);

        if (data == null) {
            LOG.debugf("Challenge not found: %s", sessionId);
            return null;
        }

        if (data.isExpired()) {
            LOG.debugf("Challenge expired: %s", sessionId);
            challengeCache.remove(sessionId);
            return null;
        }

        LOG.debugf("Retrieved challenge: %s", sessionId);
        return data;
    }

    /**
     * Delete a challenge.
     *
     * @param sessionId The session ID
     */
    public void deleteChallenge(String sessionId) {
        challengeCache.remove(sessionId);
        LOG.debugf("Deleted challenge: %s", sessionId);
    }

    /**
     * Clean up expired challenges from cache.
     */
    private void cleanupExpiredChallenges() {
        Instant now = Instant.now();
        int removed = 0;

        Iterator<Map.Entry<String, ChallengeData>> iterator = challengeCache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ChallengeData> entry = iterator.next();
            if (entry.getValue().isExpired()) {
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            LOG.debugf("Cleaned up %d expired challenges", removed);
        }
    }

    /**
     * Challenge data with TTL.
     */
    public static class ChallengeData {
        private final String userId;
        private final String challenge;
        private final String type;
        private final Instant expiresAt;

        public ChallengeData(String userId, String challenge, String type, Instant expiresAt) {
            this.userId = userId;
            this.challenge = challenge;
            this.type = type;
            this.expiresAt = expiresAt;
        }

        public String getUserId() {
            return userId;
        }

        public String getChallenge() {
            return challenge;
        }

        public String getType() {
            return type;
        }

        public Instant getExpiresAt() {
            return expiresAt;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
