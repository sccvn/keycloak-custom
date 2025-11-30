# WebAuthn Extension Design Decisions

This document records the architectural decisions, trade-offs, and rationale for the WebAuthn extension design.

## Table of Contents
1. [Storage Architecture](#storage-architecture)
2. [Challenge Management](#challenge-management)
3. [Module Organization](#module-organization)
4. [Error Handling](#error-handling)
5. [Dependency Management](#dependency-management)
6. [Security Decisions](#security-decisions)
7. [Future Considerations](#future-considerations)

---

## Storage Architecture

### Decision: Store Credentials as JSON in User Attributes

**Status**: Implemented

**Context**:
We need to store WebAuthn credentials persistently in Keycloak. Options considered:
1. JSON in user attributes
2. Custom JPA entities with separate tables
3. External storage (Redis, separate database)

**Decision**:
Store credentials as JSON-serialized list in Keycloak user attribute `webauthn.credentials`.

**Rationale**:

**Pros**:
- **Simplicity**: No schema migrations or database changes
- **Keycloak Native**: Uses existing attribute storage mechanism
- **Transactional**: Automatically handled by Keycloak's transaction management
- **Multi-realm**: Automatically scoped to realm
- **Backup/Export**: Included in Keycloak's standard export/import
- **Quick Implementation**: No JPA entity definitions or repositories needed

**Cons**:
- **Query Limitations**: Cannot efficiently search credentials across users
- **Size Limits**: User attribute storage has size constraints
- **JSON Parsing Overhead**: Deserialization on every read
- **No Relational Queries**: Cannot join with other entities
- **Scalability**: O(n) search for credential ID across users

**Trade-offs**:
- Prioritized **rapid development** and **simplicity** over **query performance**
- Acceptable for typical use case: 1-5 credentials per user
- Can migrate to JPA in future if scalability issues arise

**Alternatives Considered**:

#### Option 2: JPA Entities
```java
@Entity
@Table(name = "webauthn_credentials")
public class WebAuthnCredentialEntity {
    @Id
    private String credentialId;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private UserEntity user;

    // ... other fields
}
```

**Why Rejected**:
- Requires schema migration
- More complex implementation
- Keycloak JPA model coupling
- Not needed for initial MVP

#### Option 3: External Storage
**Why Rejected**:
- Introduces external dependency
- Additional operational complexity
- Consistency challenges (distributed transactions)
- Over-engineering for current scale

**Future Migration Path**:
1. Create `CredentialStorage` interface
2. Implement `UserAttributeCredentialStorage` (current)
3. Implement `JpaCredentialStorage` (future)
4. Add configuration to switch implementations

---

## Challenge Management

### Decision: In-Memory Challenge Cache with TTL

**Status**: Implemented

**Context**:
WebAuthn challenges must be:
- Single-use (consumed after verification)
- Time-limited (5-minute expiry)
- Associated with user and operation type

**Decision**:
Use in-memory `ConcurrentHashMap` with lazy cleanup for challenge storage.

**Rationale**:

**Pros**:
- **Performance**: O(1) lookup, no database round-trips
- **Simplicity**: No persistence layer needed
- **Automatic Cleanup**: TTL-based expiry
- **Thread-Safe**: `ConcurrentHashMap` handles concurrency

**Cons**:
- **Not Clustered**: Challenges not shared across Keycloak nodes
- **Lost on Restart**: Challenges cleared on server restart
- **Memory Usage**: Unbounded growth without cleanup

**Trade-offs**:
- Prioritized **performance** over **cluster support**
- Acceptable for current deployment (single-node or sticky sessions)
- 5-minute TTL limits memory impact

**Cluster Support Consideration**:

For multi-node deployments, challenges must be accessible across instances:

**Option 1: Sticky Sessions** (Current Recommendation)
- Configure load balancer for sticky sessions
- Client returns to same node for verification
- No code changes needed

**Option 2: Distributed Cache** (Future Enhancement)
```java
// Use Keycloak's Infinispan cache
Cache<String, ChallengeData> cache = session.getProvider(InfinispanConnectionProvider.class)
    .getCache("webauthn-challenges");
```

**Option 3: Database Storage**
```sql
CREATE TABLE webauthn_challenges (
    session_id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255),
    challenge TEXT,
    type VARCHAR(50),
    expires_at TIMESTAMP
);
```

**Future Migration Path**:
1. Create `ChallengeStorage` interface
2. Implement `InMemoryChallengeStorage` (current)
3. Implement `InfinispanChallengeStorage` (clustered)
4. Add configuration flag to switch implementations

**Cleanup Strategy**:

Current implementation uses **lazy cleanup**:
```java
private void cleanupExpiredChallenges() {
    Instant now = Instant.now();
    challengeCache.entrySet().removeIf(entry ->
        entry.getValue().isExpired()
    );
}
```

Called on each `storeChallenge()` operation.

**Alternative Cleanup Strategies** (Not Implemented):
- **Background Thread**: Periodic cleanup with `ScheduledExecutorService`
- **LRU Eviction**: Limit cache size with eviction policy
- **Guava Cache**: Use `CacheBuilder` with automatic eviction

**Why Lazy Cleanup**:
- Simpler implementation
- No additional threads
- Sufficient for low-moderate traffic

---

## Module Organization

### Decision: Layered Architecture with Six Modules

**Status**: Implemented

**Context**:
Need to organize code for maintainability, testability, and clear separation of concerns.

**Decision**:
Organize into six distinct modules:
1. **Provider** - REST API and Keycloak SPI
2. **Service** - Business logic and workflows
3. **Model** - Domain entities and DTOs
4. **Util** - Cross-cutting utilities
5. **Exception** - Error handling hierarchy
6. **External** - Third-party libraries (WebAuthn4J, Jackson)

**Rationale**:

**Layered Architecture Benefits**:
- **Separation of Concerns**: Each layer has distinct responsibility
- **Testability**: Can test layers in isolation
- **Maintainability**: Changes localized to specific layers
- **Clear Dependencies**: Dependencies flow downward

**Module Dependency Rules**:
```
Provider → Service → Model
         ↓         ↓
         Util      Exception
```

**Why Not Hexagonal/Clean Architecture**:
- **Over-engineering**: Too complex for current scope
- **Interface Overhead**: No need for multiple implementations
- **Keycloak Coupling**: Tightly coupled to Keycloak SPIs anyway

**Why Not Single Package**:
- **Poor Organization**: Hard to navigate 30+ classes
- **Unclear Boundaries**: Service logic mixed with REST endpoints
- **Testing Challenges**: Difficult to test in isolation

**Alternative Considered: Feature-Based Modules**
```
registration/
    RegistrationController.java
    RegistrationService.java
    RegistrationRequest.java
authentication/
    AuthenticationController.java
    AuthenticationService.java
credentials/
    CredentialManager.java
```

**Why Rejected**:
- Duplication of cross-cutting concerns
- Unclear where shared models belong
- Harder to enforce architectural boundaries

---

## Error Handling

### Decision: Typed Exceptions with HTTP Status Mapping

**Status**: Implemented

**Context**:
Need structured error handling that:
- Provides clear error codes for clients
- Maps to appropriate HTTP status codes
- Logs errors for debugging
- Distinguishes between error types

**Decision**:
Create exception hierarchy with base `WebAuthnException` class containing `errorCode` and `httpStatusCode`.

**Rationale**:

**Pros**:
- **Type Safety**: Compiler enforces exception handling
- **Structured Errors**: Consistent error response format
- **HTTP Mapping**: Automatic status code mapping
- **Client-Friendly**: Error codes enable i18n on client
- **Logging**: Structured logging with error codes

**Cons**:
- **Exception Overhead**: Exception creation has performance cost
- **Verbosity**: Multiple exception classes to maintain

**Error Response Format**:
```json
{
  "error": "CREDENTIAL_NOT_FOUND",
  "errorDescription": "Credential with ID xyz not found"
}
```

**HTTP Status Code Mapping**:
| Exception Type | HTTP Status | Use Case |
|----------------|-------------|----------|
| `ValidationException` | 400 | Invalid input |
| `RegistrationException` | 400/500 | Registration failures |
| `InvalidCredentialException` | 401 | Authentication failures |
| `ChallengeExpiredException` | 401 | Expired challenges |
| `WebAuthnException` (generic) | 400 | Other errors |

**Alternative Considered: Result Pattern**
```java
public Result<WebAuthnCredential, WebAuthnError> verifyCredential(...) {
    if (invalid) {
        return Result.error(new WebAuthnError("INVALID"));
    }
    return Result.success(credential);
}
```

**Why Rejected**:
- Not idiomatic in Java (common in Rust, Haskell)
- Requires custom Result type
- Harder to integrate with Keycloak error handling

**Alternative Considered: Spring @ControllerAdvice Pattern**
Not applicable - not using Spring Framework.

---

## Dependency Management

### Decision: WebAuthn4J for WebAuthn Protocol Implementation

**Status**: Implemented

**Context**:
Need a library to handle:
- CBOR parsing (attestation objects, COSE keys)
- Client data JSON validation
- Cryptographic operations
- WebAuthn specification compliance

**Decision**:
Use WebAuthn4J library (version 0.29.3).

**Rationale**:

**Pros**:
- **Mature**: Active development, widely used
- **Spec Compliant**: Implements WebAuthn Level 2
- **Comprehensive**: Handles attestation, assertion, metadata
- **Well-Documented**: Good examples and documentation
- **Java Native**: No FFI or native dependencies

**Cons**:
- **Dependency Size**: 2+ MB with transitive dependencies
- **Version Coupling**: Must track WebAuthn4J updates
- **Learning Curve**: Complex API for advanced features

**Alternatives Considered**:

#### Option 1: Implement from Scratch
**Why Rejected**:
- **Complexity**: WebAuthn spec is complex (200+ pages)
- **Security Risk**: Easy to introduce crypto vulnerabilities
- **Maintenance**: Would need to track spec updates
- **Time**: Months of development effort

#### Option 2: Yubico's java-webauthn-server
**Why Rejected**:
- More opinionated architecture
- Heavier weight
- Less flexible for custom flows

**Dependency Bundling Strategy**:

Use Maven Shade plugin to bundle WebAuthn4J and dependencies:
```xml
<artifactSet>
    <includes>
        <include>com.webauthn4j:webauthn4j-core</include>
        <include>com.webauthn4j:webauthn4j-metadata</include>
        <include>com.fasterxml.jackson.dataformat:jackson-dataformat-cbor</include>
        <include>org.bouncycastle:bcprov-jdk18on</include>
        <include>org.bouncycastle:bcpkix-jdk18on</include>
    </includes>
</artifactSet>
```

**Relocation to Avoid Conflicts**:
```xml
<relocations>
    <relocation>
        <pattern>org.bouncycastle</pattern>
        <shadedPattern>com.inventage.keycloak.webauthn.shaded.bouncycastle</shadedPattern>
    </relocation>
</relocations>
```

**Why Shade**:
- Keycloak may have conflicting BouncyCastle version
- Self-contained JAR simplifies deployment
- Avoids classloader conflicts

---

## Security Decisions

### Decision: 32-Byte Challenges with SecureRandom

**Status**: Implemented

**Context**:
WebAuthn specification requires:
- Minimum 16 bytes of entropy for challenges
- Cryptographically secure random generation

**Decision**:
Use 32-byte (256-bit) challenges generated with `java.security.SecureRandom`.

**Rationale**:

**Challenge Length**:
- **Minimum**: 16 bytes (128 bits) per WebAuthn spec
- **Chosen**: 32 bytes (256 bits)
- **Reasoning**:
  - Extra security margin
  - Aligns with SHA-256 output size
  - No performance penalty (negligible size difference)
  - Future-proof against advances in computing power

**Random Number Generator**:
- **Choice**: `java.security.SecureRandom`
- **Reasoning**:
  - Cryptographically secure PRNG
  - Platform-dependent implementation (uses /dev/urandom on Linux)
  - Thread-safe
  - Standard Java library (no dependencies)

**Alternative Considered: Math.random()**
**Why Rejected**:
- NOT cryptographically secure
- Predictable seed
- Security vulnerability

**Alternative Considered: UUID.randomUUID()**
**Why Rejected**:
- Only 122 bits of randomness (6 bits reserved for version/variant)
- Less efficient than SecureRandom
- Unnecessary string formatting overhead

### Decision: ECDSA Signature Verification (ES256)

**Status**: Implemented

**Context**:
Need to verify WebAuthn assertion signatures.

**Decision**:
Support ECDSA with SHA-256 (ES256) and RSA with SHA-256 (RS256).

**Rationale**:

**Algorithm Priority**:
1. **ES256** (ECDSA P-256 with SHA-256) - Preferred
2. **RS256** (RSA PKCS#1 v1.5 with SHA-256) - Fallback

**Why ES256 Preferred**:
- Smaller signatures (64 bytes vs 256 bytes for RSA-2048)
- Faster verification
- Modern standard for WebAuthn
- Supported by most authenticators

**Why Support RS256**:
- Legacy authenticator compatibility
- Some hardware tokens only support RSA
- Broader device support

**Security Considerations**:
- Both algorithms provide 128-bit security level
- ES256 more resistant to quantum attacks (smaller key size)
- RSA requires 2048+ bit keys for security

**Implementation**:
```java
Signature sig = Signature.getInstance("SHA256withECDSA");
sig.initVerify(publicKey);
sig.update(signedData);
boolean valid = sig.verify(signature);
```

### Decision: Sign Count Validation for Clone Detection

**Status**: Implemented

**Context**:
WebAuthn authenticators include a signature counter to detect cloned devices.

**Decision**:
Validate that sign count increases on each authentication.

**Rationale**:

**Security Threat**:
Attacker clones authenticator (extracts private key) and uses clone.

**Detection Mechanism**:
- Authenticator increments counter on each signature
- Server stores last seen counter
- If counter doesn't increase → possible clone

**Implementation**:
```java
if (newSignCount > 0 && newSignCount <= oldSignCount) {
    throw new InvalidCredentialException(
        "Sign count validation failed - possible authenticator clone"
    );
}
```

**Special Case**: Counter value 0
- Some authenticators don't support counters (always return 0)
- Accept 0 → 0 transitions
- Log warning for monitoring

**False Positive Risk**:
- User uses backup/sync authenticators (same key, different counters)
- Multiple devices with same credential
- Cloud-synced passkeys

**Future Enhancement**:
- Track counter per authenticator (using AAGUID)
- Allow counter rollback with user confirmation
- Configurable strictness levels

---

## Future Considerations

### Planned Enhancements

#### 1. Clustered Deployment Support

**Current Limitation**:
In-memory challenge cache doesn't work across Keycloak cluster nodes.

**Solutions**:
1. **Sticky Sessions**: Configure load balancer
2. **Infinispan Integration**: Use Keycloak's distributed cache
3. **Database-Backed Challenges**: Store in database with TTL

**Recommendation**: Infinispan (native to Keycloak)

#### 2. FIDO Metadata Service (MDS) Integration

**Current State**:
No attestation validation or authenticator metadata.

**Enhancement**:
- Integrate FIDO MDS for authenticator verification
- Validate attestation certificates
- Check for revoked authenticators
- Enforce authenticator policies (e.g., FIPS certified only)

**Implementation**:
```java
MetadataService metadataService = new MetadataServiceImpl();
AuthenticatorMetadata metadata = metadataService.lookup(aaguid);
if (metadata.isRevoked()) {
    throw new RegistrationException("Authenticator revoked");
}
```

#### 3. Custom Authenticator SPI

**Current State**:
REST API only, not integrated with Keycloak authentication flows.

**Enhancement**:
Implement Keycloak Authenticator SPI for:
- WebAuthn as authentication step in browser flows
- Required action for credential registration
- Admin console integration

**Implementation**:
```java
public class WebAuthnAuthenticator implements Authenticator {
    @Override
    public void authenticate(AuthenticationFlowContext context) {
        // Generate challenge
        // Redirect to WebAuthn page
        // Verify assertion
        // Complete authentication
    }
}
```

#### 4. Admin Console UI

**Current State**:
No admin UI for credential management.

**Enhancement**:
- Admin view of user credentials
- Bulk credential revocation
- Authenticator statistics
- Security policy configuration

#### 5. Passkey Synchronization

**Current State**:
Credentials stored per-realm, not synchronized.

**Enhancement**:
- Cloud-backed passkey sync (Apple, Google)
- Cross-realm credential sharing
- Device-to-device credential transfer

#### 6. Conditional UI Support

**Current State**:
Standard WebAuthn flows require user interaction.

**Enhancement**:
- Conditional mediation (autofill passkeys)
- Browser autofill integration
- Platform authenticator preference

---

## Rejected Decisions

### Rejected: Custom Token Format

**Proposed**:
Use custom JWT claims for WebAuthn metadata.

**Rejected Because**:
- Standard OAuth2/OIDC tokens sufficient
- No need for custom claims
- Increases complexity
- Breaks standard token validation

**Instead**:
Use standard Keycloak token generation.

### Rejected: Separate Credential Database

**Proposed**:
Store credentials in separate PostgreSQL database.

**Rejected Because**:
- Introduces external dependency
- Complicates deployment
- Keycloak already has database
- No significant performance benefit

**Instead**:
Use Keycloak user attributes.

### Rejected: Microservice Architecture

**Proposed**:
Separate WebAuthn service from Keycloak.

**Rejected Because**:
- Over-engineering for current scope
- Adds network latency
- Complicates deployment
- Session management challenges

**Instead**:
Keycloak SPI extension.

---

## Decision Review Process

**Frequency**: Every major version release

**Criteria for Revision**:
- Performance bottlenecks identified
- New Keycloak features available
- WebAuthn specification updates
- User feedback

**Next Review**: After v1.0 production deployment

---

## References

- [WebAuthn Level 2 Specification](https://www.w3.org/TR/webauthn-2/)
- [WebAuthn4J Documentation](https://github.com/webauthn4j/webauthn4j)
- [Keycloak SPI Documentation](https://www.keycloak.org/docs/latest/server_development/)
- [FIDO2 CTAP Specification](https://fidoalliance.org/specs/fido-v2.0-ps-20190130/fido-client-to-authenticator-protocol-v2.0-ps-20190130.html)
