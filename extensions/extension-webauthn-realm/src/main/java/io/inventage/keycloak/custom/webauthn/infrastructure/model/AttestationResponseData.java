package io.inventage.keycloak.custom.webauthn.infrastructure.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Attestation response data from authenticator
 */
public class AttestationResponseData {
    @JsonProperty("clientDataJSON")
    private String clientDataJSON;

    @JsonProperty("attestationObject")
    private String attestationObject;

    // Getters and setters
    public String getClientDataJSON() {
        return clientDataJSON;
    }

    public void setClientDataJSON(String clientDataJSON) {
        this.clientDataJSON = clientDataJSON;
    }

    public String getAttestationObject() {
        return attestationObject;
    }

    public void setAttestationObject(String attestationObject) {
        this.attestationObject = attestationObject;
    }
}
