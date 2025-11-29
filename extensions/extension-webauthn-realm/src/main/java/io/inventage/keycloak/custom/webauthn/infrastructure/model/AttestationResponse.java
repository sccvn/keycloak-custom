package io.inventage.keycloak.custom.webauthn.infrastructure.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Attestation response from client during registration
 */
public class AttestationResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("rawId")
    private String rawId;

    @JsonProperty("response")
    private AttestationResponseData response;

    @JsonProperty("type")
    private String type = "public-key";

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRawId() {
        return rawId;
    }

    public void setRawId(String rawId) {
        this.rawId = rawId;
    }

    public AttestationResponseData getResponse() {
        return response;
    }

    public void setResponse(AttestationResponseData response) {
        this.response = response;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
