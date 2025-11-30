# Future Features Specification

## Overview

This document outlines future feature enhancements for the Keycloak WebAuthn extension, organized by business value and technical complexity. Each feature includes detailed specifications, acceptance criteria, and implementation guidance.

---

## Feature 1: Conditional UI (Autofill/Autocomplete)

**Business Value**: HIGH | **Technical Complexity**: MEDIUM | **Timeline**: 4-6 weeks

### Description
Enable WebAuthn credentials to appear in browser password autofill UI, allowing users to authenticate with a single click without entering username.

### User Story
```
As a returning user
I want to see my passkey in the browser's autofill dropdown
So that I can sign in with one click instead of typing my username
```

### Technical Specification

#### Browser Support
- Chrome 108+, Edge 108+, Safari 16+
- Feature detection: `PublicKeyCredential.isConditionalMediationAvailable()`

#### Implementation

**1. Frontend Integration (JavaScript)**
```javascript
// Check conditional UI support
const conditionalSupported = await PublicKeyCredential
    .isConditionalMediationAvailable();

if (conditionalSupported) {
    // Fetch challenge without username
    const challengeResponse = await fetch('/api/webauthn/auth/challenge-conditional', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    });

    const options = await challengeResponse.json();

    // Request credentials with conditional mediation
    const credential = await navigator.credentials.get({
        publicKey: options.publicKey,
        mediation: 'conditional' // Key parameter
    });

    // Verify credential
    await fetch('/api/webauthn/auth/verify', {
        method: 'POST',
        body: JSON.stringify({
            credentialResponse: credential,
            sessionId: options.sessionId
        })
    });
}
```

**2. Backend API Endpoint**
```java
@POST
@Path("auth/challenge-conditional")
@Produces(MediaType.APPLICATION_JSON)
public Response generateConditionalChallenge() {
    // Generate challenge without requiring username
    byte[] challenge = generateRandomChallenge();
    String sessionId = storeChallenge(null, challenge, "conditional", 300);

    Map<String, Object> options = new HashMap<>();
    options.put("sessionId", sessionId);

    // PublicKeyCredentialRequestOptions
    Map<String, Object> publicKey = new HashMap<>();
    publicKey.put("challenge", Base64Util.encodeToString(challenge));
    publicKey.put("rpId", getRpId());
    publicKey.put("userVerification", "preferred");
    publicKey.put("timeout", 60000);
    // No allowCredentials - browser discovers all resident keys

    options.put("publicKey", publicKey);
    options.put("mediation", "conditional");

    return Response.ok(options).build();
}
```

**3. HTML Integration**
```html
<!-- Input field with autocomplete attribute -->
<input
    type="text"
    name="username"
    autocomplete="username webauthn"
    id="username-field"
/>
```

### Acceptance Criteria
- [ ] Browser detects conditional UI support
- [ ] Passkeys appear in autofill dropdown
- [ ] Single-click authentication works
- [ ] Graceful fallback for unsupported browsers
- [ ] Works with resident (discoverable) credentials only

### Dependencies
- Feature 3.1: Resident Key Support (must be implemented first)

---

## Feature 2: Attestation Statement Verification

**Business Value**: HIGH | **Technical Complexity**: HIGH | **Timeline**: 6-8 weeks

### Description
Verify authenticator attestation to ensure credential authenticity and detect potentially malicious authenticators.

### Business Justification
- **Enterprise Security**: Verify employees use approved hardware keys (YubiKey, Titan)
- **Compliance**: Meet regulatory requirements for strong authentication
- **Fraud Prevention**: Detect software-based "authenticators" that bypass security

### Attestation Formats

| Format | Description | Use Case | Verification Complexity |
|--------|-------------|----------|------------------------|
| `packed` | FIDO2 standard format | Most hardware keys | Medium |
| `tpm` | Trusted Platform Module | Windows Hello, Android | High |
| `android-key` | Android Keystore | Android devices | Medium |
| `android-safetynet` | Google SafetyNet API | Android apps | High |
| `fido-u2f` | Legacy U2F format | Older security keys | Low |
| `apple` | Apple Secure Enclave | Face ID, Touch ID | Medium |
| `none` | Self-attestation | Software authenticators | None |

### Implementation

#### 1. Attestation Verification Service
```java
public class AttestationVerificationService {

    private final MetadataService metadataService;
    private final CertificateValidator certificateValidator;

    public AttestationResult verifyAttestation(
            AttestationObject attestationObject,
            byte[] clientDataHash,
            AttestationConveyancePreference preference) {

        String format = attestationObject.getFormat();

        // Check if verification required
        if (preference == AttestationConveyancePreference.NONE) {
            return AttestationResult.skipped();
        }

        AttestationStatement attStmt = attestationObject.getAttestationStatement();
        AuthenticatorData authData = attestationObject.getAuthenticatorData();

        return switch (format) {
            case "packed" -> verifyPackedAttestation(attStmt, authData, clientDataHash);
            case "tpm" -> verifyTPMAttestation(attStmt, authData, clientDataHash);
            case "android-key" -> verifyAndroidKeyAttestation(attStmt, authData, clientDataHash);
            case "android-safetynet" -> verifySafetyNetAttestation(attStmt, authData, clientDataHash);
            case "fido-u2f" -> verifyFidoU2FAttestation(attStmt, authData, clientDataHash);
            case "apple" -> verifyAppleAttestation(attStmt, authData, clientDataHash);
            case "none" -> AttestationResult.selfAttestation();
            default -> throw new UnsupportedAttestationFormatException(format);
        };
    }

    private AttestationResult verifyPackedAttestation(
            AttestationStatement attStmt,
            AuthenticatorData authData,
            byte[] clientDataHash) {

        // Extract components
        byte[] sig = attStmt.getBytes("sig");
        int alg = attStmt.getInt("alg");
        List<byte[]> x5c = attStmt.getBytesList("x5c");

        if (x5c != null && !x5c.isEmpty()) {
            // Full attestation with certificate chain
            return verifyFullPackedAttestation(x5c, sig, alg, authData, clientDataHash);
        } else {
            // Self-attestation
            return verifySelfPackedAttestation(sig, alg, authData, clientDataHash);
        }
    }

    private AttestationResult verifyFullPackedAttestation(
            List<byte[]> x5c,
            byte[] signature,
            int algorithm,
            AuthenticatorData authData,
            byte[] clientDataHash) {

        try {
            // 1. Parse certificate chain
            X509Certificate attestationCert = parseCertificate(x5c.get(0));
            List<X509Certificate> certChain = x5c.stream()
                .map(this::parseCertificate)
                .collect(Collectors.toList());

            // 2. Verify certificate chain to trusted root
            certificateValidator.validateChain(certChain, getTrustedRoots());

            // 3. Verify certificate constraints
            validateAttestationCertificate(attestationCert);

            // 4. Verify signature over (authData || clientDataHash)
            byte[] signedData = concatenate(authData.getBytes(), clientDataHash);
            boolean signatureValid = verifySignature(
                attestationCert.getPublicKey(),
                signedData,
                signature,
                algorithm
            );

            if (!signatureValid) {
                throw new AttestationException("Signature verification failed");
            }

            // 5. Lookup authenticator metadata
            byte[] aaguid = authData.getAttestedCredentialData().getAaguid();
            MetadataStatement metadata = metadataService.getMetadata(aaguid);

            return AttestationResult.builder()
                .trustPath(AttestationTrustPath.FULL)
                .format("packed")
                .certificateChain(certChain)
                .metadata(metadata)
                .verified(true)
                .build();

        } catch (Exception e) {
            LOG.error("Packed attestation verification failed", e);
            return AttestationResult.failed(e.getMessage());
        }
    }

    private void validateAttestationCertificate(X509Certificate cert) throws Exception {
        // 1. Version MUST be 3
        if (cert.getVersion() != 3) {
            throw new AttestationException("Certificate version must be 3");
        }

        // 2. Subject field MUST contain OU = "Authenticator Attestation"
        String subject = cert.getSubjectX500Principal().getName();
        if (!subject.contains("OU=Authenticator Attestation")) {
            throw new AttestationException("Invalid certificate subject");
        }

        // 3. Check Basic Constraints extension
        if (cert.getBasicConstraints() != -1) {
            throw new AttestationException("Certificate must not be a CA");
        }

        // 4. Verify id-fido-gen-ce-aaguid extension contains AAGUID
        byte[] aaguidExtension = cert.getExtensionValue("1.3.6.1.4.1.45724.1.1.4");
        // ... validation logic
    }
}
```

#### 2. FIDO Metadata Service Integration
```java
@ApplicationScoped
public class FIDOMetadataService {

    private static final String MDS_URL = "https://mds.fidoalliance.org/";
    private final Map<String, MetadataStatement> metadataCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // Download FIDO MDS3 Blob on startup
        refreshMetadata();
    }

    @Scheduled(cron = "0 0 * * * ?") // Every hour
    public void refreshMetadata() {
        try {
            // 1. Download MDS3 Blob
            String blob = downloadMDSBlob();

            // 2. Verify JWT signature
            MetadataBLOBPayload payload = verifyAndParseMDSBlob(blob);

            // 3. Cache metadata entries
            for (MetadataBLOBPayloadEntry entry : payload.getEntries()) {
                String aaguid = entry.getAaguid();
                MetadataStatement metadata = entry.getMetadataStatement();
                metadataCache.put(aaguid, metadata);
            }

            LOG.info("FIDO MDS refreshed: {} entries", metadataCache.size());

        } catch (Exception e) {
            LOG.error("Failed to refresh FIDO MDS", e);
        }
    }

    public MetadataStatement getMetadata(byte[] aaguid) {
        String aaguidHex = Hex.encodeHexString(aaguid);
        MetadataStatement metadata = metadataCache.get(aaguidHex);

        if (metadata != null) {
            return metadata;
        }

        // Fallback to direct MDS lookup
        return fetchMetadataFromMDS(aaguidHex);
    }

    public boolean isAuthenticatorTrusted(byte[] aaguid, AttestationType type) {
        MetadataStatement metadata = getMetadata(aaguid);

        if (metadata == null) {
            return false; // Unknown authenticator
        }

        // Check status reports for security issues
        for (StatusReport report : metadata.getStatusReports()) {
            AuthenticatorStatus status = report.getStatus();

            if (status == AuthenticatorStatus.REVOKED ||
                status == AuthenticatorStatus.USER_VERIFICATION_BYPASS ||
                status == AuthenticatorStatus.ATTESTATION_KEY_COMPROMISE) {
                LOG.warn("Authenticator {} has status: {}", aaguidHex, status);
                return false;
            }
        }

        return true;
    }
}
```

#### 3. Realm-Level Attestation Policy
```java
// Realm attribute configuration
public enum AttestationPolicy {
    NONE,           // Accept any authenticator
    INDIRECT,       // Prefer attestation but don't require
    DIRECT,         // Require full attestation
    ENTERPRISE      // Require trusted authenticators only
}

// Configuration UI in Admin Console
@Path("webauthn/attestation-policy")
public class AttestationPolicyResource {

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updatePolicy(AttestationPolicyConfig config) {
        realm.setAttribute("webauthn.attestationPolicy", config.getPolicy().name());
        realm.setAttribute("webauthn.trustedAAGUIDs", String.join(",", config.getTrustedAAGUIDs()));

        return Response.ok().build();
    }
}
```

### Acceptance Criteria
- [ ] Verify `packed`, `tpm`, `android-key`, `fido-u2f`, `apple` formats
- [ ] Integrate FIDO MDS for authenticator metadata
- [ ] Reject revoked/compromised authenticators
- [ ] Support enterprise whitelist (only YubiKey 5 series, etc.)
- [ ] Log attestation verification results to audit log
- [ ] Handle attestation verification failures gracefully

### Security Considerations
- **Certificate Pinning**: For enterprise deployments, pin expected attestation root certificates
- **MDS Caching**: Cache MDS blob for 24 hours, verify JWT signature
- **Graceful Degradation**: If MDS unavailable, log warning and proceed (configurable)
- **Privacy**: Direct attestation reveals authenticator model to RP

---

## Feature 3: Account Recovery Mechanisms

**Business Value**: CRITICAL | **Technical Complexity**: MEDIUM | **Timeline**: 3-4 weeks

### Description
Provide secure recovery options when users lose access to their WebAuthn credentials.

### Problem Statement
Users who lose their security key or device are permanently locked out of their account, requiring manual admin intervention.

### Recovery Options

#### Option 1: Recovery Codes (Recommended)
```java
public class RecoveryCodeService {

    private static final int RECOVERY_CODE_COUNT = 10;
    private static final int RECOVERY_CODE_LENGTH = 12;

    public List<String> generateRecoveryCodes(String userId) {
        List<String> codes = new ArrayList<>();

        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String code = generateRandomCode();
            String hashedCode = hashCode(code);

            // Store hashed code
            storeRecoveryCode(userId, hashedCode);
            codes.add(formatCode(code)); // Display: XXXX-XXXX-XXXX
        }

        auditService.logEvent("RECOVERY_CODES_GENERATED", userId);
        return codes;
    }

    public boolean verifyRecoveryCode(String userId, String code) {
        String hashedCode = hashCode(code);

        List<String> storedCodes = getRecoveryCodes(userId);
        boolean valid = storedCodes.contains(hashedCode);

        if (valid) {
            // One-time use: delete after verification
            deleteRecoveryCode(userId, hashedCode);
            auditService.logEvent("RECOVERY_CODE_USED", userId,
                Map.of("remainingCodes", storedCodes.size() - 1));
        }

        return valid;
    }

    private String hashCode(String code) {
        return BCrypt.hashpw(code, BCrypt.gensalt(12));
    }
}
```

**User Flow:**
1. During WebAuthn registration, offer to generate recovery codes
2. Display codes once, require user to save them
3. User can use recovery code to sign in if they lose their key
4. After using recovery code, prompt to register new WebAuthn credential

#### Option 2: Backup Authenticator
```java
// Require 2 WebAuthn credentials for passwordless accounts
public void enforceBackupCredential(String userId) {
    List<WebAuthnCredential> credentials = credentialManager.getCredentials(userId);

    if (credentials.size() < 2) {
        throw new InsufficientCredentialsException(
            "Passwordless accounts must have at least 2 registered credentials");
    }
}

// Allow registering backup credential even when logged out (with email verification)
@POST
@Path("recovery/register-backup")
public Response registerBackupCredential(Map<String, Object> request) {
    String email = (String) request.get("email");
    String verificationCode = (String) request.get("verificationCode");

    // Verify email code
    if (!emailVerificationService.verify(email, verificationCode)) {
        return errorResponse("INVALID_CODE", "Verification code invalid", 400);
    }

    UserModel user = session.users().getUserByEmail(realm, email);

    // Generate registration challenge
    return Response.ok(registrationService.generateChallenge(
        user.getId(), "Backup Key", "passwordless")).build();
}
```

#### Option 3: Trusted Device Recovery
```java
// Allow recovery from previously trusted device
public class TrustedDeviceRecovery {

    public String initiateRecovery(String userId, String deviceId) {
        // Check if device was previously trusted
        DeviceRecord device = deviceManager.getDevice(userId, deviceId);

        if (device == null || !device.isTrusted()) {
            throw new UntrustedDeviceException("Device not recognized");
        }

        // Send verification code to user's email
        String code = generateVerificationCode();
        emailService.sendRecoveryCode(user.getEmail(), code);

        return "verification_code_sent";
    }

    public RecoveryToken verifyAndGenerateToken(String userId, String code) {
        if (!verificationService.verify(userId, code)) {
            throw new InvalidCodeException("Code invalid or expired");
        }

        // Generate temporary recovery token
        RecoveryToken token = RecoveryToken.builder()
            .userId(userId)
            .expiresAt(Instant.now().plusMinutes(15))
            .allowedOperation("credential_registration")
            .build();

        return token;
    }
}
```

### Acceptance Criteria
- [ ] Generate 10 one-time recovery codes during registration
- [ ] Support email-based recovery code delivery
- [ ] Require 2+ credentials for passwordless accounts
- [ ] Log all recovery code generation and usage
- [ ] Notify user via email when recovery code is used
- [ ] Rate limit recovery attempts (max 5 per hour)

### UX Considerations
- **Download Recovery Codes**: Offer download as PDF or text file
- **Print Option**: Provide print-friendly format
- **Warning**: Clearly communicate importance of saving codes
- **Regeneration**: Allow users to regenerate codes (invalidates old ones)

---

## Feature 4: Cross-Platform Credential Roaming

**Business Value**: HIGH | **Technical Complexity**: MEDIUM | **Timeline**: 4-6 weeks

### Description
Enable users to use credentials registered on one device (iPhone) on another device (MacBook) via cloud keychain synchronization.

### Technical Foundation
Built on FIDO2 "backup eligible" (BE) and "backup state" (BS) flags:
- **BE=1**: Credential can be backed up to cloud
- **BS=1**: Credential is currently synced to cloud

### Supported Platforms
| Platform | Cloud Provider | Sync Technology |
|----------|---------------|-----------------|
| iOS/macOS | iCloud Keychain | Apple Keychain |
| Android/Chrome | Google Password Manager | Chrome Sync |
| Windows Hello | Microsoft Account | Windows Hello |

### Implementation

#### 1. Detect Backup Capabilities
```java
public class BackupEligibilityService {

    public void processAuthenticatorFlags(WebAuthnCredential credential,
            AuthenticatorData authData) {

        byte flags = authData.getFlags();

        boolean backupEligible = (flags & 0x08) != 0; // Bit 3
        boolean backupState = (flags & 0x10) != 0;    // Bit 4

        BackupEligibility backup = new BackupEligibility();
        backup.setEligible(backupEligible);
        backup.setState(backupState);

        credential.setBackup(backup);

        LOG.infof("Credential %s: BE=%b, BS=%b",
            credential.getCredentialId(), backupEligible, backupState);

        // Track synced credential groups
        if (backupEligible && backupState) {
            trackSyncedCredential(credential);
        }
    }

    private void trackSyncedCredential(WebAuthnCredential credential) {
        // Credentials with same AAGUID and backup=true are synced
        String aaguid = credential.getMetadata().getAaguid();
        String userId = credential.getUserId();

        List<WebAuthnCredential> syncGroup = credentialManager
            .findCredentialsByAAGUID(userId, aaguid)
            .stream()
            .filter(c -> c.getBackup().isState())
            .collect(Collectors.toList());

        if (syncGroup.size() > 1) {
            LOG.infof("Credential is part of sync group: %d credentials", syncGroup.size());

            // Assign sync group ID
            String syncGroupId = generateSyncGroupId(aaguid, userId);
            for (WebAuthnCredential c : syncGroup) {
                c.setSyncGroupId(syncGroupId);
                credentialManager.updateCredential(userId, c);
            }
        }
    }
}
```

#### 2. Credential Lifecycle Management
```java
// Handle sign count across synced credentials
public class SyncedCredentialManager {

    public void updateSignCountForSyncGroup(WebAuthnCredential credential, long newSignCount) {
        String syncGroupId = credential.getSyncGroupId();

        if (syncGroupId == null) {
            // Not synced, update normally
            credential.updateSignCount(newSignCount);
            return;
        }

        // Update sign count for entire sync group
        List<WebAuthnCredential> syncGroup = credentialManager
            .findCredentialsBySyncGroupId(credential.getUserId(), syncGroupId);

        for (WebAuthnCredential c : syncGroup) {
            // Use max sign count across group
            long currentMax = c.getSignCount();
            c.setSignCount(Math.max(currentMax, newSignCount));
            credentialManager.updateCredential(credential.getUserId(), c);
        }
    }

    public void handleCredentialDeletion(String userId, String credentialId) {
        WebAuthnCredential credential = credentialManager.findCredentialById(credentialId);

        if (credential.getSyncGroupId() != null) {
            // Warn user: deleting synced credential affects other devices
            LOG.warn("User deleting synced credential: {}", credentialId);

            // Optionally: delete entire sync group
            deleteSyncGroup(userId, credential.getSyncGroupId());
        } else {
            credentialManager.deleteCredential(userId, credentialId);
        }
    }
}
```

#### 3. User Interface Indicators
```java
@GET
@Path("credentials")
public Response listCredentialsWithSyncInfo() {
    String userId = extractUserIdFromToken();
    List<WebAuthnCredential> credentials = credentialManager.getCredentials(userId);

    List<CredentialInfo> enriched = credentials.stream()
        .map(cred -> {
            CredentialInfo info = new CredentialInfo(cred);

            // Add sync indicator
            if (cred.getBackup().isState()) {
                info.setSyncStatus("synced");
                info.setSyncProvider(detectProvider(cred.getMetadata().getAaguid()));
            } else if (cred.getBackup().isEligible()) {
                info.setSyncStatus("eligible");
            } else {
                info.setSyncStatus("device-bound");
            }

            return info;
        })
        .collect(Collectors.toList());

    return Response.ok(enriched).build();
}

private String detectProvider(String aaguid) {
    // Map AAGUID to provider
    return switch (aaguid) {
        case "00000000-0000-0000-0000-000000000000" -> "iCloud Keychain";
        case "ea9b8d66-4d01-1d21-3ce4-b6b48cb575d4" -> "Google Password Manager";
        default -> "Unknown";
    };
}
```

### Acceptance Criteria
- [ ] Detect backup eligible (BE) and backup state (BS) flags
- [ ] Group synced credentials by AAGUID + backup state
- [ ] Handle sign count updates for credential groups
- [ ] Display sync status in credential list UI
- [ ] Warn users when deleting synced credentials
- [ ] Support "sign out all devices" functionality

### User Experience
```
Credential List UI:
┌─────────────────────────────────────────┐
│ My Credentials                          │
├─────────────────────────────────────────┤
│ 🔑 iPhone (Synced via iCloud)          │
│    Registered: Jan 15, 2025             │
│    Last used: 2 hours ago               │
│    [Delete] [Rename]                    │
├─────────────────────────────────────────┤
│ 🔐 YubiKey 5 NFC (Device-bound)        │
│    Registered: Dec 1, 2024              │
│    Last used: Yesterday                 │
│    [Delete] [Rename]                    │
└─────────────────────────────────────────┘
```

---

## Feature 5: User Presence vs User Verification

**Business Value**: MEDIUM | **Technical Complexity**: LOW | **Timeline**: 2 weeks

### Description
Distinguish between user presence (UP) and user verification (UV) to support different security levels for different operations.

### Use Cases
| Operation | Requirement | Reason |
|-----------|-------------|--------|
| Low-value transaction | User Presence (UP) | Quick, no biometric needed |
| High-value transaction | User Verification (UV) | PIN/biometric required |
| Account settings change | User Verification (UV) | Prevent unauthorized changes |
| Daily login | User Presence (UP) | Convenience |

### Implementation

#### 1. Configurable User Verification Requirement
```java
public enum UserVerificationRequirement {
    REQUIRED,   // Must perform UV (PIN/biometric)
    PREFERRED,  // Prefer UV but accept UP
    DISCOURAGED // Accept UP only
}

// Per-operation configuration
@POST
@Path("auth/challenge")
public Response generateChallengeWithUVRequirement(Map<String, Object> request) {
    String username = (String) request.get("username");
    String operation = (String) request.get("operation"); // "login", "high_value_tx", etc.

    UserVerificationRequirement uvr = determineUVRequirement(operation);

    Map<String, Object> challenge = authService.generateChallenge(username);
    challenge.put("userVerification", uvr.name().toLowerCase());

    return Response.ok(challenge).build();
}

private UserVerificationRequirement determineUVRequirement(String operation) {
    return switch (operation) {
        case "high_value_transaction", "account_settings" -> UserVerificationRequirement.REQUIRED;
        case "login", "low_value_transaction" -> UserVerificationRequirement.PREFERRED;
        default -> UserVerificationRequirement.PREFERRED;
    };
}
```

#### 2. Verify UV Flag in Assertion
```java
private void validateUserVerification(AuthenticatorData authData,
        UserVerificationRequirement requirement) throws InvalidCredentialException {

    byte flags = authData.getFlags();
    boolean userPresent = (flags & 0x01) != 0;
    boolean userVerified = (flags & 0x04) != 0;

    if (requirement == UserVerificationRequirement.REQUIRED && !userVerified) {
        throw new InvalidCredentialException(
            "User verification required but not performed");
    }

    if (!userPresent) {
        throw new InvalidCredentialException("User presence not confirmed");
    }

    LOG.debugf("UV requirement: %s, UP: %b, UV: %b",
        requirement, userPresent, userVerified);
}
```

#### 3. Step-Up Authentication
```java
// Require UV for sensitive operations even if logged in with UP
@POST
@Path("account/change-email")
public Response changeEmail(Map<String, Object> request) {
    String userId = extractUserIdFromToken();
    boolean sessionHasUV = checkSessionUVFlag(userId);

    if (!sessionHasUV) {
        // Require step-up authentication with UV
        return Response.status(401)
            .entity(Map.of(
                "error", "UV_REQUIRED",
                "message", "This operation requires user verification",
                "stepUpChallenge", generateStepUpChallenge(userId)
            ))
            .build();
    }

    // Proceed with email change
    return processEmailChange(userId, request);
}
```

### Acceptance Criteria
- [ ] Support REQUIRED, PREFERRED, DISCOURAGED UV modes
- [ ] Verify UV flag in authenticator data
- [ ] Implement step-up authentication for sensitive operations
- [ ] Store UV status in user session
- [ ] Log UV requirement and actual UV performed

---

## Feature 6: Transaction Confirmation

**Business Value**: HIGH | **Technical Complexity**: MEDIUM | **Timeline**: 4 weeks

### Description
Display transaction details on authenticator screen for user confirmation before signing, preventing phishing attacks.

### W3C Spec: Transaction Authorization
Uses `txAuthSimple` or `txAuthGeneric` extensions.

### Implementation

```java
@POST
@Path("transaction/authorize")
public Response authorizeTransaction(TransactionRequest request) {
    String userId = extractUserIdFromToken();
    String transactionDetails = request.getTransactionDetails();

    // Generate challenge with transaction binding
    byte[] challenge = generateRandomChallenge();
    String sessionId = storeChallenge(userId, challenge, "transaction", 300);

    Map<String, Object> options = new HashMap<>();
    options.put("sessionId", sessionId);
    options.put("challenge", Base64Util.encodeToString(challenge));

    // Transaction authorization extension
    Map<String, Object> extensions = new HashMap<>();
    extensions.put("txAuthSimple", transactionDetails); // Display on authenticator

    options.put("extensions", extensions);

    return Response.ok(options).build();
}

// Verify transaction confirmation
public void verifyTransactionAuthorization(String sessionId,
        Map<String, Object> clientExtensionResults) {

    String confirmedTx = (String) clientExtensionResults.get("txAuthSimple");

    ChallengeData challengeData = credentialManager.getChallenge(sessionId);
    String originalTx = challengeData.getTransactionDetails();

    if (!confirmedTx.equals(originalTx)) {
        throw new InvalidCredentialException("Transaction details mismatch");
    }

    LOG.infof("Transaction authorized: %s", confirmedTx);
}
```

### Use Case: Wire Transfer
```
Authenticator Display:
┌─────────────────────────┐
│ Authorize Transaction?  │
│                         │
│ Transfer $5,000.00      │
│ To: John Doe            │
│ Account: ****1234       │
│                         │
│ [Approve] [Deny]        │
└─────────────────────────┘
```

---

## Summary Table

| Feature | Business Value | Complexity | Timeline | Dependencies |
|---------|---------------|------------|----------|--------------|
| Conditional UI | HIGH | MEDIUM | 4-6 weeks | Resident Keys |
| Attestation Verification | HIGH | HIGH | 6-8 weeks | None |
| Account Recovery | CRITICAL | MEDIUM | 3-4 weeks | None |
| Credential Roaming | HIGH | MEDIUM | 4-6 weeks | None |
| UV vs UP | MEDIUM | LOW | 2 weeks | None |
| Transaction Confirmation | HIGH | MEDIUM | 4 weeks | None |

**Total Estimated Timeline**: 23-32 weeks (6-8 months)

---

**Document Version**: 1.0
**Last Updated**: 2025-11-30
**Next Review**: 2025-12-15
