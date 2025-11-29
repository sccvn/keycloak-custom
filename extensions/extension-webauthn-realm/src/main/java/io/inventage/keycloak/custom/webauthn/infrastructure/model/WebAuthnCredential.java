package io.inventage.keycloak.custom.webauthn.infrastructure.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * Represents a registered WebAuthn credential.
 * Stored as user attribute in Keycloak.
 */
public class WebAuthnCredential {

    @JsonProperty("credentialId")
    private String credentialId;

    @JsonProperty("credentialPublicKey")
    private String credentialPublicKey; // Base64url CBOR

    @JsonProperty("attestationObject")
    private String attestationObject; // Base64url

    @JsonProperty("signCount")
    private long signCount;

    @JsonProperty("credentialType")
    private String credentialType = "public-key";

    @JsonProperty("transportHints")
    private List<String> transportHints; // usb, ble, nfc, internal

    @JsonProperty("type")
    private CredentialType type = CredentialType.TYPE_PASSWORDLESS;

    @JsonProperty("metadata")
    private CredentialMetadata metadata;

    @JsonProperty("backup")
    private BackupEligibility backup;

    // Getters and setters
    public String getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    public String getCredentialPublicKey() {
        return credentialPublicKey;
    }

    public void setCredentialPublicKey(String credentialPublicKey) {
        this.credentialPublicKey = credentialPublicKey;
    }

    public String getAttestationObject() {
        return attestationObject;
    }

    public void setAttestationObject(String attestationObject) {
        this.attestationObject = attestationObject;
    }

    public long getSignCount() {
        return signCount;
    }

    public void setSignCount(long signCount) {
        this.signCount = signCount;
    }

    public String getCredentialType() {
        return credentialType;
    }

    public void setCredentialType(String credentialType) {
        this.credentialType = credentialType;
    }

    public List<String> getTransportHints() {
        return transportHints;
    }

    public void setTransportHints(List<String> transportHints) {
        this.transportHints = transportHints;
    }

    public BackupEligibility getBackup() {
        return backup;
    }

    public void setBackup(BackupEligibility backup) {
        this.backup = backup;
    }

    public void updateSignCount(long newSignCount) {
        this.signCount = newSignCount;
    }

    public void updateLastUsed() {
        if (this.metadata != null) {
            this.metadata.setLastUsedAt(Instant.now());
        }
    }

    public CredentialMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(CredentialMetadata metadata) {
        this.metadata = metadata;
    }

    public CredentialType getType() {
        return type != null ? type : CredentialType.TYPE_PASSWORDLESS;
    }

    public void setType(CredentialType type) {
        this.type = type != null ? type : CredentialType.TYPE_PASSWORDLESS;
    }

    public void setTypeFromString(String typeString) {
        this.type = CredentialType.fromValue(typeString);
    }

    public String getTypeAsString() {
        return this.type.getValue();
    }

    /**
     * Credential metadata
     */
    public static class CredentialMetadata {
        @JsonProperty("name")
        private String name;

        @JsonProperty("createdAt")
        private Instant createdAt;

        @JsonProperty("lastUsedAt")
        private Instant lastUsedAt;

        @JsonProperty("aaguid")
        private String aaguid;

        public CredentialMetadata() {
            this.createdAt = Instant.now();
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }

        public Instant getLastUsedAt() {
            return lastUsedAt;
        }

        public void setLastUsedAt(Instant lastUsedAt) {
            this.lastUsedAt = lastUsedAt;
        }

        public String getAaguid() {
            return aaguid;
        }

        public void setAaguid(String aaguid) {
            this.aaguid = aaguid;
        }
    }

    /**
     * Backup eligibility status
     */
    public static class BackupEligibility {
        @JsonProperty("eligible")
        private boolean eligible;

        @JsonProperty("state")
        private boolean state;

        public boolean isEligible() {
            return eligible;
        }

        public void setEligible(boolean eligible) {
            this.eligible = eligible;
        }

        public boolean isState() {
            return state;
        }

        public void setState(boolean state) {
            this.state = state;
        }
    }
}
