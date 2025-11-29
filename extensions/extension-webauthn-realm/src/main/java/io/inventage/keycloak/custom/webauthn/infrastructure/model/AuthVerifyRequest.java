package io.inventage.keycloak.custom.webauthn.infrastructure.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Authentication request verification
 */
public class AuthVerifyRequest {
    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("response")
    private AssertionResponse response;

    // Getters and setters
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public AssertionResponse getResponse() {
        return response;
    }

    public void setResponse(AssertionResponse response) {
        this.response = response;
    }
}
