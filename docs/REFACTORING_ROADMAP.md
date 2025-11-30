# WebAuthn Extension Refactoring Roadmap

## Executive Summary

This document outlines a comprehensive refactoring roadmap for the Keycloak WebAuthn extension. The current implementation provides a solid foundation with TDD practices, but several areas need enhancement for production readiness.

**Current State:**
- 38 Java files (30 main + 8 test files)
- Core services implemented: Registration, Authentication, Credential Management, Token Service
- Test coverage: Basic unit tests with Testcontainers integration
- Technology stack: WebAuthn4J 0.29.3, Jackson 2.18.2, BouncyCastle 1.79

**Key Findings:**
1. ✅ **Strengths**: Clean architecture, TDD approach, comprehensive exception handling
2. ⚠️ **Technical Debt**: Origin validation TODOs, incomplete COSE key parsing, simplified token refresh
3. 🔴 **Critical Gaps**: Attestation verification, resident key support, production security hardening

---

## Phase 1: Quick Wins (1-2 Weeks)

### Priority: HIGH | Effort: LOW | Risk: LOW

### 1.1 Origin Validation Enhancement
**Problem**: Currently logs origin but doesn't validate against whitelist
**Files**: `WebAuthnRegistrationService.java:218`, `WebAuthnAuthenticationService.java:221`

**Implementation:**
```java
// Add to WebAuthnCredentialManager or new OriginValidator service
private static final Set<String> ALLOWED_ORIGINS = Set.of(
    "https://myapp.example.com",
    "https://staging.example.com"
);

private void validateOrigin(String origin) throws ValidationException {
    if (!ALLOWED_ORIGINS.contains(origin)) {
        LOG.warnf("Origin validation failed: %s", origin);
        throw new ValidationException("Origin not allowed: " + origin);
    }
}
```

**Expected Outcome:**
- Prevent cross-origin attacks
- Configurable origin whitelist via realm attributes
- Audit logging for failed validations

---

### 1.2 Complete Token Service Implementation
**Problem**: Token refresh method is stubbed (TokenService.java:212-234)
**Files**: `TokenService.java`

**Missing Components:**
1. Refresh token parsing and validation
2. Token rotation logic
3. Refresh token expiry checks
4. Revocation list integration

**Implementation Plan:**
```java
public Map<String, Object> refreshTokens(String refreshToken, ClientModel client, String scope) {
    // 1. Parse and verify refresh token
    RefreshToken token = tokenManager.verifyRefreshToken(realm, refreshToken);

    // 2. Check expiration and revocation
    if (token.isExpired() || isRevoked(token)) {
        throw new TokenExpiredException("Refresh token invalid");
    }

    // 3. Validate client binding
    if (!token.getAudience().contains(client.getClientId())) {
        throw new TokenValidationException("Client mismatch");
    }

    // 4. Generate new token set with rotation
    return tokenManager.refreshAccessToken(session, realm, client, token, scope);
}
```

---

### 1.3 Enhance COSE Key Parsing
**Problem**: Simplified placeholder in `WebAuthnAuthenticationService.java:371-379`
**Current Code:**
```java
private PublicKey decodePublicKey(byte[] coseKeyBytes) throws Exception {
    Map<Integer, Object> coseKey = cborConverter.readValue(coseKeyBytes, Map.class);
    // Placeholder - should decode based on key type
    KeyFactory keyFactory = KeyFactory.getInstance("EC");
    X509EncodedKeySpec keySpec = new X509EncodedKeySpec(coseKeyBytes);
    return keyFactory.generatePublic(keySpec);
}
```

**Enhanced Implementation:**
```java
private PublicKey decodePublicKey(byte[] coseKeyBytes) throws Exception {
    Map<Integer, Object> coseKey = cborConverter.readValue(coseKeyBytes, Map.class);

    // COSE key type (1 = kty)
    int kty = (Integer) coseKey.get(1);

    switch (kty) {
        case 2: // EC2 (Elliptic Curve)
            return decodeEC2PublicKey(coseKey);
        case 3: // RSA
            return decodeRSAPublicKey(coseKey);
        default:
            throw new InvalidCredentialException("Unsupported key type: " + kty);
    }
}

private PublicKey decodeEC2PublicKey(Map<Integer, Object> coseKey) throws Exception {
    // -1 = crv (curve), -2 = x, -3 = y
    int curve = (Integer) coseKey.get(-1);
    byte[] x = (byte[]) coseKey.get(-2);
    byte[] y = (byte[]) coseKey.get(-3);

    String curveName = getCurveName(curve); // P-256, P-384, P-521
    ECParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec(curveName);
    ECPoint point = ecSpec.getCurve().createPoint(
        new BigInteger(1, x),
        new BigInteger(1, y)
    );

    KeyFactory keyFactory = KeyFactory.getInstance("EC");
    return keyFactory.generatePublic(new ECPublicKeySpec(point, ecSpec));
}
```

---

### 1.4 User Handle Extraction
**Problem**: `userHandle` parameter accepted but not used in `verifyAssertion`
**Files**: `WebAuthnAuthenticationService.java:106, 111`

**Implementation:**
```java
// If userHandle provided, verify it matches the authenticated user
if (userHandle != null && !userHandle.isEmpty()) {
    byte[] userHandleBytes = Base64Util.decode(userHandle);
    String userId = new String(userHandleBytes, StandardCharsets.UTF_8);

    if (!userId.equals(challengeData.getUserId())) {
        throw new InvalidCredentialException("User handle mismatch");
    }
}
```

---

### 1.5 Challenge Cache Optimization
**Problem**: In-memory cache with periodic cleanup; no distributed support
**Files**: `WebAuthnCredentialManager.java:31, 318-334`

**Improvements:**
1. Move to Keycloak's Infinispan cache for clustering
2. Add cache eviction policies
3. Implement cache metrics

```java
// Replace static ConcurrentHashMap with Infinispan
private Cache<String, ChallengeData> getChallengeCache() {
    return session.getProvider(CacheProvider.class)
        .getCache("webauthn-challenges", String.class, ChallengeData.class);
}

public String storeChallenge(String userId, String challenge, String type, int ttlSeconds) {
    String sessionId = UUID.randomUUID().toString();
    ChallengeData data = new ChallengeData(userId, challenge, type,
        Instant.now().plusSeconds(ttlSeconds));

    getChallengeCache().put(sessionId, data, ttlSeconds, TimeUnit.SECONDS);
    return sessionId;
}
```

---

## Phase 2: Major Improvements (2-4 Weeks)

### Priority: HIGH | Effort: MEDIUM | Risk: MEDIUM

### 2.1 Attestation Statement Verification
**Problem**: Attestation object parsed but not verified
**Impact**: Cannot validate authenticator authenticity

**Implementation Scope:**
1. Support attestation formats: `packed`, `tpm`, `android-key`, `android-safetynet`, `fido-u2f`, `none`
2. Verify attestation certificate chains
3. Integrate FIDO Metadata Service (MDS)
4. Store attestation results

**Code Structure:**
```java
// New service: AttestationVerificationService.java
public class AttestationVerificationService {

    public AttestationVerificationResult verifyAttestation(
            AttestationObject attestationObject,
            byte[] clientDataHash,
            AttestationPreferences preferences) {

        String format = attestationObject.getFormat();

        switch (format) {
            case "packed":
                return verifyPackedAttestation(attestationObject, clientDataHash);
            case "tpm":
                return verifyTPMAttestation(attestationObject, clientDataHash);
            case "android-safetynet":
                return verifySafetyNetAttestation(attestationObject, clientDataHash);
            case "fido-u2f":
                return verifyFIDOU2FAttestation(attestationObject, clientDataHash);
            case "none":
                return AttestationVerificationResult.none();
            default:
                throw new UnsupportedAttestationFormatException(format);
        }
    }

    private AttestationVerificationResult verifyPackedAttestation(
            AttestationObject attestationObject, byte[] clientDataHash) {
        // 1. Extract attestation statement
        Map<String, Object> attStmt = attestationObject.getAttestationStatement();

        // 2. Verify signature
        byte[] sig = (byte[]) attStmt.get("sig");
        int alg = (Integer) attStmt.get("alg");

        // 3. Verify certificate chain
        List<byte[]> x5c = (List<byte[]>) attStmt.get("x5c");
        if (x5c != null && !x5c.isEmpty()) {
            return verifyFullAttestation(x5c, sig, alg,
                attestationObject.getAuthenticatorData(), clientDataHash);
        }

        // 4. Self-attestation (no certificate chain)
        return verifySelfAttestation(sig, alg,
            attestationObject.getAuthenticatorData(), clientDataHash);
    }
}
```

**Configuration:**
- Realm-level attestation preferences (none, indirect, direct)
- FIDO MDS integration for authenticator metadata
- Certificate pinning for enterprise authenticators

---

### 2.2 Advanced Error Handling & Recovery
**Current State**: Basic exception hierarchy (5 exception types)
**Enhancement**: Rich error context, retry mechanisms, user-friendly messages

**New Exception Architecture:**
```java
// Enhanced WebAuthnException with context
public class WebAuthnException extends Exception {
    private final String errorCode;
    private final int httpStatusCode;
    private final Map<String, Object> errorContext; // NEW
    private final boolean retryable; // NEW

    public WebAuthnException(String errorCode, String message,
            int httpStatusCode, Map<String, Object> context, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatusCode = httpStatusCode;
        this.errorContext = context;
        this.retryable = retryable;
    }
}

// Usage example
throw new WebAuthnException(
    "ATTESTATION_VERIFICATION_FAILED",
    "Unable to verify authenticator attestation",
    400,
    Map.of(
        "format", attestationFormat,
        "reason", "certificate_chain_invalid",
        "authenticatorAAGUID", aaguid
    ),
    false // Not retryable
);
```

**Error Recovery Strategies:**
1. Automatic challenge regeneration on expiry
2. Graceful fallback to self-attestation
3. Partial credential registration rollback
4. User notification service integration

---

### 2.3 Comprehensive Audit Logging
**Problem**: Limited logging; no structured audit trail
**Files**: All service classes

**Implementation:**
```java
// New service: WebAuthnAuditService.java
public class WebAuthnAuditService {

    public void logRegistrationAttempt(String userId, String credentialId,
            RegistrationResult result, Map<String, Object> context) {

        AuditEvent event = new AuditEvent()
            .setType("WEBAUTHN_REGISTRATION")
            .setUserId(userId)
            .setRealmId(realm.getId())
            .setTimestamp(Instant.now())
            .setResult(result.isSuccess() ? "SUCCESS" : "FAILURE")
            .setDetails(Map.of(
                "credentialId", credentialId,
                "credentialType", context.get("type"),
                "authenticatorAAGUID", context.get("aaguid"),
                "attestationFormat", context.get("attestationFormat"),
                "errorCode", result.getErrorCode(),
                "ipAddress", session.getContext().getConnection().getRemoteAddr(),
                "userAgent", context.get("userAgent")
            ));

        session.getProvider(AuditProvider.class).logEvent(event);
    }
}
```

**Audit Events:**
- Registration attempts (success/failure)
- Authentication attempts (success/failure)
- Credential deletions
- Challenge generation
- Token generation
- Sign count anomalies (potential clones)
- Origin validation failures

---

### 2.4 Database Query Optimization
**Problem**: Linear search through all users for credential lookup
**Files**: `WebAuthnCredentialManager.java:133-161`

**Current Inefficiency:**
```java
// Searches ALL users with webauthn.credentials attribute
List<UserModel> users = session.users()
    .searchForUserByUserAttributeStream(realm, CREDENTIAL_ATTRIBUTE_KEY, null)
    .toList();

for (UserModel user : users) {
    List<WebAuthnCredential> credentials = getCredentials(user.getId());
    // Linear search through each user's credentials
}
```

**Optimization Strategy:**
1. Create dedicated database table for WebAuthn credentials
2. Index on `credential_id` for O(1) lookup
3. Denormalize frequently accessed fields
4. Implement caching layer

**New Schema:**
```sql
CREATE TABLE webauthn_credentials (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_entity(id) ON DELETE CASCADE,
    credential_id VARCHAR(512) UNIQUE NOT NULL,
    credential_public_key TEXT NOT NULL,
    attestation_object TEXT,
    sign_count BIGINT DEFAULT 0,
    credential_type VARCHAR(50) DEFAULT 'public-key',
    type VARCHAR(50) DEFAULT 'passwordless',
    transport_hints TEXT[], -- JSON array
    name VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW(),
    last_used_at TIMESTAMP,
    aaguid VARCHAR(100),
    backup_eligible BOOLEAN DEFAULT FALSE,
    backup_state BOOLEAN DEFAULT FALSE,
    INDEX idx_credential_id (credential_id),
    INDEX idx_user_id (user_id),
    INDEX idx_type (type)
);
```

**JPA Entity:**
```java
@Entity
@Table(name = "webauthn_credentials")
public class WebAuthnCredentialEntity {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "credential_id", unique = true, nullable = false, length = 512)
    private String credentialId;

    // ... other fields

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private UserEntity user;
}
```

**Performance Improvements:**
- Credential lookup: O(n) → O(1) (1000x faster for 1000 users)
- Supports pagination for credential lists
- Efficient querying by type, AAGUID, transport hints
- Better database indexing and query planning

---

## Phase 3: New Features (4-8 Weeks)

### Priority: MEDIUM | Effort: HIGH | Risk: MEDIUM

### 3.1 Resident Key (Discoverable Credentials) Support
**Business Value**: Enable passwordless login without username
**Technical Complexity**: HIGH

**Requirements:**
1. Set `requireResidentKey: true` in registration options
2. Store user handle in credential
3. Support resident key discovery in authentication
4. Handle multiple resident keys per authenticator

**Implementation:**
```java
// WebAuthnRegistrationService enhancement
private Map<String, Object> buildRegistrationChallengeResponse(..., boolean requireResidentKey) {
    Map<String, Object> authenticatorSelection = new HashMap<>();
    authenticatorSelection.put("requireResidentKey", requireResidentKey);
    authenticatorSelection.put("residentKey", requireResidentKey ? "required" : "preferred");
    authenticatorSelection.put("userVerification", "required"); // UV required for resident keys

    response.put("authenticatorSelection", authenticatorSelection);

    // User info MUST include user.id for resident keys
    Map<String, String> userInfo = new HashMap<>();
    userInfo.put("id", Base64Util.encodeToString(user.getId().getBytes())); // User handle
    userInfo.put("name", user.getUsername());
    userInfo.put("displayName", user.getFirstName() + " " + user.getLastName());
    response.put("user", userInfo);

    return response;
}

// New authentication flow for discoverable credentials
public Map<String, Object> generateDiscoveryChallenge() {
    // No allowCredentials - authenticator discovers resident keys
    byte[] challenge = generateRandomChallenge();
    String sessionId = storeChallenge(null, challenge, "discovery", 300); // No userId yet

    return Map.of(
        "sessionId", sessionId,
        "challenge", Base64Util.encodeToString(challenge),
        "rpId", getRpId(),
        "userVerification", "required",
        "timeout", 60000
    );
}
```

**Database Schema Addition:**
```java
@Column(name = "user_handle")
private String userHandle; // Base64url encoded user ID

@Column(name = "resident_key")
private boolean residentKey = false;
```

---

### 3.2 Multi-Device Credential Synchronization
**Use Case**: Users register on mobile, use on desktop
**Approach**: Backup/restore via cloud keychain (iCloud, Google Password Manager)

**Architecture:**
```java
public class CredentialSyncService {

    // Check backup eligibility during registration
    public void processBackupFlags(WebAuthnCredential credential,
            AuthenticatorData authData) {

        byte flags = authData.getFlags();
        boolean backupEligible = (flags & 0x08) != 0; // BE flag (bit 3)
        boolean backupState = (flags & 0x10) != 0;    // BS flag (bit 4)

        credential.getBackup().setEligible(backupEligible);
        credential.getBackup().setState(backupState);

        if (backupEligible && backupState) {
            LOG.infof("Credential is synced: %s", credential.getCredentialId());
            // Track synced credential group
            linkSyncedCredentials(credential);
        }
    }

    // Link credentials that are synced together
    private void linkSyncedCredentials(WebAuthnCredential credential) {
        String aaguid = credential.getMetadata().getAaguid();
        String userId = credential.getUserId();

        // Find other credentials with same AAGUID and backup=true
        List<WebAuthnCredential> syncGroup = credentialManager
            .findCredentialsByAAGUID(userId, aaguid)
            .stream()
            .filter(c -> c.getBackup().isState())
            .collect(Collectors.toList());

        // Store sync group ID for coordinated updates
        String syncGroupId = generateSyncGroupId(aaguid, userId);
        credential.setSyncGroupId(syncGroupId);
    }
}
```

**User Experience:**
- Indicate synced credentials in credential list
- Warn users when deleting synced credentials
- Support "sign out of all devices" functionality

---

### 3.3 Advanced Analytics & Reporting
**Metrics to Track:**
1. Registration success rate by authenticator type
2. Authentication latency percentiles
3. Credential usage patterns
4. Sign count anomaly detection
5. Platform/browser compatibility matrix

**Implementation:**
```java
@Path("analytics")
@Produces(MediaType.APPLICATION_JSON)
public class WebAuthnAnalyticsResource {

    @GET
    @Path("registration-stats")
    public Response getRegistrationStats(
            @QueryParam("from") String fromDate,
            @QueryParam("to") String toDate) {

        Map<String, Object> stats = analyticsService.getRegistrationStats(
            parseDate(fromDate), parseDate(toDate));

        return Response.ok(stats).build();
    }

    @GET
    @Path("authenticator-distribution")
    public Response getAuthenticatorDistribution() {
        // Group credentials by AAGUID
        Map<String, Long> distribution = credentialManager
            .getAllCredentials()
            .stream()
            .collect(Collectors.groupingBy(
                c -> c.getMetadata().getAaguid(),
                Collectors.counting()
            ));

        // Enrich with authenticator metadata from FIDO MDS
        Map<String, AuthenticatorInfo> enriched = enrichWithMetadata(distribution);

        return Response.ok(enriched).build();
    }
}
```

**Dashboard Widgets:**
- Real-time registration/authentication rate
- Top 10 authenticators by usage
- Error distribution chart
- Geographic distribution (if IP tracking enabled)
- Browser/platform compatibility matrix

---

### 3.4 Credential Management UI
**Scope**: Admin console and user account UI
**Features:**
1. List all registered credentials with metadata
2. Rename credentials
3. Delete credentials
4. View last used timestamp
5. Flag suspicious activity (sign count anomalies)

**REST API Extensions:**
```java
@PUT
@Path("credentials/{credentialId}")
@Consumes(MediaType.APPLICATION_JSON)
public Response updateCredential(
        @PathParam("credentialId") String credentialId,
        Map<String, Object> updates) {

    String userId = extractUserIdFromToken();
    WebAuthnCredential credential = credentialManager.findCredentialById(credentialId);

    if (!credential.getUserId().equals(userId)) {
        return Response.status(403).build();
    }

    // Allow updating: name, enabled status
    if (updates.containsKey("name")) {
        credential.getMetadata().setName((String) updates.get("name"));
    }

    credentialManager.updateCredential(userId, credential);
    return Response.ok(credential).build();
}

@GET
@Path("credentials/{credentialId}/activity")
public Response getCredentialActivity(@PathParam("credentialId") String credentialId) {
    List<AuditEvent> activity = auditService.getCredentialActivity(credentialId);
    return Response.ok(activity).build();
}
```

---

## Phase 4: Production Hardening (Ongoing)

### 4.1 Security Hardening Checklist

#### Origin Validation
- [ ] Implement strict origin whitelist
- [ ] Support wildcards for development (*.localhost)
- [ ] Log all origin validation failures
- [ ] Rate limit by origin

#### Challenge Security
- [ ] Use cryptographically secure random (✅ Already using SecureRandom)
- [ ] Implement challenge replay protection
- [ ] Short TTL (5 minutes default) ✅
- [ ] One-time use enforcement ✅ (deleted after verification)

#### Credential Storage
- [ ] Encrypt sensitive fields (public key, attestation object)
- [ ] Implement field-level encryption
- [ ] Regular security audits
- [ ] PII data handling compliance (GDPR)

#### Rate Limiting
```java
public class RateLimitService {
    private final Cache<String, AtomicInteger> rateLimitCache;

    public void checkRateLimit(String userId, String operation) throws RateLimitException {
        String key = userId + ":" + operation;
        AtomicInteger attempts = rateLimitCache.computeIfAbsent(key,
            k -> new AtomicInteger(0));

        if (attempts.incrementAndGet() > getLimit(operation)) {
            throw new RateLimitException("Rate limit exceeded for " + operation);
        }
    }

    private int getLimit(String operation) {
        return switch (operation) {
            case "registration" -> 10; // 10 per hour
            case "authentication" -> 30; // 30 per hour
            case "challenge_generation" -> 60; // 60 per hour
            default -> 100;
        };
    }
}
```

#### DOS Protection
- Request size limits (max 10KB for attestation objects)
- Concurrent request limits per user
- Challenge cache size limits (max 10,000)
- Credential count limits per user (max 20)

---

### 4.2 Monitoring & Alerting

**Key Metrics:**
1. **Availability**: Registration/authentication endpoint uptime
2. **Latency**: p50, p95, p99 response times
3. **Error Rate**: 4xx/5xx errors per minute
4. **Security**: Failed authentication attempts, sign count anomalies

**Alert Rules:**
```yaml
alerts:
  - name: HighAuthenticationFailureRate
    condition: rate(webauthn_auth_failures[5m]) > 10
    severity: warning
    message: "Unusual authentication failure rate detected"

  - name: SignCountAnomaly
    condition: webauthn_sign_count_decreases > 0
    severity: critical
    message: "Potential authenticator clone detected"

  - name: HighRegistrationLatency
    condition: webauthn_registration_p95 > 3000
    severity: warning
    message: "Registration latency exceeds threshold"
```

**Integration:**
- Prometheus metrics export
- Grafana dashboard
- PagerDuty/Opsgenie alerts
- Slack/Teams notifications

---

### 4.3 Compliance & Certification

#### FIDO2 Certification
**Requirements:**
1. Pass FIDO2 Conformance Testing
2. Support all FIDO2 mandatory features
3. Security audit by FIDO Alliance

**Testing Tools:**
- FIDO2 Conformance Testing Suite
- WebAuthn Tester browser extension
- Android SafetyNet validation

#### GDPR Compliance
**Data Handling:**
- [ ] Document data processing purposes
- [ ] Implement data export (user credentials)
- [ ] Implement right to deletion
- [ ] Minimize PII storage
- [ ] Encrypt credentials at rest
- [ ] Audit logging for data access

**User Rights Implementation:**
```java
@GET
@Path("data-export")
public Response exportUserData() {
    String userId = extractUserIdFromToken();

    Map<String, Object> userData = new HashMap<>();
    userData.put("credentials", credentialManager.getCredentials(userId));
    userData.put("auditLog", auditService.getUserAuditLog(userId));

    return Response.ok(userData)
        .header("Content-Disposition", "attachment; filename=webauthn-data.json")
        .build();
}

@DELETE
@Path("data-deletion")
public Response deleteUserData() {
    String userId = extractUserIdFromToken();

    // Delete all credentials
    List<WebAuthnCredential> credentials = credentialManager.getCredentials(userId);
    for (WebAuthnCredential cred : credentials) {
        credentialManager.deleteCredential(userId, cred.getCredentialId());
    }

    // Anonymize audit logs
    auditService.anonymizeUserLogs(userId);

    return Response.noContent().build();
}
```

---

## Success Metrics

### Technical Metrics
- **Test Coverage**: 80%+ (current: ~27%)
- **Registration Success Rate**: >95%
- **Authentication Latency**: p95 < 500ms
- **Zero critical security vulnerabilities**
- **Support 10,000+ concurrent users**

### Business Metrics
- **User Adoption**: 50%+ of users enable WebAuthn
- **Support Ticket Reduction**: 30% fewer password-related tickets
- **Account Takeover Prevention**: 99%+ reduction in ATO incidents

---

## Risk Mitigation

### Technical Risks
| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Breaking changes in WebAuthn4J | High | Low | Pin versions, test upgrades in staging |
| Database migration failures | High | Medium | Thorough testing, rollback plan, blue-green deployment |
| Performance degradation | Medium | Medium | Load testing, caching, database indexing |
| Browser compatibility | Medium | High | Progressive enhancement, fallback flows |

### Operational Risks
| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Authenticator vendor issues | Medium | Low | Support multiple authenticator types |
| User credential loss | High | Medium | Backup codes, account recovery flow |
| Certificate expiry | Low | Medium | Automated certificate monitoring |

---

## Timeline Summary

```
Week 1-2:  ✅ Origin validation, COSE parsing, token refresh
Week 3-4:  🔧 Attestation verification, audit logging
Week 5-8:  📊 Database optimization, analytics
Week 9-12: 🚀 Resident keys, credential sync
Week 13+:  🛡️ Production hardening, monitoring, compliance
```

**Next Steps:**
1. Review and approve roadmap with stakeholders
2. Create JIRA tickets for Phase 1 items
3. Set up development environment
4. Begin implementation with TDD approach

---

**Document Version**: 1.0
**Last Updated**: 2025-11-30
**Owner**: Development Team
**Reviewers**: Security Team, Product Management
