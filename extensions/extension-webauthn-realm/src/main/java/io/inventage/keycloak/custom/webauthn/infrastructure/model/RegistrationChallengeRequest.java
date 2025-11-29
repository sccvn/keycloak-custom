package io.inventage.keycloak.custom.webauthn.infrastructure.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request for registration challenge generation
 */
public class RegistrationChallengeRequest {
    @JsonProperty("username")
    private String username;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("credentialName")
    private String credentialName;

    @JsonProperty("type")
    private String type = "passwordless"; // passwordless or twofactor

    // Getters and setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
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
