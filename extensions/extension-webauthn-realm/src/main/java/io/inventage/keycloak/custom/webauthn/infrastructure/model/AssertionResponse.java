package io.inventage.keycloak.custom.webauthn.infrastructure.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Client assertion response from authenticator
 */
public class AssertionResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("rawId")
    private String rawId;

    @JsonProperty("response")
    private AssertionResponseData response;

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

    public AssertionResponseData getResponse() {
        return response;
    }

    public void setResponse(AssertionResponseData response) {
        this.response = response;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
