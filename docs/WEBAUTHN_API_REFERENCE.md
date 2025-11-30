# Keycloak WebAuthn API Reference

Complete REST API documentation for WebAuthn (FIDO2) passwordless authentication in Keycloak.

## Table of Contents

- [Overview](#overview)
- [Authentication](#authentication)
- [Base URL](#base-url)
- [Common Headers](#common-headers)
- [Endpoints](#endpoints)
  - [Health Check](#health-check)
  - [Registration Flow](#registration-flow)
  - [Authentication Flow](#authentication-flow)
  - [Credential Management](#credential-management)
- [Data Models](#data-models)
- [Error Codes](#error-codes)
- [Rate Limiting](#rate-limiting)

## Overview

The Keycloak WebAuthn API enables passwordless authentication and two-factor authentication using WebAuthn/FIDO2 standards. It supports:

- **Passwordless Authentication**: Security keys, platform authenticators, biometrics
- **Two-Factor Authentication**: WebAuthn as a second factor after password
- **Credential Management**: List and delete registered credentials
- **Token-Based Access**: JWT access and refresh tokens

### Key Features

- ✅ WebAuthn Level 2 compliant
- ✅ Support for ES256 and RS256 algorithms
- ✅ Challenge-based verification (5-minute TTL)
- ✅ Sign count validation (clone detection)
- ✅ Cross-platform authenticator support
- ✅ Credential metadata tracking

## Authentication

Most endpoints are **public** and don't require authentication:
- Registration challenge/verify
- Authentication challenge/verify

**Protected endpoints** require a Bearer token in the `Authorization` header:
- GET `/credentials` - List credentials
- DELETE `/credentials/{credentialId}` - Delete credential

### Obtaining Tokens

Tokens are issued upon successful:
1. **Registration** - After verifying attestation (`POST /register/verify`)
2. **Authentication** - After verifying assertion (`POST /auth/verify`)

```http
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

## Base URL

All API endpoints are relative to:

```
/realms/{realm}/api/webauthn
```

**Examples:**
- Production: `https://keycloak.example.com/realms/master/api/webauthn`
- Development: `http://localhost:8080/realms/master/api/webauthn`

Replace `{realm}` with your Keycloak realm name (e.g., `master`, `myrealm`).

## Common Headers

### Request Headers

```http
Content-Type: application/json
Accept: application/json
Authorization: Bearer <token>  # For protected endpoints only
```

### Response Headers

```http
Content-Type: application/json
Cache-Control: no-cache, no-store, must-revalidate
```

---

## Endpoints

### Health Check

Check if the WebAuthn service is operational.

**Endpoint:** `GET /`

**Authentication:** None

**Response:** `200 OK`

```json
{
  "status": "ok",
  "service": "webauthn",
  "realm": "master"
}
```

**Use Cases:**
- Monitoring and health checks
- Load balancer health probes
- Service availability verification

---

## Registration Flow

### 1. Generate Registration Challenge

Generate a cryptographic challenge for credential registration.

**Endpoint:** `POST /register/challenge`

**Authentication:** None

**Request Body:**

```json
{
  "username": "user@example.com",
  "displayName": "John Doe",
  "credentialName": "My Security Key",
  "type": "passwordless"
}
```

**Request Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | string | Yes | User's username or email |
| `displayName` | string | No | User's display name |
| `credentialName` | string | No | User-provided name for the credential |
| `type` | string | No | `passwordless` or `twofactor` (default: `passwordless`) |

**Response:** `200 OK`

```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "challenge": "cGxlYXNlX2RvX25vdF91c2VfdGhpc19jaGFsbGVuZ2U",
  "user": {
    "id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
    "name": "user@example.com",
    "displayName": "John Doe"
  },
  "rp": {
    "name": "Keycloak",
    "id": "keycloak.example.com"
  },
  "pubKeyCredParams": [
    { "type": "public-key", "alg": -7 },
    { "type": "public-key", "alg": -257 }
  ],
  "timeout": 60000,
  "attestation": "direct",
  "authenticatorSelection": {
    "authenticatorAttachment": "platform",
    "requireResidentKey": false,
    "userVerification": "preferred"
  }
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `sessionId` | string (UUID) | Session ID for verification (5 min TTL) |
| `challenge` | string | Base64url-encoded challenge |
| `user` | object | User information |
| `rp` | object | Relying party (server) information |
| `pubKeyCredParams` | array | Supported algorithms (-7=ES256, -257=RS256) |
| `timeout` | integer | Challenge timeout in milliseconds |
| `attestation` | string | Attestation preference |
| `authenticatorSelection` | object | Authenticator selection criteria |

**Error Responses:**

| Status | Error Code | Description |
|--------|------------|-------------|
| 400 | `VALIDATION_ERROR` | Username is required |
| 404 | `USER_NOT_FOUND` | User not found |
| 500 | `INTERNAL_ERROR` | Server error |

---

### 2. Verify Registration

Verify the attestation response and store the credential.

**Endpoint:** `POST /register/verify`

**Authentication:** None

**Request Body:**

```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "response": {
    "id": "AaFdkcD4SuPjF-jwUoRwH8-ZHuY5RW46fsZmIN080rI",
    "rawId": "AaFdkcD4SuPjF-jwUoRwH8-ZHuY5RW46fsZmIN080rI",
    "response": {
      "clientDataJSON": "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIi...",
      "attestationObject": "o2NmbXRkbm9uZWdhdHRTdG10oGhhdXRoRGF0YVik..."
    },
    "type": "public-key"
  },
  "credentialName": "My Security Key",
  "type": "passwordless"
}
```

**Request Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `sessionId` | string | Yes | Session ID from challenge |
| `response` | object | Yes | WebAuthn attestation response |
| `response.id` | string | Yes | Base64url credential ID |
| `response.rawId` | string | Yes | Base64url raw credential ID |
| `response.response.clientDataJSON` | string | Yes | Base64url client data |
| `response.response.attestationObject` | string | Yes | Base64url attestation object |
| `response.type` | string | Yes | Must be "public-key" |
| `credentialName` | string | No | User-provided credential name |
| `type` | string | No | `passwordless` or `twofactor` |

**Response:** `200 OK`

```json
{
  "success": true,
  "credentialId": "AaFdkcD4SuPjF-jwUoRwH8-ZHuY5RW46fsZmIN080rI",
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 300,
  "tokenType": "Bearer"
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `success` | boolean | Registration success status |
| `credentialId` | string | Base64url-encoded credential ID |
| `accessToken` | string | JWT access token |
| `refreshToken` | string | JWT refresh token |
| `expiresIn` | integer | Token expiration in seconds |
| `tokenType` | string | Token type ("Bearer") |

**Error Responses:**

| Status | Error Code | Description |
|--------|------------|-------------|
| 400 | `VALIDATION_ERROR` | Invalid request data |
| 401 | `CHALLENGE_EXPIRED` | Challenge expired or not found |
| 401 | `VERIFICATION_FAILED` | Credential verification failed |
| 500 | `INTERNAL_ERROR` | Server error |

---

## Authentication Flow

### 1. Generate Authentication Challenge

Generate a challenge for authentication.

**Endpoint:** `POST /auth/challenge`

**Authentication:** None

**Request Body:**

```json
{
  "username": "user@example.com"
}
```

**Request Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | string | Yes | User's username or email |

**Response:** `200 OK`

```json
{
  "sessionId": "650e8400-e29b-41d4-a716-446655440001",
  "challenge": "YW5vdGhlcl9zZWN1cmVfY2hhbGxlbmdl",
  "timeout": 60000,
  "rpId": "keycloak.example.com",
  "userVerification": "preferred",
  "allowCredentials": [
    {
      "type": "public-key",
      "id": "AaFdkcD4SuPjF-jwUoRwH8-ZHuY5RW46fsZmIN080rI",
      "transports": ["usb", "nfc"]
    }
  ]
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `sessionId` | string (UUID) | Session ID for verification (5 min TTL) |
| `challenge` | string | Base64url-encoded challenge |
| `timeout` | integer | Challenge timeout in milliseconds |
| `rpId` | string | Relying party identifier |
| `userVerification` | string | User verification requirement |
| `allowCredentials` | array | List of allowed credentials |

**Error Responses:**

| Status | Error Code | Description |
|--------|------------|-------------|
| 400 | `VALIDATION_ERROR` | Username is required |
| 404 | `USER_NOT_FOUND` | User not found |
| 500 | `INTERNAL_ERROR` | Server error |

---

### 2. Verify Authentication

Verify the assertion and authenticate the user.

**Endpoint:** `POST /auth/verify`

**Authentication:** None

**Request Body:**

```json
{
  "sessionId": "650e8400-e29b-41d4-a716-446655440001",
  "response": {
    "id": "AaFdkcD4SuPjF-jwUoRwH8-ZHuY5RW46fsZmIN080rI",
    "rawId": "AaFdkcD4SuPjF-jwUoRwH8-ZHuY5RW46fsZmIN080rI",
    "response": {
      "clientDataJSON": "eyJ0eXBlIjoid2ViYXV0aG4uZ2V0Ii...",
      "authenticatorData": "SZYN5YgOjGh0NBcPZHZgW4_krr...",
      "signature": "MEUCIQC3EcfmAKjlHxL7wLGOHBZs4FG4...",
      "userHandle": "YTBlZWJjOTktOWMwYi00ZWY4LWJiNmQtNmJiOWJkMzgwYTEx"
    },
    "type": "public-key"
  }
}
```

**Request Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `sessionId` | string | Yes | Session ID from challenge |
| `response` | object | Yes | WebAuthn assertion response |
| `response.id` | string | Yes | Base64url credential ID |
| `response.rawId` | string | Yes | Base64url raw credential ID |
| `response.response.clientDataJSON` | string | Yes | Base64url client data |
| `response.response.authenticatorData` | string | Yes | Base64url authenticator data |
| `response.response.signature` | string | Yes | Base64url signature |
| `response.response.userHandle` | string | No | Base64url user handle |
| `response.type` | string | Yes | Must be "public-key" |

**Response:** `200 OK`

```json
{
  "success": true,
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 300,
  "tokenType": "Bearer",
  "userId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
  "username": "user@example.com"
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `success` | boolean | Authentication success status |
| `accessToken` | string | JWT access token |
| `refreshToken` | string | JWT refresh token |
| `expiresIn` | integer | Token expiration in seconds |
| `tokenType` | string | Token type ("Bearer") |
| `userId` | string | Authenticated user ID |
| `username` | string | Authenticated username |

**Error Responses:**

| Status | Error Code | Description |
|--------|------------|-------------|
| 400 | `VALIDATION_ERROR` | Invalid request data |
| 401 | `CHALLENGE_EXPIRED` | Challenge expired or not found |
| 401 | `INVALID_CREDENTIAL` | Credential not found or invalid |
| 401 | `SIGN_COUNT_ERROR` | Possible authenticator clone detected |
| 500 | `INTERNAL_ERROR` | Server error |

---

## Credential Management

### List Credentials

Get all registered credentials for the authenticated user.

**Endpoint:** `GET /credentials`

**Authentication:** Required (Bearer token)

**Request Headers:**

```http
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Response:** `200 OK`

```json
{
  "credentials": [
    {
      "credentialId": "AaFdkcD4SuPjF-jwUoRwH8-ZHuY5RW46fsZmIN080rI",
      "credentialName": "My Security Key",
      "type": "passwordless",
      "createdAt": "2025-01-15T10:30:00Z",
      "lastUsedAt": "2025-01-20T14:22:00Z",
      "aaguid": "2fc0579f-8113-47ea-b116-bb5a8db9202a",
      "signCount": 42,
      "transportHints": ["usb", "nfc"]
    },
    {
      "credentialId": "BbGelcE5TvQkG-kxVpSxI9-aIvZ6SX57gtanJO191sJ",
      "credentialName": "YubiKey 5C",
      "type": "twofactor",
      "createdAt": "2025-01-10T08:15:00Z",
      "lastUsedAt": "2025-01-19T16:45:00Z",
      "aaguid": "cb69481e-8ff7-4039-93ec-0a2729a154a8",
      "signCount": 156,
      "transportHints": ["usb"]
    }
  ],
  "total": 2
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `credentials` | array | List of credential objects |
| `credentials[].credentialId` | string | Base64url credential ID |
| `credentials[].credentialName` | string | User-provided name |
| `credentials[].type` | string | `passwordless` or `twofactor` |
| `credentials[].createdAt` | string | Creation timestamp (ISO 8601) |
| `credentials[].lastUsedAt` | string | Last used timestamp (ISO 8601) |
| `credentials[].aaguid` | string | Authenticator model identifier |
| `credentials[].signCount` | integer | Current signature counter |
| `credentials[].transportHints` | array | Supported transports |
| `total` | integer | Total credential count |

**Error Responses:**

| Status | Error Code | Description |
|--------|------------|-------------|
| 401 | `UNAUTHORIZED` | Authentication required |
| 500 | `INTERNAL_ERROR` | Server error |

---

### Delete Credential

Remove a credential from the user's account.

**Endpoint:** `DELETE /credentials/{credentialId}`

**Authentication:** Required (Bearer token)

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `credentialId` | string | Base64url-encoded credential ID |

**Request Headers:**

```http
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Response:** `204 No Content`

**Error Responses:**

| Status | Error Code | Description |
|--------|------------|-------------|
| 400 | `VALIDATION_ERROR` | Credential ID is required |
| 401 | `UNAUTHORIZED` | Authentication required |
| 404 | `CREDENTIAL_NOT_FOUND` | Credential not found |
| 500 | `INTERNAL_ERROR` | Server error |

---

## Data Models

### WebAuthnCredential

Complete credential model stored in Keycloak.

```json
{
  "credentialId": "string",
  "credentialPublicKey": "string",
  "attestationObject": "string",
  "signCount": 0,
  "credentialType": "public-key",
  "transportHints": ["usb", "nfc"],
  "type": "passwordless",
  "metadata": {
    "name": "My Security Key",
    "createdAt": "2025-01-15T10:30:00Z",
    "lastUsedAt": "2025-01-20T14:22:00Z",
    "aaguid": "2fc0579f-8113-47ea-b116-bb5a8db9202a"
  },
  "backup": {
    "eligible": true,
    "state": false
  }
}
```

### Challenge Storage

Challenges are stored in-memory with TTL.

```json
{
  "sessionId": "uuid",
  "userId": "uuid",
  "challenge": "base64url-string",
  "type": "registration|authentication",
  "expiresAt": "ISO-8601-timestamp"
}
```

**Default TTL:** 5 minutes (300 seconds)

---

## Error Codes

### Common Error Codes

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `VALIDATION_ERROR` | 400 | Request validation failed |
| `USER_NOT_FOUND` | 404 | User not found in realm |
| `CHALLENGE_EXPIRED` | 401 | Challenge expired or not found |
| `INVALID_CREDENTIAL` | 401 | Credential not found or invalid |
| `VERIFICATION_FAILED` | 401 | Attestation/assertion verification failed |
| `SIGN_COUNT_ERROR` | 401 | Sign count validation failed (clone detected) |
| `CREDENTIAL_NOT_FOUND` | 404 | Credential not found |
| `DUPLICATE_CREDENTIAL` | 409 | Credential already exists |
| `UNAUTHORIZED` | 401 | Authentication required |
| `INTERNAL_ERROR` | 500 | Internal server error |
| `STORAGE_ERROR` | 500 | Credential storage failed |
| `RETRIEVAL_ERROR` | 500 | Credential retrieval failed |

### Error Response Format

```json
{
  "error": "ERROR_CODE",
  "error_description": "Human-readable description",
  "timestamp": 1706270400000
}
```

---

## Rate Limiting

**Current Status:** No rate limiting implemented

**Recommendations for Production:**

- **Challenge endpoints**: 10 requests/minute per IP
- **Verify endpoints**: 5 requests/minute per session
- **Credential management**: 20 requests/minute per user

---

## See Also

- [OpenAPI Specification](/docs/openapi.yaml)
- [Code Examples](/docs/API_EXAMPLES.md)
- [WebAuthn Implementation Guide](/docs/WEBAUTHN_IMPLEMENTATION_GUIDE.md)
