# WebAuthn Credential Type - Quick Reference Guide

## 🎯 What Changed?

The WebAuthn architecture now supports **Credential Type Classification** - distinguishing between:
- **PASSWORDLESS**: WebAuthn as primary auth factor (no password needed)
- **TWOFACTOR**: WebAuthn as secondary auth factor (password + WebAuthn required)

## 📍 Where to Find Changes

### 1. CredentialType Enum
- **Location**: `WEBAUTHN_IMPLEMENTATION_GUIDE.md` (Section 3)
- **What**: New enum with TYPE_PASSWORDLESS and TYPE_TWOFACTOR
- **Code**: 62 lines
- **Key Method**: `CredentialType.fromValue(String)`

### 2. Registration API - Type Parameter
- **Location**: `WEBAUTHN_EXTENSION_DESIGN.md` (Section 4, Endpoint 2)
- **What**: POST /register/verify now accepts `type` parameter
- **Example**: `"type": "passwordless"`

### 3. Complete Registration Flow
- **Location**: `WEBAUTHN_SEQUENCE_DIAGRAMS.puml` (Diagram 2)
- **What**: Expanded sequence showing type classification
- **Key Step**: classifyCredentialType() → credential.setType()

### 4. Service Implementation
- **Location**: `WEBAUTHN_IMPLEMENTATION_GUIDE.md` (Section 6)
- **What**: Complete WebAuthnRegistrationService with type handling
- **Key Code**: 
  ```java
  credential.setType(credentialType);
  credentialManager.storeCredential(userId, credential);
  ```

### 5. Data Model Extension
- **Location**: `WEBAUTHN_IMPLEMENTATION_GUIDE.md` (Section 3)
- **What**: WebAuthnCredential now has `@JsonProperty("type")`
- **Storage**: Persists to Keycloak user attributes

## 🔄 The Complete Flow

### Registration with Type
```
1. Client: POST /register/challenge { type: "passwordless" }
2. Server: generateChallenge() → returns challenge
3. Client: navigator.credentials.create()
4. Client: POST /register/verify { type: "passwordless", response: {...} }
5. Server: classifyCredentialType("passwordless") → TYPE_PASSWORDLESS
6. Server: credential.setType(CredentialType.TYPE_PASSWORDLESS)
7. Server: Store credential with type in Keycloak user attributes
8. Keycloak Attribute:
   {
     "credentialId": "...",
     "type": "passwordless",
     "metadata": {...}
   }
```

### Authentication Uses Type
```
1. Authentication Service retrieves credential from user
2. Checks credential.getType()
3. If TYPE_PASSWORDLESS: verify only WebAuthn assertion
4. If TYPE_TWOFACTOR: verify password + WebAuthn assertion
5. Generate appropriate tokens
```

## 📋 Key Additions

### New Classes
- `CredentialType` enum - 2 values: TYPE_PASSWORDLESS, TYPE_TWOFACTOR
- `RegistrationVerifyRequest` - Request DTO with type field
- `AttestationResponse` - Separated from assertion response
- `AttestationResponseData` - Response data structure

### Modified Classes
- `WebAuthnCredential` - Added type field + getter/setter
- `RegistrationChallengeRequest` - Added type field

### New Methods
- `CredentialType.fromValue(String)` - Parse from string
- `WebAuthnCredential.getType()` - Returns enum
- `WebAuthnCredential.setType(CredentialType)` - Sets enum
- `WebAuthnCredential.setTypeFromString(String)` - Parse and set
- `WebAuthnCredential.getTypeAsString()` - Returns string value
- `WebAuthnRegistrationService.classifyCredentialType(String)` - Type classification

## 💾 JSON Storage Format

### Before
```json
{
  "credentialId": "...",
  "credentialPublicKey": "...",
  "signCount": 0,
  "metadata": {...}
}
```

### After
```json
{
  "credentialId": "...",
  "credentialPublicKey": "...",
  "signCount": 0,
  "type": "passwordless",
  "metadata": {...}
}
```

## 📊 API Endpoint Changes

### POST /register/verify
**New Request Parameter**:
```json
{
  "sessionId": "...",
  "response": { /* attestation */ },
  "credentialName": "...",
  "type": "passwordless"  // NEW
}
```

**New Response Field**:
```json
{
  "success": true,
  "credentialId": "...",
  "credentialType": "passwordless",  // NEW
  "accessToken": "...",
  ...
}
```

## ✅ What's Now Possible

With credential type persistence:

1. **Mixed Authentication**: Users can have both PASSWORDLESS and TWOFACTOR credentials
2. **Flexible Auth Flows**: Different flows for different credential types
3. **Compliance**: Support compliance scenarios (password + WebAuthn)
4. **Passwordless**: Support passwordless authentication
5. **Audit Trail**: Type stored with credential for compliance logging

## 🚀 For Implementation

### Steps to Implement
1. Add CredentialType enum class
2. Add type field to WebAuthnCredential
3. Add RegistrationVerifyRequest DTO
4. Implement classifyCredentialType() in registration service
5. Update credential storage to persist type
6. Update authentication service to read and use type

### Testing Checklist
- [ ] CredentialType.fromValue() parses correctly
- [ ] Registration with type="passwordless" works
- [ ] Registration with type="twofactor" works
- [ ] Type persists in Keycloak user attributes
- [ ] Authentication service retrieves type
- [ ] Different auth flows for different types

## 📚 Related Documents

- **Full Details**: `WEBAUTHN_REVAMP_SUMMARY.md`
- **Implementation Code**: `WEBAUTHN_IMPLEMENTATION_GUIDE.md` (Section 6)
- **API Specs**: `WEBAUTHN_EXTENSION_DESIGN.md` (Section 4)
- **Sequence Flows**: `WEBAUTHN_SEQUENCE_DIAGRAMS.puml` (Diagram 2)
- **Feature Overview**: `WEBAUTHN_README.md` (🏷️ Credential Types)

---

**Status**: ✅ Complete and Ready for Implementation
**Total Changes**: +675 lines across 4 documents
**Key Achievement**: Complete credential type persistence for passwordless and two-factor authentication
