package io.inventage.keycloak.custom.webauthn.infrastructure.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response containing list of user's WebAuthn credentials
 */
public class CredentialListResponse {
    @JsonProperty("credentials")
    private List<WebAuthnCredential> credentials;

    @JsonProperty("total")
    private int total;

    // Getters and setters
    public List<WebAuthnCredential> getCredentials() {
        return credentials;
    }

    public void setCredentials(List<WebAuthnCredential> credentials) {
        this.credentials = credentials;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }
}
