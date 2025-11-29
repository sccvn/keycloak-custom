package io.inventage.keycloak.custom.webauthn.infrastructure.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response with authentication challenge
 */
public class AuthenticationChallengeResponse {
    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("challenge")
    private String challenge;

    @JsonProperty("allowCredentials")
    private List<CredentialDescriptor> allowCredentials;

    @JsonProperty("timeout")
    private long timeout = 60000;

    @JsonProperty("userVerification")
    private String userVerification = "preferred";

    @JsonProperty("rpId")
    private String rpId;

    // Getters and setters
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getChallenge() {
        return challenge;
    }

    public void setChallenge(String challenge) {
        this.challenge = challenge;
    }

    public List<CredentialDescriptor> getAllowCredentials() {
        return allowCredentials;
    }

    public void setAllowCredentials(List<CredentialDescriptor> allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public String getUserVerification() {
        return userVerification;
    }

    public void setUserVerification(String userVerification) {
        this.userVerification = userVerification;
    }

    public String getRpId() {
        return rpId;
    }

    public void setRpId(String rpId) {
        this.rpId = rpId;
    }

    /**
     * Credential descriptor for allowed credentials
     */
    public static class CredentialDescriptor {
        @JsonProperty("type")
        private String type = "public-key";

        @JsonProperty("id")
        private String id;

        @JsonProperty("transports")
        private List<String> transports;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public List<String> getTransports() {
            return transports;
        }

        public void setTransports(List<String> transports) {
            this.transports = transports;
        }
    }
}
