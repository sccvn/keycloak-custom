package io.inventage.keycloak.custom.webauthn.infrastructure.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response with registration challenge
 */
public class RegistrationChallengeResponse {
    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("challenge")
    private String challenge;

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("userName")
    private String userName;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("rp")
    private RelyingPartyInfo rp;

    @JsonProperty("user")
    private UserInfo user;

    @JsonProperty("pubKeyCredParams")
    private List<PublicKeyCredentialParameter> pubKeyCredParams;

    @JsonProperty("timeout")
    private long timeout = 60000;

    @JsonProperty("attestation")
    private String attestation = "direct";

    @JsonProperty("authenticatorSelection")
    private AuthenticatorSelectionCriteria authenticatorSelection;

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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public RelyingPartyInfo getRp() {
        return rp;
    }

    public void setRp(RelyingPartyInfo rp) {
        this.rp = rp;
    }

    public UserInfo getUser() {
        return user;
    }

    public void setUser(UserInfo user) {
        this.user = user;
    }

    public List<PublicKeyCredentialParameter> getPubKeyCredParams() {
        return pubKeyCredParams;
    }

    public void setPubKeyCredParams(List<PublicKeyCredentialParameter> pubKeyCredParams) {
        this.pubKeyCredParams = pubKeyCredParams;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public String getAttestation() {
        return attestation;
    }

    public void setAttestation(String attestation) {
        this.attestation = attestation;
    }

    public AuthenticatorSelectionCriteria getAuthenticatorSelection() {
        return authenticatorSelection;
    }

    public void setAuthenticatorSelection(AuthenticatorSelectionCriteria authenticatorSelection) {
        this.authenticatorSelection = authenticatorSelection;
    }

    /**
     * Relying Party information
     */
    public static class RelyingPartyInfo {
        @JsonProperty("name")
        private String name;

        @JsonProperty("id")
        private String id;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    /**
     * User information for registration
     */
    public static class UserInfo {
        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("displayName")
        private String displayName;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }

    /**
     * Public key credential parameters
     */
    public static class PublicKeyCredentialParameter {
        @JsonProperty("type")
        private String type = "public-key";

        @JsonProperty("alg")
        private int alg;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public int getAlg() {
            return alg;
        }

        public void setAlg(int alg) {
            this.alg = alg;
        }
    }

    /**
     * Authenticator selection criteria
     */
    public static class AuthenticatorSelectionCriteria {
        @JsonProperty("authenticatorAttachment")
        private String authenticatorAttachment;

        @JsonProperty("requireResidentKey")
        private boolean requireResidentKey;

        @JsonProperty("residentKey")
        private String residentKey;

        @JsonProperty("userVerification")
        private String userVerification = "preferred";

        public String getAuthenticatorAttachment() {
            return authenticatorAttachment;
        }

        public void setAuthenticatorAttachment(String authenticatorAttachment) {
            this.authenticatorAttachment = authenticatorAttachment;
        }

        public boolean isRequireResidentKey() {
            return requireResidentKey;
        }

        public void setRequireResidentKey(boolean requireResidentKey) {
            this.requireResidentKey = requireResidentKey;
        }

        public String getResidentKey() {
            return residentKey;
        }

        public void setResidentKey(String residentKey) {
            this.residentKey = residentKey;
        }

        public String getUserVerification() {
            return userVerification;
        }

        public void setUserVerification(String userVerification) {
            this.userVerification = userVerification;
        }
    }
}
