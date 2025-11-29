# WebAuthn Realm API Extension - Low-Level Design Document

## Executive Summary

This document provides a detailed low-level design for implementing a custom Keycloak SPI extension that exposes a WebAuthn-based authentication API for mobile and web clients. The extension implements `RealmResourceProvider` and `RealmResourceProviderFactory` to provide REST endpoints for client registration and authentication without browser-based flows.

**Version**: 1.0.0
**Date**: 2025-11-29
**Target Framework**: Keycloak 26.4.6
**Java Version**: 21+

---

## 1. Requirements Analysis

### Functional Requirements

**FR1**: Client Registration Endpoint
- Register new users via REST API with WebAuthn credentials
- Support public key credential creation
- Store credentials in Keycloak database

**FR2**: Authentication Endpoint
- Authenticate users using WebAuthn assertions
- Return OAuth2 tokens (access token, refresh token, ID token)
- Support multiple credential types per user

**FR3**: Credential Management
- List user's registered credentials
- Delete/revoke specific credentials
- Update credential metadata (name, last used)

**FR4**: WebAuthn Challenge Flow
- Generate registration challenges for new credentials
- Generate authentication challenges
- Validate challenge responses

### Non-Functional Requirements

**NFR1**: Security
- Validate all WebAuthn cryptographic signatures
- Prevent replay attacks
- Enforce HTTPS for production
- Implement rate limiting

**NFR2**: Compatibility
- Support FIDO2/WebAuthn standard
- Compatible with U2F devices
- Support platform authenticators

**NFR3**: Performance
- Challenge generation < 100ms
- Authentication < 500ms
- Minimal database queries

**NFR4**: Extensibility
- Plugin architecture for future authenticators
- Configurable via Keycloak realm settings
- Support custom claim mappers

---

## 2. Architecture Overview

### High-Level Components

```
┌─────────────────────────────────────────────────────────────┐
│                    Mobile/Web Client                         │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│               REST API (JSON over HTTPS)                     │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ POST /realms/{realm}/api/webauthn/register/challenge   │  │
│  │ POST /realms/{realm}/api/webauthn/register/verify      │  │
│  │ POST /realms/{realm}/api/webauthn/auth/challenge       │  │
│  │ POST /realms/{realm}/api/webauthn/auth/verify          │  │
│  │ GET  /realms/{realm}/api/webauthn/credentials          │  │
│  │ DELETE /realms/{realm}/api/webauthn/credentials/{id}   │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│           WebAuthN Realm Resource Provider                  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │   WebAuthnRealmResourceProvider (REST Endpoints)      │  │
│  │   - Registration Handler                              │  │
│  │   - Authentication Handler                            │  │
│  │   - Credential Manager                                │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│           Core Service Layer                                │
│  ┌────────────────────────────────────────────────────┐   │
│  │  WebAuthnRegistrationService                       │   │
│  │  - Challenge generation                            │   │
│  │  - Credential creation & validation                │   │
│  │  - Attestation verification                        │   │
│  └────────────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────────────┐   │
│  │  WebAuthnAuthenticationService                     │   │
│  │  - Challenge generation                            │   │
│  │  - Assertion verification                          │   │
│  │  - Replay attack prevention                        │   │
│  └────────────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────────────┐   │
│  │  WebAuthnCredentialManager                         │   │
│  │  - Credential persistence                          │   │
│  │  - Challenge storage                               │   │
│  │  - Metadata management                             │   │
│  └────────────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────────────┐   │
│  │  TokenService (leverages Keycloak managers)        │   │
│  │  - Token generation                                │   │
│  │  - Protocol mapping                                │   │
│  └────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│           External Libraries                                │
│  ┌────────────────────────────────────────────────────┐   │
│  │  com.webauthn4j:                                   │   │
│  │  - webauthn4j-core (crypto validation)             │   │
│  │  - webauthn4j-metadata (attestation)              │   │
│  └────────────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────────────┐   │
│  │  com.fasterxml.jackson:                            │   │
│  │  - jackson-databind (JSON serialization)           │   │
│  │  - jackson-datatype-jsr310 (date handling)         │   │
│  └────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│           Keycloak Data Layer                               │
│  ┌────────────────────────────────────────────────────┐   │
│  │  UserModel, RealmModel                             │   │
│  │  Custom Credential Storage                         │   │
│  │  Challenge Cache                                   │   │
│  └────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Tech |
|-----------|-----------------|------|
| **RealmResourceProvider** | HTTP routing and endpoint handling | JAX-RS |
| **Registration Service** | Challenge generation and credential creation | webauthn4j |
| **Authentication Service** | Challenge and assertion verification | webauthn4j |
| **Credential Manager** | Persistence and retrieval | Keycloak UserModel |
| **Token Service** | OAuth2 token generation | Keycloak |
| **JSON Serialization** | Request/response marshalling | Jackson |

---

## 3. Data Model

### WebAuthn Credential Storage

Credentials are stored as user attributes in Keycloak:

```
User
├── Attributes
│   ├── webauthn.credentials (JSON array)
│   │   └── Credential Object
│   │       ├── credentialId: String (base64url)
│   │       ├── credentialPublicKey: String (base64url, CBOR)
│   │       ├── attestationObject: String (base64url)
│   │       ├── signCount: Long
│   │       ├── credentialType: String ("public-key")
│   │       ├── transportHints: String[] (usb, ble, nfc, internal)
│   │       ├── metadata: Object
│   │       │   ├── name: String (user-provided name)
│   │       │   ├── createdAt: ISO8601 timestamp
│   │       │   ├── lastUsedAt: ISO8601 timestamp
│   │       │   └── aaguid: String (authenticator AAGUID)
│   │       └── backup: Object
│   │           ├── eligible: Boolean
│   │           └── state: Boolean
│   │
│   └── webauthn.challenges (JSON object - TTL based)
│       └── Challenge Object (per session)
│           ├── challengeValue: String (base64url)
│           ├── purpose: String ("registration" | "authentication")
│           ├── createdAt: Timestamp
│           ├── expiresAt: Timestamp
│           └── sessionId: String
```

### Credential Type Classification

Credentials are classified into two authentication types that determine how authentication is performed:

**TYPE_PASSWORDLESS**
- WebAuthn credential serves as primary authentication factor
- User does NOT need to provide a password to authenticate
- Use cases: Mobile-first apps, enterprise devices with strong biometric enrollment
- Authentication flow: Challenge → WebAuthn assertion → Token generation
- Default type when not specified in registration

**TYPE_TWOFACTOR**
- WebAuthn credential serves as secondary/additional authentication factor
- User must provide BOTH password and WebAuthn credential during authentication
- Use cases: High-security accounts, compliance-driven requirements (PCI-DSS, SOC 2), legacy systems requiring passwords
- Authentication flow: Challenge → WebAuthn assertion + password verification → Token generation

The `type` field is stored with each credential and retrieved during authentication to determine the correct authentication flow to apply.

### JSON Serialization Format

**Credential Storage (User Attribute)**:
```json
{
  "credentialId": "dGVzdC1jcmVkLWlkLWJhc2U2NHVybA",
  "credentialPublicKey": "o2Nrc....",
  "attestationObject": "o2Nmm...",
  "signCount": 0,
  "credentialType": "public-key",
  "transportHints": ["internal"],
  "type": "passwordless",
  "metadata": {
    "name": "My Security Key",
    "createdAt": "2025-11-29T10:30:00Z",
    "lastUsedAt": "2025-11-29T11:45:00Z",
    "aaguid": "00000000-0000-0000-0000-000000000000"
  },
  "backup": {
    "eligible": true,
    "state": false
  }
}
```

**Note**: The `type` field ("passwordless" or "twofactor") indicates how the credential is used during authentication.

---

## 4. API Specifications

### Endpoint 1: Registration Challenge Generation

**Request**:
```http
POST /realms/{realm}/api/webauthn/register/challenge
Content-Type: application/json

{
  "username": "user@example.com",
  "displayName": "User Name",
  "credentialName": "My iPhone"
}
```

**Response** (200 OK):
```json
{
  "sessionId": "uuid-session-id",
  "challenge": "base64url-encoded-challenge",
  "userId": "uuid-user-id",
  "userName": "user@example.com",
  "displayName": "User Name",
  "attestation": "direct",
  "authenticatorSelection": {
    "authenticatorAttachment": "platform",
    "residentKey": "preferred",
    "userVerification": "preferred"
  },
  "timeout": 60000,
  "supportedAlgorithms": [-7, -257]
}
```

### Endpoint 2: Registration Verification

**Request**:
```http
POST /realms/{realm}/api/webauthn/register/verify
Content-Type: application/json

{
  "sessionId": "uuid-session-id",
  "response": {
    "id": "credential-id-base64url",
    "rawId": "credential-raw-id-base64url",
    "response": {
      "clientDataJSON": "base64url-encoded",
      "attestationObject": "base64url-encoded"
    },
    "type": "public-key"
  },
  "credentialName": "My iPhone",
  "type": "passwordless"
}
```

**Parameters**:
- `sessionId` (String, required): Session ID from `/register/challenge` response
- `response` (Object, required): Attestation response from WebAuthn API
  - `id`: Credential ID (base64url)
  - `rawId`: Raw credential ID (base64url)
  - `response.clientDataJSON`: Client data (base64url)
  - `response.attestationObject`: Attestation object (base64url)
  - `type`: Always "public-key"
- `credentialName` (String, required): User-provided credential name
- `type` (String, optional): Credential type - "passwordless" (default) or "twofactor"
  - `passwordless`: WebAuthn as primary authentication factor only
  - `twofactor`: WebAuthn as secondary factor (password + WebAuthn required)

**Response** (200 OK):
```json
{
  "success": true,
  "credentialId": "stored-credential-id",
  "credentialType": "passwordless",
  "message": "Credential registered successfully",
  "accessToken": "eyJhbGciOiJSUzI1NiI...",
  "refreshToken": "eyJhbGciOiJSUzI1NiI...",
  "idToken": "eyJhbGciOiJSUzI1NiI...",
  "expiresIn": 300,
  "tokenType": "bearer"
}
```

**Response Fields**:
- `credentialType`: The stored credential type (passwordless or twofactor)

### Endpoint 3: Authentication Challenge Generation

**Request**:
```http
POST /realms/{realm}/api/webauthn/auth/challenge
Content-Type: application/json

{
  "username": "user@example.com"
}
```

**Response** (200 OK):
```json
{
  "sessionId": "uuid-session-id",
  "challenge": "base64url-encoded-challenge",
  "allowCredentials": [
    {
      "type": "public-key",
      "id": "credential-id-base64url",
      "transports": ["internal"]
    }
  ],
  "timeout": 60000,
  "userVerification": "preferred"
}
```

### Endpoint 4: Authentication Verification

**Request**:
```http
POST /realms/{realm}/api/webauthn/auth/verify
Content-Type: application/json

{
  "sessionId": "uuid-session-id",
  "response": {
    "id": "credential-id-base64url",
    "rawId": "credential-raw-id-base64url",
    "response": {
      "clientDataJSON": "base64url-encoded",
      "authenticatorData": "base64url-encoded",
      "signature": "base64url-encoded"
    },
    "type": "public-key"
  }
}
```

**Response** (200 OK):
```json
{
  "success": true,
  "accessToken": "eyJhbGciOiJSUzI1NiI...",
  "refreshToken": "eyJhbGciOiJSUzI1NiI...",
  "idToken": "eyJhbGciOiJSUzI1NiI...",
  "expiresIn": 300,
  "tokenType": "bearer"
}
```

### Endpoint 5: List Credentials

**Request**:
```http
GET /realms/{realm}/api/webauthn/credentials
Authorization: Bearer {accessToken}
```

**Response** (200 OK):
```json
{
  "credentials": [
    {
      "credentialId": "cred-id-1",
      "credentialName": "My iPhone",
      "credentialType": "public-key",
      "createdAt": "2025-11-29T10:30:00Z",
      "lastUsedAt": "2025-11-29T11:45:00Z",
      "aaguid": "00000000-0000-0000-0000-000000000000"
    }
  ],
  "total": 1
}
```

### Endpoint 6: Delete Credential

**Request**:
```http
DELETE /realms/{realm}/api/webauthn/credentials/{credentialId}
Authorization: Bearer {accessToken}
```

**Response** (204 No Content)

---

## 5. Detailed Sequence Diagrams (PlantUML)

### Sequence 1: Registration Flow
(See WEBAUTHN_SEQUENCE_DIAGRAMS.puml)

### Sequence 2: Authentication Flow
(See WEBAUTHN_SEQUENCE_DIAGRAMS.puml)

---

## 6. Java Class Structure

### Package Organization

```
com.inventage.keycloak.webauthn
├── infrastructure
│   ├── provider
│   │   ├── WebAuthnRealmResourceProvider.java
│   │   └── WebAuthnRealmResourceProviderFactory.java
│   ├── service
│   │   ├── WebAuthnRegistrationService.java
│   │   ├── WebAuthnAuthenticationService.java
│   │   ├── WebAuthnCredentialManager.java
│   │   └── TokenService.java
│   ├── model
│   │   ├── WebAuthnCredential.java
│   │   ├── WebAuthnChallenge.java
│   │   ├── RegistrationRequest.java
│   │   ├── RegistrationResponse.java
│   │   ├── AuthenticationRequest.java
│   │   ├── AuthenticationResponse.java
│   │   └── ErrorResponse.java
│   ├── mapper
│   │   ├── CredentialMapper.java
│   │   └── ChallengeMapper.java
│   └── validation
│       ├── WebAuthnValidator.java
│       └── RateLimiter.java
├── util
│   ├── Base64Util.java
│   ├── ChallengeGenerator.java
│   └── Constants.java
└── exception
    ├── WebAuthnException.java
    ├── InvalidCredentialException.java
    ├── ChallengeExpiredException.java
    └── RegistrationException.java
```

### Core Classes

#### 1. WebAuthnRealmResourceProviderFactory

```java
@AutoService(RealmResourceProviderFactory.class)
public class WebAuthnRealmResourceProviderFactory
    implements RealmResourceProviderFactory {

    public static final String PROVIDER_ID = "webauthn";

    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new WebAuthnRealmResourceProvider(session);
    }

    @Override
    public void init(Config.Scope config) { }

    @Override
    public void postInit(KeycloakSessionFactory factory) { }

    @Override
    public void close() { }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
```

#### 2. WebAuthnRealmResourceProvider

```java
public class WebAuthnRealmResourceProvider implements RealmResourceProvider {

    private final KeycloakSession session;
    private final WebAuthnRegistrationService registrationService;
    private final WebAuthnAuthenticationService authService;
    private final WebAuthnCredentialManager credentialManager;
    private final TokenService tokenService;

    @Path("register/challenge")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response registrationChallenge(
        @RequestBody RegistrationChallengeRequest request) {
        // Implementation
    }

    @Path("register/verify")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response verifyRegistration(
        @RequestBody RegistrationVerifyRequest request) {
        // Implementation
    }

    @Path("auth/challenge")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response authenticationChallenge(
        @RequestBody AuthChallengeRequest request) {
        // Implementation
    }

    @Path("auth/verify")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response verifyAuthentication(
        @RequestBody AuthVerifyRequest request) {
        // Implementation
    }

    @Path("credentials")
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response listCredentials() {
        // Implementation
    }

    @Path("credentials/{credentialId}")
    @DELETE
    public Response deleteCredential(
        @PathParam("credentialId") String credentialId) {
        // Implementation
    }

    @Override
    public void close() { }
}
```

#### 3. WebAuthnRegistrationService

```java
public class WebAuthnRegistrationService {

    private final KeycloakSession session;
    private final WebAuthnCredentialManager credentialManager;
    private final WebAuthn4jService webauthn4j;

    /**
     * Generate challenge for credential creation
     * @param userId Keycloak user ID
     * @param credentialName User-provided name
     * @return Challenge response with public key creation options
     */
    public RegistrationChallengeResponse generateChallenge(
        String userId, String credentialName) throws WebAuthnException {

        // 1. Retrieve user from Keycloak
        UserModel user = getUserModel(userId);

        // 2. Generate random challenge (32 bytes min)
        byte[] challengeBytes = generateRandomChallenge(32);
        String challengeB64 = Base64Util.encodeToString(challengeBytes);

        // 3. Prepare public key creation options
        PublicKeyCredentialCreationOptions options =
            new PublicKeyCredentialCreationOptions();

        // 4. Store challenge in session with TTL (5 minutes)
        storeChallenge(userId, challengeB64, "registration", 300);

        // 5. Build response with public key options
        return buildChallengeResponse(options, user);
    }

    /**
     * Verify and store new credential
     * @param response Client attestation response
     * @return Credential stored successfully
     */
    public void verifyAndStoreCredential(
        RegistrationVerifyRequest response)
        throws InvalidCredentialException, RegistrationException {

        // 1. Retrieve stored challenge
        WebAuthnChallenge challenge = getStoredChallenge(
            response.getSessionId());
        if (challenge.isExpired()) {
            throw new ChallengeExpiredException();
        }

        // 2. Parse client data JSON
        String clientDataJSON = response.getResponse()
            .getClientDataJSON();
        ClientDataJSON clientData = parseClientDataJSON(clientDataJSON);

        // 3. Validate client data
        validateClientData(clientData, challenge);

        // 4. Parse attestation object
        byte[] attestationObjectBytes = Base64Util.decode(
            response.getResponse().getAttestationObject());
        AttestationObject attestationObject =
            parseAttestationObject(attestationObjectBytes);

        // 5. Verify authenticator data
        AuthenticatorData authData =
            attestationObject.getAuthenticatorData();

        // 6. Extract and validate credential public key
        CredentialPublicKey pubKey = extractPublicKey(
            authData.getAttestedCredentialData());

        // 7. Verify attestation (optional for user-supplied devices)
        if (isAttestationRequired()) {
            verifyAttestation(attestationObject);
        }

        // 8. Store credential with metadata
        WebAuthnCredential credential = new WebAuthnCredential();
        credential.setCredentialId(response.getResponse().getId());
        credential.setCredentialPublicKey(pubKey);
        credential.setSignCount(authData.getSignCount());
        credential.setMetadata(buildMetadata(
            response.getCredentialName()));

        credentialManager.storeCredential(
            challenge.getUserId(), credential);

        // 9. Clear challenge
        deleteChallenge(response.getSessionId());
    }

    private void validateClientData(ClientDataJSON clientData,
        WebAuthnChallenge challenge)
        throws InvalidCredentialException {

        // Validate challenge matches
        if (!clientData.getChallenge()
            .equals(challenge.getChallengeValue())) {
            throw new InvalidCredentialException(
                "Challenge mismatch");
        }

        // Validate origin
        if (!isOriginAllowed(clientData.getOrigin())) {
            throw new InvalidCredentialException(
                "Invalid origin");
        }

        // Validate type
        if (!"webauthn.create".equals(clientData.getType())) {
            throw new InvalidCredentialException(
                "Invalid client data type");
        }
    }

    private CredentialPublicKey extractPublicKey(
        AttestedCredentialData credData) {
        // Use webauthn4j to extract public key from credential data
        return credData.getCredentialPublicKey();
    }
}
```

#### 4. WebAuthnAuthenticationService

```java
public class WebAuthnAuthenticationService {

    private final KeycloakSession session;
    private final WebAuthnCredentialManager credentialManager;

    /**
     * Generate challenge for authentication
     * @param username User identifier
     * @return Challenge with allowed credentials
     */
    public AuthenticationChallengeResponse generateChallenge(
        String username) throws WebAuthnException {

        // 1. Find user by username
        UserModel user = findUserByUsername(username);
        if (user == null) {
            // Don't reveal user doesn't exist - return empty allowCredentials
            return buildEmptyChallenge();
        }

        // 2. Generate random challenge (32 bytes)
        byte[] challengeBytes = generateRandomChallenge(32);
        String challengeB64 = Base64Util.encodeToString(challengeBytes);

        // 3. Retrieve user's registered credentials
        List<WebAuthnCredential> credentials =
            credentialManager.getCredentials(user.getId());

        // 4. Store challenge with TTL
        storeChallenge(user.getId(), challengeB64,
            "authentication", 300);

        // 5. Build response with allowed credentials
        return buildChallengeResponse(challengeB64, credentials);
    }

    /**
     * Verify authentication assertion
     * @param request Authentication assertion response
     * @return Authenticated user
     */
    public UserModel verifyAssertion(AuthVerifyRequest request)
        throws InvalidCredentialException {

        // 1. Retrieve and validate challenge
        WebAuthnChallenge challenge = getStoredChallenge(
            request.getSessionId());
        if (challenge.isExpired()) {
            throw new ChallengeExpiredException();
        }

        // 2. Parse client data JSON
        ClientDataJSON clientData = parseClientDataJSON(
            request.getResponse().getClientDataJSON());

        // 3. Validate client data
        validateAuthClientData(clientData, challenge);

        // 4. Decode authenticator data
        byte[] authenticatorDataBytes = Base64Util.decode(
            request.getResponse().getAuthenticatorData());
        AuthenticatorData authData =
            parseAuthenticatorData(authenticatorDataBytes);

        // 5. Decode signature
        byte[] signature = Base64Util.decode(
            request.getResponse().getSignature());

        // 6. Find credential by ID
        String credentialId = request.getResponse().getId();
        WebAuthnCredential credential =
            credentialManager.findCredentialById(credentialId);

        if (credential == null) {
            throw new InvalidCredentialException(
                "Credential not found");
        }

        // 7. Verify signature using stored public key
        verifySignature(signature, authenticatorDataBytes,
            clientData, credential);

        // 8. Prevent replay attacks - validate sign count
        validateSignCount(authData.getSignCount(), credential);

        // 9. Update sign count and last used timestamp
        credential.updateSignCount(authData.getSignCount());
        credential.updateLastUsed();
        credentialManager.updateCredential(credential);

        // 10. Clear challenge
        deleteChallenge(request.getSessionId());

        // 11. Return authenticated user
        return credential.getUser();
    }

    private void verifySignature(byte[] signature,
        byte[] authenticatorData, ClientDataJSON clientData,
        WebAuthnCredential credential)
        throws InvalidCredentialException {

        // Use webauthn4j for signature verification
        byte[] clientDataHash = hashSHA256(
            clientData.toJSON().getBytes());

        byte[] signedData = concatenate(authenticatorDataBytes,
            clientDataHash);

        boolean valid = credential.getPublicKey()
            .verifySignature(signedData, signature);

        if (!valid) {
            throw new InvalidCredentialException(
                "Invalid signature");
        }
    }

    private void validateSignCount(long newSignCount,
        WebAuthnCredential credential)
        throws InvalidCredentialException {

        // Detect cloned authenticator
        if (newSignCount <= credential.getSignCount()) {
            throw new InvalidCredentialException(
                "Sign count validation failed - " +
                "possible authenticator clone");
        }
    }
}
```

#### 5. WebAuthnCredentialManager

```java
public class WebAuthnCredentialManager {

    private final KeycloakSession session;
    private final ObjectMapper mapper;

    /**
     * Store credential as user attribute
     */
    public void storeCredential(String userId,
        WebAuthnCredential credential) {

        UserModel user = session.users()
            .getUserById(userId, getRealm());

        List<WebAuthnCredential> existing = getCredentials(userId);
        existing.add(credential);

        String json = mapper.writeValueAsString(existing);
        user.setSingleAttribute("webauthn.credentials", json);
    }

    /**
     * Retrieve all credentials for user
     */
    public List<WebAuthnCredential> getCredentials(String userId) {
        UserModel user = session.users()
            .getUserById(userId, getRealm());

        String json = user.getFirstAttribute(
            "webauthn.credentials");

        if (json == null) {
            return new ArrayList<>();
        }

        return mapper.readValue(json,
            new TypeReference<List<WebAuthnCredential>>() {});
    }

    /**
     * Find credential by ID
     */
    public WebAuthnCredential findCredentialById(String credentialId) {
        // Implementation
    }

    /**
     * Delete credential
     */
    public void deleteCredential(String userId, String credentialId) {
        // Implementation
    }

    /**
     * Update credential metadata
     */
    public void updateCredential(WebAuthnCredential credential) {
        // Implementation
    }
}
```

#### 6. TokenService

```java
public class TokenService {

    private final KeycloakSession session;
    private final TokenManager tokenManager;

    /**
     * Generate OAuth2 tokens after successful authentication
     */
    public TokenResponse generateTokens(UserModel user,
        ClientModel client, String scope) {

        // 1. Create authentication session
        AuthenticationSessionModel authSession =
            createAuthenticationSession(user, client);

        // 2. Create login session
        UserSessionModel userSession =
            createUserSession(user);

        // 3. Generate access token
        String accessToken = tokenManager.createAccessToken(
            session, user, userSession, client);

        // 4. Generate refresh token
        String refreshToken = tokenManager.createRefreshToken(
            session, user, userSession, client);

        // 5. Generate ID token
        String idToken = tokenManager.createIDToken(
            session, user, userSession, client);

        // 6. Build response
        TokenResponse response = new TokenResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setIdToken(idToken);
        response.setExpiresIn(tokenManager.getAccessTokenLifespan());
        response.setTokenType("bearer");

        return response;
    }
}
```

---

## 7. Code Style Guidelines

### Naming Conventions

**Classes**:
- Suffix with `Service` for business logic: `WebAuthnAuthenticationService`
- Suffix with `Provider` for Keycloak SPI: `WebAuthnRealmResourceProvider`
- Suffix with `Factory` for object creation: `WebAuthnRealmResourceProviderFactory`
- Suffix with `Request`/`Response` for DTOs

**Methods**:
- Use verb-noun pattern: `verifyAssertion()`, `generateChallenge()`
- Prefix question methods with `is` or `has`: `isExpired()`, `hasCredentials()`
- Prefix search methods with `find` or `get`: `findUserByUsername()`, `getCredentials()`

**Constants**:
- UPPER_SNAKE_CASE
- Group related constants in interfaces

### Code Organization

**Method Order**:
1. Constructors
2. Public methods (organized by responsibility)
3. Protected methods
4. Private methods (organized by responsibility)
5. Static utility methods

**Exception Handling**:
```java
try {
    // Primary logic
} catch (WebAuthnException e) {
    logger.error("WebAuthn operation failed", e);
    throw new WebAuthnException("User-friendly message", e);
}
```

### Documentation

**Javadoc Requirements**:
- All public classes and methods must have Javadoc
- Include `@param`, `@return`, `@throws` tags
- Document side effects and security considerations

```java
/**
 * Verifies the authenticator assertion and authenticates the user.
 *
 * <p>This method performs the following validations:
 * <ul>
 *   <li>Challenge matches stored value</li>
 *   <li>Signature verification using stored public key</li>
 *   <li>Sign count validation to prevent cloning</li>
 * </ul>
 *
 * @param request The authentication assertion response from client
 * @return Authenticated UserModel
 * @throws InvalidCredentialException if verification fails
 * @throws ChallengeExpiredException if challenge has expired
 */
public UserModel verifyAssertion(AuthVerifyRequest request)
    throws InvalidCredentialException, ChallengeExpiredException {
    // Implementation
}
```

### Logging

Use SLF4J via JBoss Logging:

```java
private static final Logger LOG =
    Logger.getLogger(WebAuthnAuthenticationService.class);

public void someMethod() {
    LOG.debugf("Verifying assertion for user: %s", userId);
    try {
        // Logic
    } catch (Exception e) {
        LOG.errorf(e, "Failed to verify assertion for user: %s",
            userId);
    }
}
```

### Error Handling

Define custom exceptions:

```java
public class WebAuthnException extends Exception {
    public WebAuthnException(String message) {
        super(message);
    }

    public WebAuthnException(String message, Throwable cause) {
        super(message, cause);
    }
}

public class InvalidCredentialException extends WebAuthnException {
    public InvalidCredentialException(String message) {
        super(message);
    }
}
```

---

## 8. Security Considerations

### HTTPS Enforcement

```java
public Response registrationChallenge(RegistrationChallengeRequest req) {
    // Verify HTTPS in production
    if (isProduction() && !isSecureConnection()) {
        throw new WebAuthnException("HTTPS required");
    }
}
```

### Challenge Validation

- Challenge must be random, 32+ bytes
- Challenge must be validated before processing attestation/assertion
- Challenge TTL: 5 minutes default (configurable)
- Challenges must be one-time use

### Signature Verification

- Use webauthn4j library for cryptographic operations
- Validate signature against stored public key
- Include authenticator data + client data hash in signature

### Replay Attack Prevention

- Validate sign count increments
- Store session ID to prevent challenge reuse
- Implement IP-based rate limiting

### Credential Validation

- Verify public key algorithm support
- Validate credential ID format
- Check for credential type "public-key"

---

## 9. Testing Strategy

### Unit Tests

**Test Classes**:
- `WebAuthnRegistrationServiceTest`
- `WebAuthnAuthenticationServiceTest`
- `WebAuthnCredentialManagerTest`

**Test Scenarios**:
- Valid registration with various authenticators
- Invalid challenge
- Expired challenge
- Invalid signature
- Sign count validation
- Credential management (CRUD)

### Integration Tests

**Setup**: Testcontainers with Keycloak

**Scenarios**:
- Full registration flow
- Full authentication flow
- Multiple credentials per user
- Credential deletion

### Load Testing

- 1000 concurrent registrations
- 5000 concurrent authentications
- Monitor token generation performance

---

## 10. Deployment & Configuration

### Maven Dependencies

```xml
<!-- WebAuthn4j -->
<dependency>
    <groupId>com.webauthn4j</groupId>
    <artifactId>webauthn4j-core</artifactId>
    <version>0.21.0.RELEASE</version>
</dependency>

<!-- Jackson -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.15.2</version>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-jsr310</artifactId>
    <version>2.15.2</version>
    <scope>provided</scope>
</dependency>

<!-- Keycloak -->
<dependency>
    <groupId>org.keycloak</groupId>
    <artifactId>keycloak-core</artifactId>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>org.keycloak</groupId>
    <artifactId>keycloak-server-spi</artifactId>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>org.keycloak</groupId>
    <artifactId>keycloak-services</artifactId>
    <scope>provided</scope>
</dependency>
```

### Configuration

Realm-level configuration via Admin API:

```json
{
  "attributes": {
    "webauthn.attestation.required": "false",
    "webauthn.challenge.ttl": "300",
    "webauthn.allowed.origins": "http://localhost:3000,https://app.example.com",
    "webauthn.rp.id": "example.com",
    "webauthn.rp.name": "Example Application"
  }
}
```

---

## 11. Implementation Roadmap

### Phase 1: Foundation (Week 1-2)
- [ ] Model classes (DTOs)
- [ ] Exception hierarchy
- [ ] Challenge storage
- [ ] Basic REST endpoints

### Phase 2: Core Services (Week 2-3)
- [ ] Registration service with webauthn4j integration
- [ ] Authentication service with signature verification
- [ ] Credential manager

### Phase 3: Integration (Week 3-4)
- [ ] Token generation (Keycloak integration)
- [ ] Error handling and validation
- [ ] Security hardening

### Phase 4: Testing (Week 4-5)
- [ ] Unit tests
- [ ] Integration tests
- [ ] Load testing

### Phase 5: Documentation & Hardening (Week 5)
- [ ] API documentation (OpenAPI/Swagger)
- [ ] Rate limiting
- [ ] Audit logging

---

## 12. References

- [FIDO2/WebAuthn Specification](https://www.w3.org/TR/webauthn-2/)
- [WebAuthn4j Documentation](https://webauthn4j.github.io/webauthn4j/)
- [Keycloak SPI Documentation](https://www.keycloak.org/docs/latest/server_development/)
- [Jackson Documentation](https://github.com/FasterXML/jackson)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)

---

**Document Status**: Ready for Development
**Last Updated**: 2025-11-29
**Next Phase**: Implementation
