# WebAuthn Extension Architecture

## Overview

The Keycloak WebAuthn Extension is a comprehensive implementation of WebAuthn/FIDO2 passwordless and multi-factor authentication. The architecture follows clean, layered design principles with clear separation of concerns across six primary modules.

## System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Keycloak Server                                  │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                    SPI Integration Layer                           │  │
│  │  ┌─────────────────────────────────────────────────────────────┐  │  │
│  │  │  WebAuthnRealmResourceProviderFactory (Provider Module)      │  │  │
│  │  │    • Registers REST endpoints                                │  │  │
│  │  │    • Manages lifecycle                                       │  │  │
│  │  └─────────────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      REST API Layer (Provider Module)                    │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │           WebAuthnRealmResourceProvider                           │  │
│  │                                                                   │  │
│  │  Endpoints:                                                       │  │
│  │  • POST /register/challenge    → Generate registration challenge │  │
│  │  • POST /register/verify       → Verify and store credential     │  │
│  │  • POST /auth/challenge        → Generate auth challenge         │  │
│  │  • POST /auth/verify           → Verify assertion & auth user    │  │
│  │  • GET  /credentials           → List user credentials           │  │
│  │  • DELETE /credentials/{id}    → Delete credential               │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                   │
                    ┌──────────────┼──────────────┐
                    │              │              │
                    ▼              ▼              ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                       Service Layer (Service Module)                      │
│  ┌─────────────────┐  ┌──────────────────┐  ┌────────────────────────┐  │
│  │  Registration   │  │  Authentication  │  │  Credential Manager    │  │
│  │     Service     │  │     Service      │  │                        │  │
│  │                 │  │                  │  │  • CRUD operations     │  │
│  │ • Challenge gen │  │ • Challenge gen  │  │  • Storage/retrieval   │  │
│  │ • Attestation   │  │ • Assertion      │  │  • Challenge cache     │  │
│  │   verification  │  │   verification   │  │  • JSON serialization  │  │
│  │ • Credential    │  │ • Signature      │  │                        │  │
│  │   storage       │  │   validation     │  │                        │  │
│  └─────────────────┘  └──────────────────┘  └────────────────────────┘  │
│                                                                           │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                      TokenService                                  │  │
│  │  • Generate OAuth2/OIDC tokens                                    │  │
│  │  • Create authentication sessions                                 │  │
│  │  • Token lifecycle management                                     │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
                                   │
                    ┌──────────────┼──────────────┐
                    │              │              │
                    ▼              ▼              ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                      Domain Model Layer (Model Module)                    │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │  WebAuthnCredential                                              │    │
│  │  • credentialId, credentialPublicKey                            │    │
│  │  • signCount, attestationObject                                 │    │
│  │  • type (passwordless/twofactor)                                │    │
│  │  • metadata (name, aaguid, timestamps)                          │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                                                           │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │  DTOs (Request/Response Models)                                  │    │
│  │  • RegistrationChallengeRequest/Response                        │    │
│  │  • AuthenticationChallengeRequest/Response                      │    │
│  │  • RegistrationVerifyRequest/Response                           │    │
│  │  • AuthVerifyRequest/Response                                   │    │
│  │  • TokenResponse, ErrorResponse                                 │    │
│  └─────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                    Utility Layer (Util Module)                            │
│  ┌────────────────────┐  ┌─────────────────────────────────────────┐   │
│  │   Base64Util       │  │   ChallengeGenerator                    │   │
│  │  • Encode/decode   │  │  • SecureRandom generation              │   │
│  │  • URL-safe base64 │  │  • 32-byte challenges                   │   │
│  └────────────────────┘  └─────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                   Exception Layer (Exception Module)                      │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │  WebAuthnException (Base)                                        │    │
│  │    • errorCode, httpStatusCode                                  │    │
│  │    • Structured error handling                                   │    │
│  │                                                                   │    │
│  │  Specialized Exceptions:                                         │    │
│  │    • RegistrationException       (400/500)                      │    │
│  │    • InvalidCredentialException  (401)                          │    │
│  │    • ChallengeExpiredException   (401)                          │    │
│  │    • ValidationException         (400)                          │    │
│  └─────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                    External Dependencies                                  │
│  ┌──────────────┐  ┌─────────────┐  ┌────────────────────────────────┐ │
│  │  WebAuthn4J  │  │  Jackson    │  │  Keycloak SPIs                 │ │
│  │  • CBOR      │  │  • JSON     │  │  • UserModel, RealmModel       │ │
│  │  • Crypto    │  │  • CBOR     │  │  • KeycloakSession             │ │
│  │  • Parsing   │  │             │  │  • TokenManager                │ │
│  └──────────────┘  └─────────────┘  └────────────────────────────────┘ │
│  ┌──────────────┐                                                        │
│  │ BouncyCastle │                                                        │
│  │  • RSA/ECDSA │                                                        │
│  └──────────────┘                                                        │
└──────────────────────────────────────────────────────────────────────────┘
```

## Data Flow Diagrams

### Registration Flow

```
Client                  Provider               RegistrationService        CredentialManager      Keycloak
  │                        │                          │                          │                │
  │──Register Challenge──▶│                          │                          │                │
  │                        │───Generate Challenge────▶│                          │                │
  │                        │                          │──Store Challenge────────▶│                │
  │                        │                          │◀─SessionId───────────────│                │
  │                        │◀─Challenge + Session─────│                          │                │
  │◀─Challenge Response────│                          │                          │                │
  │                        │                          │                          │                │
  │                        │                          │                          │                │
  │                     [User interacts with authenticator]                      │                │
  │                        │                          │                          │                │
  │                        │                          │                          │                │
  │──Attestation────────▶│                          │                          │                │
  │                        │───Verify Attestation────▶│                          │                │
  │                        │                          │──Get Challenge──────────▶│                │
  │                        │                          │◀─Challenge Data──────────│                │
  │                        │                          │                          │                │
  │                        │                          │──Validate ClientData─────│                │
  │                        │                          │──Parse AttestationObj────│                │
  │                        │                          │──Extract Credential──────│                │
  │                        │                          │                          │                │
  │                        │                          │──Store Credential───────▶│                │
  │                        │                          │                          │──User.setAttribute─▶│
  │                        │                          │──Delete Challenge───────▶│                │
  │                        │                          │                          │                │
  │                        │───Generate Tokens────────────────────────────────────────────────────▶│
  │                        │◀─Access/Refresh/ID Tokens────────────────────────────────────────────│
  │◀─Token Response────────│                          │                          │                │
```

### Authentication Flow

```
Client                  Provider            AuthenticationService       CredentialManager      Keycloak
  │                        │                          │                          │                │
  │──Auth Challenge────▶│                          │                          │                │
  │                        │───Generate Challenge────▶│                          │                │
  │                        │                          │──Get Credentials────────▶│                │
  │                        │                          │                          │──User.getAttribute─▶│
  │                        │                          │◀─Credential List─────────│◀───────────────│
  │                        │                          │──Store Challenge────────▶│                │
  │                        │◀─Challenge + AllowedCreds│                          │                │
  │◀─Challenge Response────│                          │                          │                │
  │                        │                          │                          │                │
  │                     [User authenticates with device]                         │                │
  │                        │                          │                          │                │
  │──Assertion──────────▶│                          │                          │                │
  │                        │───Verify Assertion──────▶│                          │                │
  │                        │                          │──Get Challenge──────────▶│                │
  │                        │                          │──Find Credential────────▶│                │
  │                        │                          │◀─Credential──────────────│                │
  │                        │                          │                          │                │
  │                        │                          │──Validate ClientData─────│                │
  │                        │                          │──Parse AuthData──────────│                │
  │                        │                          │──Verify Signature────────│                │
  │                        │                          │──Validate SignCount──────│                │
  │                        │                          │                          │                │
  │                        │                          │──Update Credential──────▶│                │
  │                        │                          │                          │──User.setAttribute─▶│
  │                        │                          │──Delete Challenge───────▶│                │
  │                        │                          │                          │                │
  │                        │───Generate Tokens────────────────────────────────────────────────────▶│
  │                        │◀─Access/Refresh/ID Tokens────────────────────────────────────────────│
  │◀─Token Response────────│                          │                          │                │
```

## Module Dependency Graph

```
┌──────────────────────────────────────────────────────────────────┐
│                      Module Dependencies                          │
│                   (Lower → depends on → Higher)                   │
└──────────────────────────────────────────────────────────────────┘

┌─────────────────────┐
│  Provider Module    │  ◀── Entry point, JAX-RS REST endpoints
│  (Provider Layer)   │
└─────────────────────┘
          │
          │ depends on
          ▼
┌─────────────────────┐
│  Service Module     │  ◀── Business logic, WebAuthn workflows
│  (Service Layer)    │
└─────────────────────┘
          │
          │ depends on
          ├──────────────────┬──────────────────┐
          ▼                  ▼                  ▼
┌─────────────────┐  ┌─────────────┐  ┌──────────────┐
│  Model Module   │  │ Util Module │  │ Exception    │
│  (Domain)       │  │ (Helpers)   │  │ Module       │
└─────────────────┘  └─────────────┘  └──────────────┘

External Dependencies (used by Service Layer):
┌──────────────┐  ┌─────────────┐  ┌────────────────┐
│  WebAuthn4J  │  │  Jackson    │  │  Keycloak SPIs │
│              │  │             │  │                │
└──────────────┘  └─────────────┘  └────────────────┘
```

## Layer Responsibilities

### 1. Provider Layer (Provider Module)
**Package**: `com.inventage.keycloak.webauthn.infrastructure.provider`

**Responsibility**:
- Keycloak SPI integration
- REST API endpoint exposure
- Request/response handling
- HTTP status code management
- Error response formatting

**Key Classes**:
- `WebAuthnRealmResourceProvider` - REST endpoint implementation
- `WebAuthnRealmResourceProviderFactory` - SPI factory

**Dependencies**: Service Layer, Model Layer, Exception Layer

### 2. Service Layer (Service Module)
**Package**: `com.inventage.keycloak.webauthn.infrastructure.service`

**Responsibility**:
- Business logic implementation
- WebAuthn protocol workflows
- Cryptographic operations
- State management
- Token generation

**Key Classes**:
- `WebAuthnRegistrationService` - Registration workflow
- `WebAuthnAuthenticationService` - Authentication workflow
- `WebAuthnCredentialManager` - Credential CRUD + challenge management
- `TokenService` - OAuth2/OIDC token generation

**Dependencies**: Model Layer, Util Layer, Exception Layer, External Libraries

### 3. Model Layer (Model Module)
**Packages**:
- `io.inventage.keycloak.custom.webauthn.infrastructure.model`
- DTOs in same package

**Responsibility**:
- Domain entities
- Data transfer objects
- Validation rules
- JSON serialization configuration

**Key Classes**:
- `WebAuthnCredential` - Core credential entity
- `CredentialType` - Enum for credential classification
- Request/Response DTOs (17 classes)

**Dependencies**: None (pure domain layer)

### 4. Util Layer (Util Module)
**Package**: `com.inventage.keycloak.webauthn.util`

**Responsibility**:
- Cross-cutting utilities
- Encoding/decoding operations
- Cryptographic random generation

**Key Classes**:
- `Base64Util` - URL-safe Base64 encoding
- `ChallengeGenerator` - Secure random challenge generation

**Dependencies**: None (utility classes)

### 5. Exception Layer (Exception Module)
**Package**: `com.inventage.keycloak.webauthn.infrastructure.exception`

**Responsibility**:
- Structured error handling
- Error code management
- HTTP status mapping
- Exception hierarchy

**Key Classes**:
- `WebAuthnException` - Base exception (400)
- `RegistrationException` - Registration failures (400/500)
- `InvalidCredentialException` - Authentication failures (401)
- `ChallengeExpiredException` - Expired challenges (401)
- `ValidationException` - Input validation (400)

**Dependencies**: None (exception hierarchy)

## Communication Patterns

### Service → Service Communication
- **Direct Method Calls**: Services instantiate dependencies directly
- **No Interface Abstraction**: Concrete class dependencies
- **Keycloak Session Context**: Passed through constructor

Example:
```java
public class WebAuthnRegistrationService {
    private final WebAuthnCredentialManager credentialManager;

    public WebAuthnRegistrationService(KeycloakSession session) {
        this.credentialManager = new WebAuthnCredentialManager(session);
    }
}
```

### Provider → Service Communication
- **Direct Instantiation**: Provider creates service instances
- **Per-Request Scope**: New service instances per HTTP request
- **Error Translation**: Converts service exceptions to HTTP responses

### Service → Keycloak Communication
- **KeycloakSession**: Primary interface to Keycloak
- **User Attributes**: Credentials stored as JSON in user attributes
- **TokenManager**: OAuth2/OIDC token generation
- **Session Management**: Authentication and client sessions

## Module-Level Invariants

### Provider Module
- All endpoints return JSON responses
- All errors include `error` and `errorDescription` fields
- All endpoints use `@NoCache` annotation
- Session and realm retrieved from `KeycloakSession.getContext()`

### Service Module
- All challenge operations use 5-minute TTL (300 seconds)
- All challenges are 32 bytes (256 bits)
- Credential storage uses JSON serialization
- Sign count validation detects cloned authenticators

### Model Module
- All timestamps use `java.time.Instant`
- All credentials have unique `credentialId`
- Default credential type is `TYPE_PASSWORDLESS`
- JSON property names use camelCase

### Util Module
- All Base64 encoding is URL-safe without padding
- All random generation uses `SecureRandom`
- Utilities are stateless with private constructors

### Exception Module
- All exceptions include `errorCode` and `httpStatusCode`
- HTTP 400 for validation errors
- HTTP 401 for authentication failures
- HTTP 404 for not found errors
- HTTP 500 for internal errors

## Extension Points

### 1. Adding New Credential Types
**Location**: `CredentialType` enum in Model Module

**Steps**:
1. Add new enum value to `CredentialType`
2. Update `fromValue()` method
3. Update registration/authentication services to handle new type
4. Update client-side credential metadata

**Impact**: Service Layer, Model Layer

### 2. Custom Credential Validation
**Location**: `WebAuthnRegistrationService` and `WebAuthnAuthenticationService`

**Extension Points**:
- `validateClientData()` - Custom origin validation
- `parseAttestationObject()` - Custom attestation formats
- `verifySignature()` - Additional signature algorithms
- `validateSignCount()` - Custom clone detection logic

**Impact**: Service Layer

### 3. Alternative Storage Mechanisms
**Location**: `WebAuthnCredentialManager`

**Current**: JSON in user attributes
**Alternatives**:
- JPA entities with custom tables
- External credential storage (Redis, database)
- Encrypted credential storage

**Extension Strategy**:
1. Create `CredentialStorage` interface
2. Implement `UserAttributeCredentialStorage` (current)
3. Implement `JpaCredentialStorage` (alternative)
4. Inject via constructor or factory

**Impact**: Service Layer

### 4. Token Customization
**Location**: `TokenService`

**Extension Points**:
- Custom claims in access tokens
- Different token lifetimes
- Custom scopes
- Additional token types

**Impact**: Service Layer

### 5. New REST Endpoints
**Location**: `WebAuthnRealmResourceProvider`

**Steps**:
1. Add JAX-RS annotated method
2. Create corresponding service method
3. Create request/response DTOs
4. Add error handling

**Impact**: Provider Layer, Service Layer, Model Layer

### 6. Attestation Validation
**Location**: `WebAuthnRegistrationService.parseAttestationObject()`

**Current**: Basic parsing, no attestation validation
**Extensions**:
- FIDO MDS integration for authenticator metadata
- Attestation statement verification
- Certificate chain validation
- Revocation checking

**Impact**: Service Layer

## Security Boundaries

### 1. Input Validation Boundary
**Location**: Provider Layer
- Validates all request parameters
- Rejects malformed JSON
- Validates required fields

### 2. Cryptographic Boundary
**Location**: Service Layer
- Challenge generation (SecureRandom)
- Signature verification (ECDSA/RSA)
- Hash computation (SHA-256)
- CBOR parsing (WebAuthn4J)

### 3. Authentication Boundary
**Location**: Service Layer → Keycloak
- User lookup and verification
- Token generation
- Session creation
- Scope management

### 4. Storage Boundary
**Location**: CredentialManager → Keycloak
- Credential serialization
- User attribute storage
- Challenge cache management

## Performance Characteristics

### Challenge Cache
- **Storage**: In-memory `ConcurrentHashMap`
- **TTL**: 5 minutes (300 seconds)
- **Cleanup**: Lazy cleanup on cache access
- **Scalability**: Per-JVM instance (not clustered)

**Scaling Consideration**:
For multi-node deployments, consider:
- Distributed cache (Infinispan)
- Database-backed challenge storage
- Redis for challenge cache

### Credential Storage
- **Format**: JSON in user attributes
- **Read Performance**: O(1) user attribute lookup
- **Write Performance**: O(1) user attribute update
- **Search Performance**: O(n) for credential lookup across users

**Scaling Consideration**:
For large user bases, consider:
- Indexed credential ID → user ID mapping
- Separate credential table with JPA
- Caching layer for frequently used credentials

### Token Generation
- **Performance**: Depends on Keycloak's TokenManager
- **Session Creation**: Database transaction per authentication
- **Token Signing**: RSA/ECDSA operations

## Testing Strategies

### Unit Testing
**Target**: Service Layer, Util Layer
**Approach**:
- Mock Keycloak dependencies
- Test business logic in isolation
- Validate error handling

### Integration Testing
**Target**: Full stack (Provider → Service → Keycloak)
**Approach**:
- Testcontainers with Keycloak
- Real database (PostgreSQL)
- End-to-end API testing

### Current Coverage
- Unit tests: Service Layer, Util Layer
- Integration tests: Full WebAuthn flows
- Fixtures: Shared test data and mocks

## Technology Stack

### Core Dependencies
- **Keycloak**: 26.0.7
- **WebAuthn4J**: 0.29.3
- **Jackson**: 2.18.2
- **BouncyCastle**: 1.79
- **Jakarta EE**: JAX-RS 3.1.0, CDI 4.0.0

### Build & Packaging
- **Maven**: Multi-module project
- **Maven Shade**: Dependency bundling with relocation
- **Auto-service**: SPI registration

### Testing
- **JUnit Jupiter**: 5.x
- **Mockito**: 5.14.2
- **AssertJ**: 3.27.3
- **Testcontainers**: Keycloak + PostgreSQL

## Design Decisions

See [DESIGN_DECISIONS.md](./DESIGN_DECISIONS.md) for detailed rationale.

## Future Architecture Evolution

### Short-term (v1.1)
- Clustered challenge cache (Infinispan)
- FIDO MDS integration
- Custom authenticator SPI

### Medium-term (v2.0)
- JPA credential storage
- Attestation validation
- Admin UI for credential management

### Long-term (v3.0)
- Passkey synchronization
- Cross-device authentication
- Conditional UI support
