# WebAuthn Extension Module Guide

This document provides detailed information about each module in the WebAuthn extension architecture.

## Table of Contents
1. [Provider Module](#provider-module)
2. [Service Module](#service-module)
3. [Model Module](#model-module)
4. [Util Module](#util-module)
5. [Exception Module](#exception-module)
6. [Module Interaction Examples](#module-interaction-examples)

---

## Provider Module

**Package**: `com.inventage.keycloak.webauthn.infrastructure.provider`

### Purpose
The Provider Module serves as the integration point between Keycloak and the WebAuthn extension. It exposes REST API endpoints and implements the Keycloak Service Provider Interface (SPI).

### Components

#### WebAuthnRealmResourceProviderFactory
**Type**: SPI Factory
**Lifecycle**: Singleton per Keycloak deployment

**Responsibilities**:
- Register the WebAuthn provider with Keycloak
- Create provider instances per HTTP request
- Define the provider ID ("webauthn")

**Key Methods**:
```java
RealmResourceProvider create(KeycloakSession session)
String getId()
void init(Config.Scope config)
void close()
```

**Configuration**:
- Uses `@AutoService` annotation for automatic SPI registration
- No additional configuration required

#### WebAuthnRealmResourceProvider
**Type**: JAX-RS Resource
**Lifecycle**: Per-request instance

**REST Endpoints**:

| Method | Path | Description | Request | Response |
|--------|------|-------------|---------|----------|
| GET | `/api/webauthn` | Health check | None | `{status, service, realm}` |
| POST | `/api/webauthn/register/challenge` | Generate registration challenge | `{username, displayName, credentialName, type}` | `{sessionId, challenge, user, rp, ...}` |
| POST | `/api/webauthn/register/verify` | Verify registration | `{sessionId, response, credentialName, type}` | `{success, credentialId, tokens}` |
| POST | `/api/webauthn/auth/challenge` | Generate auth challenge | `{username}` | `{sessionId, challenge, allowCredentials}` |
| POST | `/api/webauthn/auth/verify` | Verify authentication | `{sessionId, response}` | `{success, tokens}` |
| GET | `/api/webauthn/credentials` | List credentials | None (requires auth) | `{credentials: [...], total}` |
| DELETE | `/api/webauthn/credentials/{id}` | Delete credential | None | HTTP 204 |

**Error Handling**:
- All errors return JSON with `error` and `errorDescription`
- HTTP status codes: 400 (validation), 401 (auth), 404 (not found), 500 (internal)
- Converts service exceptions to HTTP responses

**Dependencies**:
```
WebAuthnRealmResourceProvider
    ├── WebAuthnRegistrationService
    ├── WebAuthnAuthenticationService
    ├── WebAuthnCredentialManager
    └── TokenService
```

### Public API

#### Registration Flow
```java
// 1. Generate challenge
POST /realms/{realm}/api/webauthn/register/challenge
{
  "username": "user@example.com",
  "displayName": "John Doe",
  "credentialName": "My YubiKey",
  "type": "passwordless"
}

// 2. Verify and store
POST /realms/{realm}/api/webauthn/register/verify
{
  "sessionId": "uuid",
  "response": {
    "id": "credential-id",
    "rawId": "base64url",
    "response": {
      "clientDataJSON": "base64url",
      "attestationObject": "base64url"
    },
    "type": "public-key"
  },
  "credentialName": "My YubiKey",
  "type": "passwordless"
}
```

#### Authentication Flow
```java
// 1. Generate challenge
POST /realms/{realm}/api/webauthn/auth/challenge
{
  "username": "user@example.com"
}

// 2. Verify assertion
POST /realms/{realm}/api/webauthn/auth/verify
{
  "sessionId": "uuid",
  "response": {
    "id": "credential-id",
    "rawId": "base64url",
    "response": {
      "clientDataJSON": "base64url",
      "authenticatorData": "base64url",
      "signature": "base64url",
      "userHandle": "base64url"
    },
    "type": "public-key"
  }
}
```

### Extension Points

#### Adding New Endpoints
1. Add JAX-RS annotated method to `WebAuthnRealmResourceProvider`
2. Create service method in appropriate service class
3. Define request/response DTOs
4. Add error handling

Example:
```java
@POST
@Path("credentials/rename")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@NoCache
public Response renameCredential(Map<String, Object> request) {
    String credentialId = (String) request.get("credentialId");
    String newName = (String) request.get("newName");

    credentialManager.renameCredential(userId, credentialId, newName);

    return Response.ok().build();
}
```

---

## Service Module

**Package**: `com.inventage.keycloak.webauthn.infrastructure.service`

### Purpose
The Service Module implements the core business logic for WebAuthn registration, authentication, credential management, and token generation.

### Components

#### WebAuthnRegistrationService
**Responsibility**: Credential registration workflow

**Key Methods**:

| Method | Parameters | Returns | Throws |
|--------|------------|---------|--------|
| `generateChallenge` | `userId, credentialName, credentialType` | `Map<String, Object>` | `RegistrationException` |
| `verifyAndStoreCredential` | `sessionId, clientDataJSON, attestationObject, credentialName, credentialType` | `WebAuthnCredential` | `RegistrationException`, `ChallengeExpiredException` |

**Workflow**:
1. **Challenge Generation**:
   - Generate 32-byte random challenge
   - Store challenge with 5-minute TTL
   - Build registration options (RP info, user info, pubKeyCredParams)
   - Return challenge response

2. **Verification**:
   - Validate challenge (exists and not expired)
   - Parse and validate client data JSON
   - Parse attestation object (CBOR)
   - Extract credential (ID, public key, sign count)
   - Store credential in user attributes
   - Clear challenge

**Dependencies**:
- `WebAuthnCredentialManager` - Credential storage
- `WebAuthn4J` - CBOR parsing, crypto validation
- `ObjectConverter` - JSON/CBOR conversion

**Configuration**:
```java
private static final int CHALLENGE_LENGTH = 32;
private static final SecureRandom RANDOM = new SecureRandom();
```

#### WebAuthnAuthenticationService
**Responsibility**: Credential authentication workflow

**Key Methods**:

| Method | Parameters | Returns | Throws |
|--------|------------|---------|--------|
| `generateChallenge` | `username` | `Map<String, Object>` | `WebAuthnException` |
| `verifyAssertion` | `sessionId, credentialId, clientDataJSON, authenticatorData, signature, userHandle` | `UserModel` | `InvalidCredentialException`, `ChallengeExpiredException` |

**Workflow**:
1. **Challenge Generation**:
   - Generate 32-byte random challenge
   - Retrieve user's registered credentials
   - Store challenge with 5-minute TTL
   - Build authentication options (allowCredentials)
   - Return challenge response

2. **Verification**:
   - Validate challenge (exists and not expired)
   - Parse and validate client data JSON
   - Parse authenticator data
   - Verify cryptographic signature
   - Validate sign count (detect cloned authenticators)
   - Update credential (sign count, last used timestamp)
   - Clear challenge
   - Return authenticated user

**Cryptographic Operations**:
- **Signature Verification**: ECDSA with SHA-256
- **Hash Computation**: SHA-256 for client data
- **Public Key Decoding**: COSE key format

**Dependencies**:
- `WebAuthnCredentialManager` - Credential retrieval
- `WebAuthn4J` - Client data parsing
- Java Cryptography (JCA) - Signature verification

#### WebAuthnCredentialManager
**Responsibility**: Credential CRUD operations and challenge management

**Storage Strategy**: JSON serialization in Keycloak user attributes

**Key Methods**:

| Method | Parameters | Returns | Throws |
|--------|------------|---------|--------|
| `storeCredential` | `userId, credential` | `void` | `WebAuthnException` |
| `getCredentials` | `userId` | `List<WebAuthnCredential>` | `WebAuthnException` |
| `findCredentialById` | `credentialId` | `WebAuthnCredential` | `WebAuthnException` |
| `deleteCredential` | `userId, credentialId` | `void` | `WebAuthnException` |
| `updateCredential` | `userId, credential` | `void` | `WebAuthnException` |
| `storeChallenge` | `userId, challenge, type, ttl` | `String` (sessionId) | - |
| `getChallenge` | `sessionId` | `ChallengeData` | - |
| `deleteChallenge` | `sessionId` | `void` | - |

**Credential Storage**:
```java
// Stored as JSON in user attribute "webauthn.credentials"
List<WebAuthnCredential> credentials = [...]
String json = objectMapper.writeValueAsString(credentials);
user.setSingleAttribute("webauthn.credentials", json);
```

**Challenge Cache**:
```java
// In-memory cache with TTL
private static final Map<String, ChallengeData> challengeCache =
    new ConcurrentHashMap<>();

public static class ChallengeData {
    private final String userId;
    private final String challenge;
    private final String type; // "registration" or "authentication"
    private final Instant expiresAt;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
```

**Cleanup Strategy**: Lazy cleanup on cache access

#### TokenService
**Responsibility**: OAuth2/OIDC token generation after successful WebAuthn authentication

**Key Methods**:

| Method | Parameters | Returns | Throws |
|--------|------------|---------|--------|
| `generateTokens` | `user, client, scope` | `Map<String, Object>` | `RuntimeException` |
| `revokeTokens` | `sessionId` | `void` | `RuntimeException` |

**Token Generation Workflow**:
1. Create root authentication session
2. Create tab-specific authentication session
3. Set authenticated user
4. Create user session
5. Create client session
6. Use Keycloak's `TokenManager` to generate tokens
7. Return access token, refresh token, ID token

**Generated Tokens**:
```json
{
  "access_token": "eyJhbGc...",
  "refresh_token": "eyJhbGc...",
  "id_token": "eyJhbGc...",
  "expires_in": 300,
  "refresh_expires_in": 1800,
  "token_type": "Bearer",
  "session_state": "uuid"
}
```

**Dependencies**:
- Keycloak `TokenManager`
- Keycloak `AuthenticationManager`
- Keycloak session management

### Service Layer Invariants

1. **Challenge TTL**: All challenges expire after 300 seconds (5 minutes)
2. **Challenge Length**: All challenges are 32 bytes (256 bits)
3. **SecureRandom**: All random generation uses `SecureRandom`
4. **Sign Count**: Must increase on each authentication (detect clones)
5. **Credential Uniqueness**: Credential IDs must be unique per user

### Error Handling

All service methods throw typed exceptions:
- `RegistrationException` - Registration workflow failures
- `InvalidCredentialException` - Authentication failures
- `ChallengeExpiredException` - Expired challenges
- `WebAuthnException` - General WebAuthn errors

---

## Model Module

**Packages**:
- `io.inventage.keycloak.custom.webauthn.infrastructure.model`

### Purpose
The Model Module defines domain entities, enums, and data transfer objects (DTOs) used throughout the extension.

### Domain Entities

#### WebAuthnCredential
**Purpose**: Represents a registered WebAuthn credential

**Fields**:

| Field | Type | Description | Required |
|-------|------|-------------|----------|
| `credentialId` | `String` | Base64url credential identifier | Yes |
| `credentialPublicKey` | `String` | Base64url COSE-encoded public key | Yes |
| `attestationObject` | `String` | Base64url attestation object | Yes |
| `signCount` | `long` | Signature counter (anti-cloning) | Yes |
| `credentialType` | `String` | "public-key" | Yes |
| `transportHints` | `List<String>` | usb, ble, nfc, internal | No |
| `type` | `CredentialType` | passwordless or twofactor | Yes |
| `metadata` | `CredentialMetadata` | Name, timestamps, AAGUID | No |
| `backup` | `BackupEligibility` | Backup flags | No |

**Nested Classes**:

```java
public static class CredentialMetadata {
    private String name;
    private Instant createdAt;
    private Instant lastUsedAt;
    private String aaguid; // Authenticator AAGUID
}

public static class BackupEligibility {
    private boolean eligible;
    private boolean state;
}
```

**Methods**:
- `updateSignCount(long)` - Update signature counter
- `updateLastUsed()` - Update last used timestamp
- `setTypeFromString(String)` - Parse credential type
- `getTypeAsString()` - Get credential type as string

**JSON Serialization**:
Uses Jackson annotations (`@JsonProperty`) for camelCase property names.

#### CredentialType (Enum)
**Purpose**: Classify credentials as passwordless or two-factor

**Values**:

| Enum | Value | Description |
|------|-------|-------------|
| `TYPE_PASSWORDLESS` | "passwordless" | Primary authentication factor |
| `TYPE_TWOFACTOR` | "twofactor" | Secondary authentication factor |

**Methods**:
```java
public static CredentialType fromValue(String value) {
    // Returns TYPE_PASSWORDLESS as default
}
```

**Use Cases**:
- **Passwordless**: Mobile apps, biometric authentication
- **Two-Factor**: Additional security layer, compliance requirements

### Data Transfer Objects (DTOs)

#### Request DTOs

| Class | Purpose | Fields |
|-------|---------|--------|
| `RegistrationChallengeRequest` | Request registration challenge | username, displayName, credentialName, type |
| `RegistrationVerifyRequest` | Verify registration | sessionId, response, credentialName, type |
| `AuthChallengeRequest` | Request auth challenge | username |
| `AuthVerifyRequest` | Verify authentication | sessionId, response |

#### Response DTOs

| Class | Purpose | Fields |
|-------|---------|--------|
| `RegistrationChallengeResponse` | Registration challenge | sessionId, challenge, user, rp, pubKeyCredParams |
| `RegistrationVerifyResponse` | Registration result | success, credentialId, tokens |
| `AuthenticationChallengeResponse` | Auth challenge | sessionId, challenge, allowCredentials |
| `AuthVerifyResponse` | Auth result | success, tokens |
| `TokenResponse` | OAuth2 tokens | access_token, refresh_token, id_token, expires_in |
| `ErrorResponse` | Error information | error, errorDescription |
| `CredentialListResponse` | Credential list | credentials, total |

#### WebAuthn-Specific DTOs

| Class | Purpose | Fields |
|-------|---------|--------|
| `AttestationResponse` | WebAuthn attestation | id, rawId, response, type |
| `AttestationResponseData` | Attestation data | clientDataJSON, attestationObject |
| `AssertionResponse` | WebAuthn assertion | id, rawId, response, type |
| `AssertionResponseData` | Assertion data | clientDataJSON, authenticatorData, signature, userHandle |

### Model Validation Rules

1. **Credential ID**: Must be unique, Base64url encoded
2. **Public Key**: Must be valid COSE key, Base64url encoded
3. **Sign Count**: Must be non-negative, increases on auth
4. **Timestamps**: ISO-8601 format via `Instant`
5. **Credential Type**: Defaults to `TYPE_PASSWORDLESS` if null

### JSON Schema Examples

**WebAuthnCredential**:
```json
{
  "credentialId": "base64url-encoded-id",
  "credentialPublicKey": "base64url-cose-key",
  "attestationObject": "base64url-attestation",
  "signCount": 42,
  "credentialType": "public-key",
  "transportHints": ["usb", "nfc"],
  "type": "passwordless",
  "metadata": {
    "name": "My YubiKey",
    "createdAt": "2025-01-15T10:30:00Z",
    "lastUsedAt": "2025-01-20T14:22:00Z",
    "aaguid": "base64url-aaguid"
  }
}
```

---

## Util Module

**Package**: `com.inventage.keycloak.webauthn.util`

### Purpose
The Util Module provides cross-cutting utility functions for encoding/decoding and random generation.

### Components

#### Base64Util
**Purpose**: URL-safe Base64 encoding/decoding without padding (per WebAuthn spec)

**Methods**:

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `encodeToString` | `byte[]` | `String` | Encode bytes to base64url |
| `decode` | `String` | `byte[]` | Decode base64url to bytes |
| `encode` | `String` | `String` | Encode string to base64url |
| `decodeToString` | `String` | `String` | Decode base64url to string |

**Implementation**:
```java
private static final Base64.Encoder ENCODER =
    Base64.getUrlEncoder().withoutPadding();
private static final Base64.Decoder DECODER =
    Base64.getUrlDecoder();
```

**Usage**:
```java
// Encode credential ID
String credentialId = Base64Util.encodeToString(credentialIdBytes);

// Decode client data JSON
byte[] clientData = Base64Util.decode(clientDataJSON);
```

#### ChallengeGenerator
**Purpose**: Generate cryptographically secure random challenges

**Methods**:

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `generate` | `int length` | `byte[]` | Generate random bytes |
| `generate` | None | `byte[]` | Generate 32-byte challenge |
| `generateBase64Url` | None | `String` | Generate base64url challenge |

**Implementation**:
```java
private static final SecureRandom RANDOM = new SecureRandom();
private static final int DEFAULT_LENGTH = 32;

public static byte[] generate(int length) {
    byte[] challenge = new byte[length];
    RANDOM.nextBytes(challenge);
    return challenge;
}
```

**Security**:
- Uses `java.security.SecureRandom`
- 32 bytes = 256 bits of entropy
- Meets WebAuthn minimum of 16 bytes

**Usage**:
```java
// Generate challenge for registration
String challenge = ChallengeGenerator.generateBase64Url();
```

### Utility Patterns

1. **Static Methods**: All utilities are stateless
2. **Private Constructors**: Prevent instantiation
3. **Thread-Safe**: `SecureRandom` is thread-safe
4. **Immutable**: No mutable state

---

## Exception Module

**Package**: `com.inventage.keycloak.webauthn.infrastructure.exception`

### Purpose
The Exception Module provides structured error handling with HTTP status code mapping.

### Exception Hierarchy

```
Throwable
    └── Exception
        └── WebAuthnException (base)
            ├── RegistrationException
            ├── InvalidCredentialException
            ├── ChallengeExpiredException
            └── ValidationException
```

### Components

#### WebAuthnException (Base)
**Purpose**: Base class for all WebAuthn exceptions

**Fields**:
- `errorCode` - String identifier for error type
- `httpStatusCode` - HTTP status code for response
- `message` - Human-readable error message
- `cause` - Underlying exception (optional)

**Constructors**:
```java
WebAuthnException(String errorCode, String message)
WebAuthnException(String errorCode, String message, int httpStatusCode)
WebAuthnException(String errorCode, String message, int httpStatusCode, Throwable cause)
```

**Methods**:
- `getErrorCode()` - Get error code
- `getHttpStatusCode()` - Get HTTP status
- `log()` - Log exception with structured format

**Default HTTP Status**: 400 (Bad Request)

#### RegistrationException
**Purpose**: Registration workflow failures

**Common Error Codes**:
- `CHALLENGE_ERROR` - Challenge generation failed
- `ATTESTATION_ERROR` - Attestation parsing failed
- `CREDENTIAL_ERROR` - Credential extraction failed
- `STORAGE_ERROR` - Storage operation failed

**HTTP Status**: 400 (validation), 500 (internal)

**Usage**:
```java
throw new RegistrationException(
    "ATTESTATION_ERROR",
    "Invalid attestation object format",
    400
);
```

#### InvalidCredentialException
**Purpose**: Authentication failures

**Common Error Codes**:
- `CREDENTIAL_NOT_FOUND` - Credential doesn't exist
- `SIGNATURE_INVALID` - Signature verification failed
- `SIGN_COUNT_ERROR` - Sign count validation failed

**HTTP Status**: 401 (Unauthorized)

**Usage**:
```java
throw new InvalidCredentialException(
    "Signature verification failed"
);
```

#### ChallengeExpiredException
**Purpose**: Expired or missing challenges

**HTTP Status**: 401 (Unauthorized)

**Usage**:
```java
if (challengeData == null || challengeData.isExpired()) {
    throw new ChallengeExpiredException(
        "Challenge expired or not found"
    );
}
```

#### ValidationException
**Purpose**: Input validation errors

**Common Error Codes**:
- `VALIDATION_ERROR` - General validation failure
- `REQUIRED_FIELD` - Missing required field
- `INVALID_FORMAT` - Invalid data format

**HTTP Status**: 400 (Bad Request)

### Error Code Conventions

| Prefix | Category | Example |
|--------|----------|---------|
| `USER_*` | User-related | `USER_NOT_FOUND` |
| `CREDENTIAL_*` | Credential-related | `CREDENTIAL_NOT_FOUND` |
| `CHALLENGE_*` | Challenge-related | `CHALLENGE_EXPIRED` |
| `VALIDATION_*` | Validation | `VALIDATION_ERROR` |
| `STORAGE_*` | Storage | `STORAGE_ERROR` |

### Error Response Format

All exceptions are converted to JSON by the Provider Layer:

```json
{
  "error": "CREDENTIAL_NOT_FOUND",
  "errorDescription": "Credential with ID xyz not found"
}
```

HTTP Status Code is set in the response header.

---

## Module Interaction Examples

### Example 1: Complete Registration Flow

```java
// 1. Client calls Provider
POST /api/webauthn/register/challenge

// 2. Provider → RegistrationService
WebAuthnRegistrationService service = new WebAuthnRegistrationService(session);
Map<String, Object> response = service.generateChallenge(
    userId,
    "My Security Key",
    "passwordless"
);

// 3. RegistrationService → CredentialManager
WebAuthnCredentialManager manager = new WebAuthnCredentialManager(session);
String sessionId = manager.storeChallenge(
    userId,
    challenge,
    "registration",
    300
);

// 4. RegistrationService → Util
String challenge = ChallengeGenerator.generateBase64Url();

// 5. Client receives challenge and interacts with authenticator

// 6. Client calls Provider with attestation
POST /api/webauthn/register/verify

// 7. Provider → RegistrationService
WebAuthnCredential credential = service.verifyAndStoreCredential(
    sessionId,
    clientDataJSON,
    attestationObject,
    "My Security Key",
    "passwordless"
);

// 8. RegistrationService validates and extracts credential
ChallengeData challengeData = manager.getChallenge(sessionId);
CollectedClientData clientData = validateClientData(...);
AttestationObject attestation = parseAttestationObject(...);
WebAuthnCredential cred = extractCredentialFromAttestation(...);

// 9. RegistrationService → CredentialManager
manager.storeCredential(userId, credential);

// 10. CredentialManager → Keycloak
UserModel user = session.users().getUserById(realm, userId);
String json = objectMapper.writeValueAsString(credentials);
user.setSingleAttribute("webauthn.credentials", json);

// 11. Provider → TokenService
TokenService tokenService = new TokenService(session);
Map<String, Object> tokens = tokenService.generateTokens(user, client, "openid profile");

// 12. Client receives tokens
```

### Example 2: Credential Listing

```java
// 1. Client calls Provider
GET /api/webauthn/credentials
Authorization: Bearer <access_token>

// 2. Provider extracts user from token
String userId = extractUserIdFromToken();

// 3. Provider → CredentialManager
WebAuthnCredentialManager manager = new WebAuthnCredentialManager(session);
List<WebAuthnCredential> credentials = manager.getCredentials(userId);

// 4. CredentialManager → Keycloak
UserModel user = session.users().getUserById(realm, userId);
List<String> attrs = user.getAttributes().get("webauthn.credentials");
String json = attrs.get(0);

// 5. CredentialManager → Model
List<WebAuthnCredential> credentials = objectMapper.readValue(
    json,
    new TypeReference<List<WebAuthnCredential>>() {}
);

// 6. Client receives credential list
{
  "credentials": [
    {
      "credentialId": "...",
      "metadata": {
        "name": "My YubiKey",
        "createdAt": "...",
        "lastUsedAt": "..."
      },
      "type": "passwordless"
    }
  ],
  "total": 1
}
```

### Example 3: Error Handling Flow

```java
// 1. Invalid credential during authentication
POST /api/webauthn/auth/verify

// 2. AuthenticationService → CredentialManager
WebAuthnCredential credential = manager.findCredentialById(credentialId);
if (credential == null) {
    throw new InvalidCredentialException("Credential not found");
}

// 3. Signature verification fails
verifySignature(...);
throw new InvalidCredentialException("Signature verification failed");

// 4. Exception bubbles up to Provider
catch (InvalidCredentialException e) {
    return errorResponse(e);
}

// 5. Provider converts to HTTP response
{
  "error": "INVALID_CREDENTIAL",
  "errorDescription": "Signature verification failed"
}
HTTP Status: 401
```

## Module Testing Strategies

### Provider Module
- **Integration Tests**: Testcontainers with Keycloak
- **REST API Tests**: JAX-RS client testing
- **Error Response Tests**: Validate error format

### Service Module
- **Unit Tests**: Mock Keycloak dependencies
- **Business Logic Tests**: Test workflows in isolation
- **Cryptographic Tests**: Validate signature verification

### Model Module
- **Serialization Tests**: JSON round-trip testing
- **Validation Tests**: Test constraints
- **Enum Tests**: Test CredentialType parsing

### Util Module
- **Unit Tests**: Test encoding/decoding
- **Randomness Tests**: Validate entropy
- **Edge Cases**: Empty input, large data

### Exception Module
- **Unit Tests**: Test exception creation
- **HTTP Mapping Tests**: Validate status codes
- **Logging Tests**: Verify structured logging
