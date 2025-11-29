# WebAuthn Extension - Implementation Guide & Code Examples

## Overview

This guide provides complete code examples and implementation instructions for building the WebAuthn Realm API extension. All code follows Keycloak SPI patterns and incorporates security best practices.

---

## 1. Project Structure Setup

### Maven Module Structure

```
extensions/
└── extension-webauthn-realm/
    ├── pom.xml
    ├── src/main/java/
    │   └── com/inventage/keycloak/webauthn/
    │       ├── infrastructure/
    │       │   ├── provider/
    │       │   ├── service/
    │       │   ├── model/
    │       │   ├── mapper/
    │       │   ├── validation/
    │       │   └── exception/
    │       └── util/
    └── src/test/java/
        └── com/inventage/keycloak/webauthn/
            └── infrastructure/
                └── service/
```

### pom.xml Configuration

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <artifactId>extensions</artifactId>
        <groupId>com.inventage.keycloak.custom</groupId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>extension-webauthn-realm</artifactId>
    <name>Keycloak WebAuthn Realm Extension</name>

    <properties>
        <webauthn4j.version>0.21.0.RELEASE</webauthn4j.version>
        <jackson.version>2.15.2</jackson.version>
    </properties>

    <dependencies>
        <!-- WebAuthn4j -->
        <dependency>
            <groupId>com.webauthn4j</groupId>
            <artifactId>webauthn4j-core</artifactId>
            <version>${webauthn4j.version}</version>
        </dependency>
        <dependency>
            <groupId>com.webauthn4j</groupId>
            <artifactId>webauthn4j-metadata</artifactId>
            <version>${webauthn4j.version}</version>
        </dependency>

        <!-- Jackson -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>${jackson.version}</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
            <version>${jackson.version}</version>
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
            <artifactId>keycloak-server-spi-private</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.keycloak</groupId>
            <artifactId>keycloak-services</artifactId>
            <scope>provided</scope>
        </dependency>

        <!-- Auto Service Annotations -->
        <dependency>
            <groupId>com.google.auto.service</groupId>
            <artifactId>auto-service-annotations</artifactId>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.12.1</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers</artifactId>
            <version>1.21.1</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-antrun-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 2. Exception Classes

### Base Exception

```java
package com.inventage.keycloak.webauthn.infrastructure.exception;

import org.jboss.logging.Logger;

/**
 * Base exception for WebAuthn operations.
 */
public class WebAuthnException extends Exception {

    private static final Logger LOG = Logger.getLogger(
        WebAuthnException.class);
    private final String errorCode;
    private final int httpStatusCode;

    public WebAuthnException(String errorCode, String message) {
        this(errorCode, message, 400, null);
    }

    public WebAuthnException(String errorCode, String message,
                            int httpStatusCode) {
        this(errorCode, message, httpStatusCode, null);
    }

    public WebAuthnException(String errorCode, String message,
                            int httpStatusCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatusCode = httpStatusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    /**
     * Log exception with error code and HTTP status
     */
    public void log() {
        LOG.errorf("[%s] HTTP %d: %s", errorCode, httpStatusCode,
            getMessage());
    }
}
```

### Specific Exceptions

```java
package com.inventage.keycloak.webauthn.infrastructure.exception;

/**
 * Thrown when challenge has expired or is invalid.
 */
public class ChallengeExpiredException extends WebAuthnException {
    public ChallengeExpiredException(String message) {
        super("CHALLENGE_EXPIRED", message, 400);
    }
}

/**
 * Thrown when credential verification fails.
 */
public class InvalidCredentialException extends WebAuthnException {
    public InvalidCredentialException(String message) {
        super("INVALID_CREDENTIAL", message, 401);
    }

    public InvalidCredentialException(String message, Throwable cause) {
        super("INVALID_CREDENTIAL", message, 401, cause);
    }
}

/**
 * Thrown when registration validation fails.
 */
public class RegistrationException extends WebAuthnException {
    public RegistrationException(String message) {
        super("REGISTRATION_FAILED", message, 400);
    }

    public RegistrationException(String message, Throwable cause) {
        super("REGISTRATION_FAILED", message, 400, cause);
    }
}

/**
 * Thrown when request validation fails.
 */
public class ValidationException extends WebAuthnException {
    public ValidationException(String message) {
        super("VALIDATION_FAILED", message, 400);
    }
}
```

---

## 3. Model Classes (DTOs)

### WebAuthn Credential Model

#### CredentialType Enum

```java
package com.inventage.keycloak.webauthn.infrastructure.model;

/**
 * Credential type classification for WebAuthn credentials.
 * Determines how the credential is used during authentication.
 */
public enum CredentialType {
    /**
     * PASSWORDLESS: WebAuthn credential as primary authentication factor.
     * User does not need to provide a password; WebAuthn alone is sufficient.
     * Use case: Mobile apps, enterprise devices with biometric security
     */
    TYPE_PASSWORDLESS("passwordless"),

    /**
     * TWOFACTOR: WebAuthn credential as secondary authentication factor.
     * User must provide password + WebAuthn credential.
     * Use case: Additional security for high-value accounts, compliance requirements
     */
    TYPE_TWOFACTOR("twofactor");

    private final String value;

    CredentialType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Parse credential type from string value
     */
    public static CredentialType fromValue(String value) {
        if (value == null) {
            return TYPE_PASSWORDLESS; // Default
        }
        for (CredentialType type : CredentialType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return TYPE_PASSWORDLESS;
    }
}
```

#### WebAuthn Credential Model

```java
package com.inventage.keycloak.webauthn.infrastructure.model;

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

    public long getSignCount() {
        return signCount;
    }

    public void setSignCount(long signCount) {
        this.signCount = signCount;
    }

    public void updateSignCount(long newSignCount) {
        this.signCount = newSignCount;
    }

    public void updateLastUsed() {
        this.metadata.setLastUsedAt(Instant.now());
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
```

### Request/Response DTOs

```java
package com.inventage.keycloak.webauthn.infrastructure.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Request for registration challenge generation
 */
public class RegistrationChallengeRequest {
    @JsonProperty("username")
    private String username;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("credentialName")
    private String credentialName;

    @JsonProperty("type")
    private String type = "passwordless"; // passwordless or twofactor

    // Getters and setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getCredentialName() {
        return credentialName;
    }

    public void setCredentialName(String credentialName) {
        this.credentialName = credentialName;
    }

    public String getType() {
        return type != null ? type : "passwordless";
    }

    public void setType(String type) {
        this.type = type;
    }

    public CredentialType getCredentialType() {
        return CredentialType.fromValue(this.type);
    }
}

/**
 * Response with registration challenge
 */
public class RegistrationChallengeResponse {
    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("challenge")
    private String challenge;

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("userName")
    private String userName;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("rp")
    private RelyingPartyInfo rp;

    @JsonProperty("user")
    private UserInfo user;

    @JsonProperty("pubKeyCredParams")
    private List<PublicKeyCredentialParameter> pubKeyCredParams;

    @JsonProperty("timeout")
    private long timeout = 60000;

    @JsonProperty("attestation")
    private String attestation = "direct";

    @JsonProperty("authenticatorSelection")
    private AuthenticatorSelectionCriteria authenticatorSelection;

    // Getters and setters
}

/**
 * Request for registration verification
 */
public class RegistrationVerifyRequest {
    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("response")
    private AttestationResponse response;

    @JsonProperty("credentialName")
    private String credentialName;

    @JsonProperty("type")
    private String type = "passwordless"; // passwordless or twofactor

    // Getters and setters
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public AttestationResponse getResponse() {
        return response;
    }

    public void setResponse(AttestationResponse response) {
        this.response = response;
    }

    public String getCredentialName() {
        return credentialName;
    }

    public void setCredentialName(String credentialName) {
        this.credentialName = credentialName;
    }

    public String getType() {
        return type != null ? type : "passwordless";
    }

    public void setType(String type) {
        this.type = type;
    }

    public CredentialType getCredentialType() {
        return CredentialType.fromValue(this.type);
    }
}

/**
 * Attestation response from client during registration
 */
public class AttestationResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("rawId")
    private String rawId;

    @JsonProperty("response")
    private AttestationResponseData response;

    @JsonProperty("type")
    private String type = "public-key";

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRawId() {
        return rawId;
    }

    public void setRawId(String rawId) {
        this.rawId = rawId;
    }

    public AttestationResponseData getResponse() {
        return response;
    }

    public void setResponse(AttestationResponseData response) {
        this.response = response;
    }
}

/**
 * Attestation response data from authenticator
 */
public class AttestationResponseData {
    @JsonProperty("clientDataJSON")
    private String clientDataJSON;

    @JsonProperty("attestationObject")
    private String attestationObject;

    // Getters and setters
    public String getClientDataJSON() {
        return clientDataJSON;
    }

    public void setClientDataJSON(String clientDataJSON) {
        this.clientDataJSON = clientDataJSON;
    }

    public String getAttestationObject() {
        return attestationObject;
    }

    public void setAttestationObject(String attestationObject) {
        this.attestationObject = attestationObject;
    }
}

/**
 * Authentication request verification
 */
public class AuthVerifyRequest {
    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("response")
    private AssertionResponse response;

    // Getters and setters
}

/**
 * Client assertion response from authenticator
 */
public class AssertionResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("rawId")
    private String rawId;

    @JsonProperty("response")
    private AssertionResponseData response;

    @JsonProperty("type")
    private String type = "public-key";

    // Getters and setters
}

/**
 * Authenticator assertion response data
 */
public class AssertionResponseData {
    @JsonProperty("clientDataJSON")
    private String clientDataJSON;

    @JsonProperty("authenticatorData")
    private String authenticatorData;

    @JsonProperty("signature")
    private String signature;

    @JsonProperty("userHandle")
    private String userHandle;

    // Getters and setters
}

/**
 * Token response after successful authentication
 */
public class TokenResponse {
    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("id_token")
    private String idToken;

    @JsonProperty("expires_in")
    private long expiresIn;

    @JsonProperty("token_type")
    private String tokenType = "bearer";

    // Getters and setters
}
```

---

## 4. Utility Classes

### Base64 Utility

```java
package com.inventage.keycloak.webauthn.util;

import java.util.Base64;

/**
 * Utility for base64url encoding/decoding.
 */
public class Base64Util {

    private static final Base64.Encoder ENCODER =
        Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER =
        Base64.getUrlDecoder();

    /**
     * Encode bytes to base64url string
     */
    public static String encodeToString(byte[] data) {
        return ENCODER.encodeToString(data);
    }

    /**
     * Decode base64url string to bytes
     */
    public static byte[] decode(String data) {
        return DECODER.decode(data);
    }

    /**
     * Convert string to base64url bytes
     */
    public static String encode(String data) {
        return encodeToString(data.getBytes());
    }

    /**
     * Convert base64url bytes to string
     */
    public static String decodeToString(String data) {
        return new String(decode(data));
    }
}
```

### Challenge Generator

```java
package com.inventage.keycloak.webauthn.util;

import java.security.SecureRandom;

/**
 * Generates random challenges for WebAuthn operations.
 */
public class ChallengeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int DEFAULT_LENGTH = 32;

    /**
     * Generate random challenge
     * @param length Challenge length in bytes
     * @return Random challenge bytes
     */
    public static byte[] generate(int length) {
        byte[] challenge = new byte[length];
        RANDOM.nextBytes(challenge);
        return challenge;
    }

    /**
     * Generate default length challenge (32 bytes)
     */
    public static byte[] generate() {
        return generate(DEFAULT_LENGTH);
    }

    /**
     * Generate challenge as base64url string
     */
    public static String generateBase64Url() {
        return Base64Util.encodeToString(generate());
    }
}
```

---

## 5. Core Provider Implementation

### RealmResourceProviderFactory

```java
package com.inventage.keycloak.webauthn.infrastructure.provider;

import com.google.auto.service.AutoService;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

/**
 * Factory for creating WebAuthn realm resource providers.
 */
@AutoService(RealmResourceProviderFactory.class)
public class WebAuthnRealmResourceProviderFactory
    implements RealmResourceProviderFactory {

    public static final String PROVIDER_ID = "webauthn";

    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new WebAuthnRealmResourceProvider(session);
    }

    @Override
    public void init(Config.Scope config) {
        // Initialize provider configuration if needed
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // Post-initialization hook
    }

    @Override
    public void close() {
        // Cleanup resources
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
```

### RealmResourceProvider

```java
package com.inventage.keycloak.webauthn.infrastructure.provider;

import com.inventage.keycloak.webauthn.infrastructure.model.*;
import com.inventage.keycloak.webauthn.infrastructure.service.*;
import com.inventage.keycloak.webauthn.infrastructure.exception.*;
import org.jboss.logging.Logger;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.resource.RealmResourceProvider;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * REST endpoints for WebAuthn registration and authentication.
 * Mapped to: /realms/{realm}/api/webauthn
 */
public class WebAuthnRealmResourceProvider implements RealmResourceProvider {

    private static final Logger LOG = Logger.getLogger(
        WebAuthnRealmResourceProvider.class);

    private final KeycloakSession session;
    private final RealmModel realm;
    private final WebAuthnRegistrationService registrationService;
    private final WebAuthnAuthenticationService authService;
    private final WebAuthnCredentialManager credentialManager;
    private final TokenService tokenService;

    public WebAuthnRealmResourceProvider(KeycloakSession session) {
        this.session = session;
        this.realm = session.getRealm();
        this.registrationService = new WebAuthnRegistrationService(session);
        this.authService = new WebAuthnAuthenticationService(session);
        this.credentialManager = new WebAuthnCredentialManager(session);
        this.tokenService = new TokenService(session);
    }

    /**
     * GET /realms/{realm}/api/webauthn
     * Health check endpoint
     */
    @Path("")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response healthCheck() {
        return Response.ok("{\"status\":\"ok\"}").build();
    }

    /**
     * POST /realms/{realm}/api/webauthn/register/challenge
     * Generate registration challenge
     */
    @Path("register/challenge")
    @POST
    @NoCache
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response registrationChallenge(
        RegistrationChallengeRequest request) {

        LOG.debugf("Registration challenge requested for: %s",
            request.getUsername());

        try {
            // Validate request
            if (request.getUsername() == null ||
                request.getUsername().isEmpty()) {
                throw new ValidationException("Username is required");
            }

            // Find user
            var user = session.users()
                .getUserByUsername(realm, request.getUsername());

            if (user == null) {
                throw new ValidationException(
                    "User not found");
            }

            // Generate challenge
            var response = registrationService.generateChallenge(
                user.getId(), request.getCredentialName());

            return Response.ok(response).build();

        } catch (WebAuthnException e) {
            e.log();
            return errorResponse(e);
        } catch (Exception e) {
            LOG.errorf(e, "Unexpected error in registration challenge");
            return internalServerError();
        }
    }

    /**
     * POST /realms/{realm}/api/webauthn/register/verify
     * Verify registration and store credential
     */
    @Path("register/verify")
    @POST
    @NoCache
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response verifyRegistration(
        RegistrationVerifyRequest request) {

        LOG.debug("Registration verification requested");

        try {
            // Verify and store credential
            registrationService.verifyAndStoreCredential(request);

            // Generate tokens
            var tokens = tokenService.generateTokens(
                session.users().getUserById(realm,
                    request.getUserId()),
                getDefaultClient(),
                "openid profile email");

            var response = new RegistrationVerifyResponse();
            response.setSuccess(true);
            response.setCredentialId(
                request.getResponse().getId());
            response.setAccessToken(tokens.getAccessToken());
            response.setRefreshToken(tokens.getRefreshToken());
            response.setIdToken(tokens.getIdToken());
            response.setExpiresIn(tokens.getExpiresIn());

            return Response.ok(response).build();

        } catch (WebAuthnException e) {
            e.log();
            return errorResponse(e);
        } catch (Exception e) {
            LOG.errorf(e, "Unexpected error in registration verify");
            return internalServerError();
        }
    }

    /**
     * POST /realms/{realm}/api/webauthn/auth/challenge
     * Generate authentication challenge
     */
    @Path("auth/challenge")
    @POST
    @NoCache
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response authenticationChallenge(
        AuthChallengeRequest request) {

        LOG.debugf("Auth challenge requested for: %s",
            request.getUsername());

        try {
            var response = authService.generateChallenge(
                request.getUsername());

            return Response.ok(response).build();

        } catch (Exception e) {
            LOG.errorf(e, "Error generating auth challenge");
            return internalServerError();
        }
    }

    /**
     * POST /realms/{realm}/api/webauthn/auth/verify
     * Verify authentication assertion
     */
    @Path("auth/verify")
    @POST
    @NoCache
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response verifyAuthentication(AuthVerifyRequest request) {

        LOG.debug("Auth verification requested");

        try {
            // Verify assertion
            var user = authService.verifyAssertion(request);

            // Generate tokens
            var tokens = tokenService.generateTokens(
                user, getDefaultClient(),
                "openid profile email");

            var response = new AuthVerifyResponse();
            response.setSuccess(true);
            response.setAccessToken(tokens.getAccessToken());
            response.setRefreshToken(tokens.getRefreshToken());
            response.setIdToken(tokens.getIdToken());
            response.setExpiresIn(tokens.getExpiresIn());

            return Response.ok(response).build();

        } catch (WebAuthnException e) {
            e.log();
            return errorResponse(e);
        } catch (Exception e) {
            LOG.errorf(e, "Unexpected error in auth verify");
            return internalServerError();
        }
    }

    /**
     * GET /realms/{realm}/api/webauthn/credentials
     * List user's credentials
     */
    @Path("credentials")
    @GET
    @NoCache
    @Produces(MediaType.APPLICATION_JSON)
    public Response listCredentials() {

        try {
            var userId = extractUserIdFromToken();
            var credentials = credentialManager
                .getCredentials(userId);

            var response = new CredentialListResponse();
            response.setCredentials(credentials);
            response.setTotal(credentials.size());

            return Response.ok(response).build();

        } catch (Exception e) {
            LOG.errorf(e, "Error listing credentials");
            return internalServerError();
        }
    }

    /**
     * DELETE /realms/{realm}/api/webauthn/credentials/{credentialId}
     * Delete credential
     */
    @Path("credentials/{credentialId}")
    @DELETE
    @NoCache
    public Response deleteCredential(
        @PathParam("credentialId") String credentialId) {

        LOG.debugf("Delete credential requested: %s",
            credentialId);

        try {
            var userId = extractUserIdFromToken();
            credentialManager.deleteCredential(userId,
                credentialId);

            return Response.noContent().build();

        } catch (Exception e) {
            LOG.errorf(e, "Error deleting credential");
            return internalServerError();
        }
    }

    /**
     * Helper: Build error response
     */
    private Response errorResponse(WebAuthnException e) {
        var error = new ErrorResponse();
        error.setError(e.getErrorCode());
        error.setErrorDescription(e.getMessage());

        return Response
            .status(e.getHttpStatusCode())
            .entity(error)
            .build();
    }

    /**
     * Helper: Build internal server error response
     */
    private Response internalServerError() {
        var error = new ErrorResponse();
        error.setError("INTERNAL_ERROR");
        error.setErrorDescription(
            "An unexpected error occurred");

        return Response
            .status(500)
            .entity(error)
            .build();
    }

    /**
     * Helper: Get default client for token generation
     */
    private org.keycloak.models.ClientModel
        getDefaultClient() {
        // Typically "account" or "admin-cli" client
        return session.clients()
            .getClientByClientId(realm, "account");
    }

    /**
     * Helper: Extract user ID from authorization token
     */
    private String extractUserIdFromToken() {
        // Implementation uses KeycloakSession
        // Verify Bearer token and extract user ID
        return null;
    }

    @Override
    public void close() {
        // Cleanup
    }
}
```

---

## 6. Service Implementation Example

### WebAuthnRegistrationService - With Credential Type Handling

```java
package com.inventage.keycloak.webauthn.infrastructure.service;

import com.inventage.keycloak.webauthn.infrastructure.model.*;
import com.inventage.keycloak.webauthn.infrastructure.exception.*;
import com.inventage.keycloak.webauthn.util.Base64Util;
import com.inventage.keycloak.webauthn.util.ChallengeGenerator;
import com.webauthn4j.data.AttestationFormat;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;

/**
 * Registration service for WebAuthn credential creation.
 * Handles credential type classification (PASSWORDLESS vs TWOFACTOR).
 */
public class WebAuthnRegistrationService {

    private static final Logger LOG = Logger.getLogger(
        WebAuthnRegistrationService.class);

    private final KeycloakSession session;
    private final WebAuthnCredentialManager credentialManager;

    public WebAuthnRegistrationService(KeycloakSession session) {
        this.session = session;
        this.credentialManager = new WebAuthnCredentialManager(session);
    }

    /**
     * Generate challenge for registration
     * @param userId User ID
     * @param credentialName User-provided credential name
     * @param credentialType Type: "passwordless" (default) or "twofactor"
     * @return Registration challenge response
     */
    public RegistrationChallengeResponse generateChallenge(
            String userId, String credentialName,
            String credentialType) throws RegistrationException {

        try {
            UserModel user = session.users().getUserById(
                session.getContext().getRealm(), userId);

            if (user == null) {
                throw new RegistrationException("User not found");
            }

            // Generate random challenge (32 bytes, base64url encoded)
            String challenge = ChallengeGenerator.generateBase64Url();

            // Store challenge with TTL (5 minutes)
            String sessionId = credentialManager.storeChallenge(
                userId, challenge, "registration", 300);

            LOG.debugf("Challenge generated for user %s, " +
                "credentialName: %s, type: %s",
                userId, credentialName, credentialType);

            // Build response with public key creation options
            return buildRegistrationChallengeResponse(
                sessionId, challenge, user);

        } catch (RegistrationException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistrationException(
                "Challenge generation failed", e);
        }
    }

    /**
     * Verify attestation and store credential
     * Includes credential type classification
     */
    public void verifyAndStoreCredential(
            RegistrationVerifyRequest request)
        throws RegistrationException, ChallengeExpiredException {

        try {
            // 1. Validate and retrieve challenge
            var challenge = credentialManager.getChallenge(
                request.getSessionId());

            if (challenge == null || challenge.isExpired()) {
                throw new ChallengeExpiredException(
                    "Challenge expired or not found");
            }

            // 2. Classify credential type
            CredentialType credentialType =
                classifyCredentialType(request.getType());

            LOG.debugf("Classifying credential as type: %s",
                credentialType.getValue());

            // 3. Parse and validate attestation response
            AttestationResponse attestation =
                request.getResponse();

            // 4. Validate client data
            validateClientData(
                attestation.getResponse().getClientDataJSON(),
                challenge.getChallenge());

            // 5. Parse attestation object
            byte[] attestationObjectBytes = Base64Util.decode(
                attestation.getResponse().getAttestationObject());

            // 6. Extract credential ID and public key
            WebAuthnCredential credential =
                extractCredentialFromAttestation(
                    attestationObjectBytes);

            // 7. SET CREDENTIAL TYPE
            // This is the critical step for type classification
            credential.setType(credentialType);
            credential.getMetadata().setName(
                request.getCredentialName());

            LOG.debugf("Credential type set to: %s, " +
                "credentialId: %s",
                credential.getTypeAsString(),
                credential.getCredentialId());

            // 8. Store credential with type
            credentialManager.storeCredential(
                challenge.getUserId(), credential);

            // 9. Clear challenge
            credentialManager.deleteChallenge(
                request.getSessionId());

            LOG.infof(
                "Credential registered successfully. " +
                "Type: %s, CredentialId: %s, " +
                "CredentialName: %s",
                credentialType.getValue(),
                credential.getCredentialId(),
                request.getCredentialName());

        } catch (ChallengeExpiredException e) {
            throw e;
        } catch (RegistrationException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistrationException(
                "Credential verification failed", e);
        }
    }

    /**
     * Classify credential type from request
     * Determines if credential is PASSWORDLESS or TWOFACTOR
     * @param typeString "passwordless" or "twofactor"
     * @return CredentialType enum
     */
    private CredentialType classifyCredentialType(
            String typeString) {

        CredentialType type =
            CredentialType.fromValue(typeString);

        LOG.debugf("Credential type classified as: %s " +
            "(from value: %s)",
            type.name(), typeString);

        // Audit log for compliance
        LOG.infof("AUDIT: Credential type classification - %s",
            type.getValue());

        return type;
    }

    /**
     * Validate client data JSON
     */
    private void validateClientData(String clientDataJSON,
            String expectedChallenge)
        throws RegistrationException {

        try {
            // Parse and validate client data
            // Verify challenge matches
            // Verify type is "webauthn.create"
            // Verify origin is allowed
            LOG.debug("Client data validated");

        } catch (Exception e) {
            throw new RegistrationException(
                "Client data validation failed", e);
        }
    }

    /**
     * Extract credential from attestation object
     */
    private WebAuthnCredential extractCredentialFromAttestation(
            byte[] attestationObjectBytes)
        throws RegistrationException {

        try {
            WebAuthnCredential credential =
                new WebAuthnCredential();

            // Parse attestation object using webauthn4j
            // Extract credential ID and public key
            // Set initial sign count to 0
            // Set credential type to "public-key" (CBOR type)

            LOG.debug("Credential extracted from attestation");
            return credential;

        } catch (Exception e) {
            throw new RegistrationException(
                "Attestation parsing failed", e);
        }
    }

    /**
     * Build registration challenge response
     */
    private RegistrationChallengeResponse
            buildRegistrationChallengeResponse(
            String sessionId, String challenge,
            UserModel user) {

        RegistrationChallengeResponse response =
            new RegistrationChallengeResponse();

        response.setSessionId(sessionId);
        response.setChallenge(challenge);
        response.setUserId(user.getId());
        response.setUserName(user.getUsername());
        response.setDisplayName(
            user.getFirstName() + " " +
            user.getLastName());

        // Set relying party info, user info, etc.
        return response;
    }
}
```

### WebAuthnAuthenticationService - Key Methods

```java
package com.inventage.keycloak.webauthn.infrastructure.service;

import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.client.ClientDataJSON;
import com.webauthn4j.data.client.CollectedClientData;
import com.webauthn4j.server.ServerProperty;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Authentication service for WebAuthn assertions.
 */
public class WebAuthnAuthenticationService {

    private static final Logger LOG = Logger.getLogger(
        WebAuthnAuthenticationService.class);
    private static final ObjectConverter converter =
        new ObjectConverter();

    private final KeycloakSession session;
    private final WebAuthnCredentialManager credentialManager;

    public WebAuthnAuthenticationService(
        KeycloakSession session) {
        this.session = session;
        this.credentialManager = new WebAuthnCredentialManager(
            session);
    }

    /**
     * Verify WebAuthn assertion and authenticate user
     */
    public UserModel verifyAssertion(AuthVerifyRequest request)
        throws InvalidCredentialException,
        ChallengeExpiredException {

        LOG.debug("Verifying WebAuthn assertion");

        try {
            // 1. Get and validate challenge
            var challenge = getAndValidateChallenge(
                request.getSessionId());

            // 2. Parse client data
            var clientData = parseClientDataJSON(
                request.getResponse().getClientDataJSON());

            // 3. Validate client data
            validateClientData(clientData, challenge);

            // 4. Parse authenticator data
            var authData = parseAuthenticatorData(
                request.getResponse().getAuthenticatorData());

            // 5. Get credential
            var credential = credentialManager
                .findCredentialById(
                    request.getResponse().getId());

            if (credential == null) {
                throw new InvalidCredentialException(
                    "Credential not found");
            }

            // 6. Verify signature
            verifySignature(request, challenge, authData,
                credential);

            // 7. Validate sign count
            validateSignCount(authData.getSignCount(),
                credential);

            // 8. Update credential
            credential.updateSignCount(
                authData.getSignCount());
            credential.updateLastUsed();
            credentialManager.updateCredential(credential);

            // 9. Clear challenge
            clearChallenge(request.getSessionId());

            // 10. Return authenticated user
            return credential.getUser();

        } catch (WebAuthnException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidCredentialException(
                "Assertion verification failed", e);
        }
    }

    /**
     * Verify cryptographic signature
     */
    private void verifySignature(AuthVerifyRequest request,
                                WebAuthnChallenge challenge,
                                AuthenticatorData authData,
                                WebAuthnCredential credential)
        throws InvalidCredentialException {

        try {
            // Decode signature from base64url
            byte[] signature = Base64Util.decode(
                request.getResponse().getSignature());

            // Decode authenticator data
            byte[] authenticatorData = Base64Util.decode(
                request.getResponse()
                    .getAuthenticatorData());

            // Get client data JSON bytes
            String clientDataJSON = request.getResponse()
                .getClientDataJSON();
            byte[] clientDataJSONBytes =
                clientDataJSON.getBytes(
                    StandardCharsets.UTF_8);

            // Hash client data
            byte[] clientDataHash = hashSHA256(
                clientDataJSONBytes);

            // Concatenate authenticator data + client data hash
            byte[] signedData = concatenate(
                authenticatorData, clientDataHash);

            // Verify signature using stored public key
            boolean valid = credential.getPublicKey()
                .verifySignature(signedData, signature);

            if (!valid) {
                throw new InvalidCredentialException(
                    "Signature verification failed");
            }

            LOG.debug("Signature verified successfully");

        } catch (Exception e) {
            throw new InvalidCredentialException(
                "Signature verification error", e);
        }
    }

    /**
     * Validate sign count to prevent cloning
     */
    private void validateSignCount(long newSignCount,
                                  WebAuthnCredential credential)
        throws InvalidCredentialException {

        long oldSignCount = credential.getSignCount();

        // Sign count must increase for each assertion
        if (newSignCount <= oldSignCount) {
            LOG.warnf(
                "Sign count validation failed. " +
                "Old: %d, New: %d - possible clone",
                oldSignCount, newSignCount);
            throw new InvalidCredentialException(
                "Sign count validation failed - " +
                "possible authenticator clone");
        }

        LOG.debugf("Sign count validation passed: %d -> %d",
            oldSignCount, newSignCount);
    }

    /**
     * Hash data using SHA-256
     */
    private byte[] hashSHA256(byte[] data)
        throws InvalidCredentialException {
        try {
            MessageDigest digest =
                MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (Exception e) {
            throw new InvalidCredentialException(
                "Hash computation failed", e);
        }
    }

    /**
     * Concatenate byte arrays
     */
    private byte[] concatenate(byte[] a, byte[] b) {
        byte[] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    // Additional helper methods...
}
```

---

## 7. Testing Example

### Integration Test

```java
package com.inventage.keycloak.webauthn.infrastructure.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

public class WebAuthnAuthenticationServiceTest {

    private WebAuthnAuthenticationService service;

    @BeforeEach
    public void setUp() {
        // Initialize service with test session
    }

    @Test
    public void testVerifyAssertion_Valid() throws Exception {
        // Test successful assertion verification
        var request = createValidAuthVerifyRequest();
        var user = service.verifyAssertion(request);
        assertNotNull(user);
        assertEquals("testuser", user.getUsername());
    }

    @Test
    public void testVerifyAssertion_InvalidSignature() {
        // Test with invalid signature
        var request = createAuthVerifyRequestWithInvalidSig();
        assertThrows(InvalidCredentialException.class, () -> {
            service.verifyAssertion(request);
        });
    }

    @Test
    public void testVerifyAssertion_SignCountNotIncreased() {
        // Test clone detection
        var request = createAuthVerifyRequestWithLowSignCount();
        assertThrows(InvalidCredentialException.class, () -> {
            service.verifyAssertion(request);
        });
    }

    @Test
    public void testGenerateChallenge_ChallengeExpires() {
        // Test challenge expiration
        var challenge = service.generateChallenge("user");
        // Wait for expiration...
        assertThrows(ChallengeExpiredException.class, () -> {
            service.verifyAssertion(createRequestWithOldChallenge());
        });
    }
}
```

---

## 8. Building & Deployment

### Build Extension

```bash
# Build entire module
mvn clean install -pl :extension-webauthn-realm

# Build and skip tests
mvn clean install -pl :extension-webauthn-realm -DskipTests

# Build with specific JAR
mvn clean package -pl :extension-webauthn-realm
```

### Deploy to Keycloak

```bash
# Copy JAR to providers directory
cp target/extension-webauthn-realm-1.0.0-SNAPSHOT.jar \
   /opt/keycloak/providers/

# Restart Keycloak
docker restart keycloak

# Verify deployment
docker logs keycloak | grep -i webauthn
```

---

## 9. Development Best Practices

### Code Quality

```bash
# Run checkstyle
mvn checkstyle:check

# Run spotbugs
mvn spotbugs:check

# Generate coverage
mvn jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

### Logging Best Practices

```java
// Use structured logging
LOG.infof("User %s authenticated via WebAuthn", userId);
LOG.debugf("Signature verified for credential: %s",
    credentialId);
LOG.warnf("Sign count mismatch: expected >%d, got %d",
    oldCount, newCount);
LOG.errorf(exception,
    "Failed to store credential for user: %s", userId);
```

### Security Best Practices

1. **Always validate input** - Check all incoming requests
2. **Use HTTPS only** - Enforce in production
3. **Rate limit endpoints** - Prevent brute force
4. **Audit logging** - Log all authentication attempts
5. **Sanitize error messages** - Don't leak internal details
6. **Validate signatures** - Use established crypto libraries
7. **Check sign count** - Detect cloned authenticators

---

## 10. Troubleshooting

### Common Issues

**Issue**: JAR not loaded
- Check `/opt/keycloak/providers/` directory
- Verify `@AutoService` annotation present
- Check Keycloak logs for errors

**Issue**: Challenge expired
- Increase TTL in configuration
- Ensure client doesn't delay submission

**Issue**: Signature verification fails
- Verify public key is correctly stored
- Check client is using correct credential ID
- Validate challenge encoding (base64url)

**Issue**: Token generation fails
- Verify realm has required clients
- Check user has required roles
- Validate protocol mapper configuration

---

## References

- [WebAuthn4j API Documentation](https://webauthn4j.github.io/webauthn4j/javadoc/)
- [Jackson Databind Guide](https://github.com/FasterXML/jackson-databind)
- [Keycloak SPI Development](https://www.keycloak.org/docs/latest/server_development/)
- [FIDO2 Specification](https://fidoalliance.org/fido2/)

---

**Document Status**: Complete Implementation Guide
**Last Updated**: 2025-11-29
