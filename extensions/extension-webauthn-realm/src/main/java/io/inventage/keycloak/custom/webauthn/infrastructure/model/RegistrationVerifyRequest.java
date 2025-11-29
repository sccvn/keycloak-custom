package io.inventage.keycloak.custom.webauthn.infrastructure.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request for registration verification
 */
public class RegistrationVerifyRequest {
    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("response")
    private AttestationResponse response;

    @JsonProperty("credentialName")
    private String credentialName;

    @JsonProperty("type")
    private String type = "passwordless"; // passwordless or twofactor

    // Getters and setters
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public AttestationResponse getResponse() {
        return response;
    }

    public void setResponse(AttestationResponse response) {
        this.response = response;
    }

    public String getCredentialName() {
        return credentialName;
    }

    public void setCredentialName(String credentialName) {
        this.credentialName = credentialName;
    }

    public String getType() {
        return type != null ? type : "passwordless";
    }

    public void setType(String type) {
        this.type = type;
    }

    public CredentialType getCredentialType() {
        return CredentialType.fromValue(this.type);
    }
}
