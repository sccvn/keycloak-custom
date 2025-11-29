package io.inventage.keycloak.custom.webauthn.infrastructure.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Authenticator assertion response data
 */
public class AssertionResponseData {
    @JsonProperty("clientDataJSON")
    private String clientDataJSON;

    @JsonProperty("authenticatorData")
    private String authenticatorData;

    @JsonProperty("signature")
    private String signature;

    @JsonProperty("userHandle")
    private String userHandle;

    // Getters and setters
    public String getClientDataJSON() {
        return clientDataJSON;
    }

    public void setClientDataJSON(String clientDataJSON) {
        this.clientDataJSON = clientDataJSON;
    }

    public String getAuthenticatorData() {
        return authenticatorData;
    }

    public void setAuthenticatorData(String authenticatorData) {
        this.authenticatorData = authenticatorData;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getUserHandle() {
        return userHandle;
    }

    public void setUserHandle(String userHandle) {
        this.userHandle = userHandle;
    }
}
