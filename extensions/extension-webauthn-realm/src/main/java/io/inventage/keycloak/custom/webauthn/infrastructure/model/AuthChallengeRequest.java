package io.inventage.keycloak.custom.webauthn.infrastructure.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request for authentication challenge generation
 */
public class AuthChallengeRequest {
    @JsonProperty("username")
    private String username;

    // Getters and setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
