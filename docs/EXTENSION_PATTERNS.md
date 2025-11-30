# Keycloak WebAuthn Extension Patterns

## Overview

This guide provides patterns and best practices for extending the Keycloak WebAuthn implementation. It covers common extension scenarios, architecture patterns, and integration strategies.

---

## Table of Contents

1. [Custom Credential Types](#custom-credential-types)
2. [Custom Authenticators](#custom-authenticators)
3. [Provider Integration](#provider-integration)
4. [Event Listeners](#event-listeners)
5. [Custom Validation Rules](#custom-validation-rules)
6. [Third-Party Integration](#third-party-integration)

---

## 1. Custom Credential Types

### Pattern: Adding Platform-Specific Credentials

**Use Case**: Support platform-specific authenticators (Apple, Windows Hello, Android)

#### Step 1: Extend CredentialType Enum
```java
// File: CredentialType.java
public enum CredentialType {
    TYPE_PASSWORDLESS("passwordless"),
    TYPE_TWO_FACTOR("twofactor"),
    TYPE_PLATFORM_APPLE("platform_apple"),      // NEW
    TYPE_PLATFORM_WINDOWS("platform_windows"),  // NEW
    TYPE_PLATFORM_ANDROID("platform_android");  // NEW

    private final String value;

    CredentialType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static CredentialType fromValue(String value) {
        for (CredentialType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return TYPE_PASSWORDLESS; // Default fallback
    }

    // Platform detection helpers
    public boolean isPlatformAuthenticator() {
        return this == TYPE_PLATFORM_APPLE ||
               this == TYPE_PLATFORM_WINDOWS ||
               this == TYPE_PLATFORM_ANDROID;
    }

    public boolean isCrossPlatform() {
        return this == TYPE_PASSWORDLESS || this == TYPE_TWO_FACTOR;
    }
}
```

#### Step 2: Platform Detection Service
```java
// File: PlatformDetectionService.java
public class PlatformDetectionService {

    private static final Map<String, CredentialType> AAGUID_TO_PLATFORM = Map.of(
        "00000000-0000-0000-0000-000000000000", CredentialType.TYPE_PLATFORM_APPLE,
        "08987058-cadc-4b81-b6e1-30de50dcbe96", CredentialType.TYPE_PLATFORM_WINDOWS,
        "ea9b8d66-4d01-1d21-3ce4-b6b48cb575d4", CredentialType.TYPE_PLATFORM_ANDROID
    );

    public CredentialType detectPlatform(String aaguid, String userAgent) {
        // 1. Try AAGUID-based detection
        CredentialType type = AAGUID_TO_PLATFORM.get(aaguid);
        if (type != null) {
            return type;
        }

        // 2. Fallback to User-Agent detection
        if (userAgent.contains("iPhone") || userAgent.contains("Mac")) {
            return CredentialType.TYPE_PLATFORM_APPLE;
        } else if (userAgent.contains("Windows")) {
            return CredentialType.TYPE_PLATFORM_WINDOWS;
        } else if (userAgent.contains("Android")) {
            return CredentialType.TYPE_PLATFORM_ANDROID;
        }

        // 3. Default to generic passwordless
        return CredentialType.TYPE_PASSWORDLESS;
    }

    public AuthenticatorAttachment getAttachment(CredentialType type) {
        return type.isPlatformAuthenticator() ?
            AuthenticatorAttachment.PLATFORM :
            AuthenticatorAttachment.CROSS_PLATFORM;
    }
}
```

#### Step 3: Update Registration Service
```java
// File: WebAuthnRegistrationService.java (enhancement)
public Map<String, Object> generateChallenge(String userId, String credentialName,
        String credentialType, String userAgent) throws RegistrationException {

    CredentialType type = classifyCredentialType(credentialType);

    Map<String, Object> response = buildRegistrationChallengeResponse(
        sessionId, challenge, user, credentialName, type);

    // Set platform-specific options
    if (type.isPlatformAuthenticator()) {
        Map<String, Object> authenticatorSelection =
            (Map<String, Object>) response.get("authenticatorSelection");

        authenticatorSelection.put("authenticatorAttachment", "platform");
        authenticatorSelection.put("requireResidentKey", true); // Platform authenticators support resident keys
        authenticatorSelection.put("userVerification", "required");
    }

    return response;
}
```

### Pattern: Custom Credential Validation

```java
// File: CustomCredentialValidator.java
@ApplicationScoped
public class CustomCredentialValidator {

    @Inject
    private Logger logger;

    @Inject
    private KeycloakSession session;

    /**
     * Validate credential based on custom business rules
     */
    public ValidationResult validate(WebAuthnCredential credential, String userId) {
        ValidationResult result = new ValidationResult();

        // Rule 1: Enterprise must use hardware keys only
        if (isEnterpriseUser(userId)) {
            if (!isHardwareKey(credential)) {
                result.addError("ENTERPRISE_HARDWARE_REQUIRED",
                    "Enterprise users must use hardware security keys");
            }
        }

        // Rule 2: Limit credentials per user
        List<WebAuthnCredential> existing = credentialManager.getCredentials(userId);
        if (existing.size() >= getMaxCredentials(userId)) {
            result.addError("MAX_CREDENTIALS_EXCEEDED",
                "Maximum number of credentials reached");
        }

        // Rule 3: Prevent duplicate authenticator models
        if (hasDuplicateAuthenticator(credential, existing)) {
            result.addWarning("DUPLICATE_AUTHENTICATOR",
                "You already have a credential from this authenticator");
        }

        return result;
    }

    private boolean isEnterpriseUser(String userId) {
        UserModel user = session.users().getUserById(session.getContext().getRealm(), userId);
        return user.getGroups().stream()
            .anyMatch(g -> g.getName().equals("enterprise"));
    }

    private boolean isHardwareKey(WebAuthnCredential credential) {
        // Check AAGUID against known hardware key manufacturers
        String aaguid = credential.getMetadata().getAaguid();
        return KNOWN_HARDWARE_KEYS.contains(aaguid);
    }

    private int getMaxCredentials(String userId) {
        // Premium users get more credentials
        return isPremiumUser(userId) ? 20 : 5;
    }

    private boolean hasDuplicateAuthenticator(WebAuthnCredential newCred,
            List<WebAuthnCredential> existing) {

        String newAAGUID = newCred.getMetadata().getAaguid();

        return existing.stream()
            .anyMatch(c -> c.getMetadata().getAaguid().equals(newAAGUID));
    }
}
```

---

## 2. Custom Authenticators

### Pattern: WebAuthn + Password Hybrid Flow

**Use Case**: Allow either WebAuthn OR password login (not both)

#### Step 1: Create Custom Authenticator
```java
// File: WebAuthnOrPasswordAuthenticatorFactory.java
@AutoService(AuthenticatorFactory.class)
public class WebAuthnOrPasswordAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "webauthn-or-password";

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "WebAuthn OR Password";
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return new WebAuthnOrPasswordAuthenticator();
    }

    @Override
    public String getHelpText() {
        return "Allows authentication with either WebAuthn or password";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of(
            new ProviderConfigProperty("preferWebAuthn", "Prefer WebAuthn",
                "Show WebAuthn option first", ProviderConfigProperty.BOOLEAN_TYPE, "true"),
            new ProviderConfigProperty("allowPasswordFallback", "Allow Password Fallback",
                "Allow password if WebAuthn fails", ProviderConfigProperty.BOOLEAN_TYPE, "true")
        );
    }
}

// File: WebAuthnOrPasswordAuthenticator.java
public class WebAuthnOrPasswordAuthenticator implements Authenticator {

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        String username = context.getAuthenticationSession().getAuthNote("username");

        if (username == null) {
            // First step: ask for username
            context.challenge(createUsernameChallenge(context));
            return;
        }

        UserModel user = context.getSession().users()
            .getUserByUsername(context.getRealm(), username);

        if (user == null) {
            context.failure(AuthenticationFlowError.INVALID_USER);
            return;
        }

        // Check if user has WebAuthn credentials
        List<WebAuthnCredential> credentials = getWebAuthnCredentials(user);

        if (!credentials.isEmpty()) {
            // User has WebAuthn - offer WebAuthn + optional password fallback
            context.challenge(createWebAuthnChallenge(context, user, credentials));
        } else {
            // User doesn't have WebAuthn - password only
            context.challenge(createPasswordChallenge(context, user));
        }
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

        String authMethod = formData.getFirst("auth_method"); // "webauthn" or "password"

        if ("webauthn".equals(authMethod)) {
            handleWebAuthnAuth(context, formData);
        } else if ("password".equals(authMethod)) {
            handlePasswordAuth(context, formData);
        } else {
            context.failure(AuthenticationFlowError.INVALID_CREDENTIALS);
        }
    }

    private void handleWebAuthnAuth(AuthenticationFlowContext context,
            MultivaluedMap<String, String> formData) {

        String credentialResponse = formData.getFirst("credential_response");

        try {
            WebAuthnAuthenticationService authService =
                new WebAuthnAuthenticationService(context.getSession());

            UserModel user = authService.verifyAssertion(
                formData.getFirst("session_id"),
                formData.getFirst("credential_id"),
                formData.getFirst("client_data_json"),
                formData.getFirst("authenticator_data"),
                formData.getFirst("signature"),
                formData.getFirst("user_handle")
            );

            context.setUser(user);
            context.success();

        } catch (Exception e) {
            context.failure(AuthenticationFlowError.INVALID_CREDENTIALS);
        }
    }

    private void handlePasswordAuth(AuthenticationFlowContext context,
            MultivaluedMap<String, String> formData) {

        String password = formData.getFirst("password");
        UserModel user = context.getUser();

        if (!context.getSession().userCredentialManager()
                .isValid(context.getRealm(), user, UserCredentialModel.password(password))) {
            context.failure(AuthenticationFlowError.INVALID_CREDENTIALS);
            return;
        }

        context.success();
    }
}
```

#### Step 2: Register in Authentication Flow
```xml
<!-- File: realm-example.json -->
{
  "authenticationFlows": [
    {
      "alias": "WebAuthn OR Password Flow",
      "providerId": "basic-flow",
      "topLevel": true,
      "builtIn": false,
      "authenticationExecutions": [
        {
          "authenticator": "webauthn-or-password",
          "requirement": "REQUIRED",
          "priority": 10
        }
      ]
    }
  ]
}
```

---

## 3. Provider Integration

### Pattern: Custom Credential Storage Provider

**Use Case**: Store WebAuthn credentials in external database/vault

#### Implementation
```java
// File: ExternalCredentialStorageProviderFactory.java
@AutoService(UserStorageProviderFactory.class)
public class ExternalCredentialStorageProviderFactory
        implements UserStorageProviderFactory<ExternalCredentialStorageProvider> {

    public static final String PROVIDER_ID = "external-webauthn-storage";

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public ExternalCredentialStorageProvider create(KeycloakSession session, ComponentModel model) {
        return new ExternalCredentialStorageProvider(session, model);
    }
}

// File: ExternalCredentialStorageProvider.java
public class ExternalCredentialStorageProvider implements
        UserStorageProvider,
        CredentialInputValidator,
        CredentialInputUpdater {

    private final KeycloakSession session;
    private final ComponentModel model;
    private final ExternalCredentialClient client;

    public ExternalCredentialStorageProvider(KeycloakSession session, ComponentModel model) {
        this.session = session;
        this.model = model;
        this.client = new ExternalCredentialClient(model.get("apiUrl"), model.get("apiKey"));
    }

    @Override
    public boolean supportsCredentialType(String credentialType) {
        return "webauthn".equals(credentialType);
    }

    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        return client.hasCredentials(user.getId());
    }

    @Override
    public boolean isValid(RealmModel realm, UserModel user, CredentialInput input) {
        if (!(input instanceof WebAuthnCredentialInput)) {
            return false;
        }

        WebAuthnCredentialInput webAuthnInput = (WebAuthnCredentialInput) input;

        // Verify credential via external API
        return client.verifyCredential(
            user.getId(),
            webAuthnInput.getCredentialId(),
            webAuthnInput.getSignature(),
            webAuthnInput.getAuthenticatorData()
        );
    }

    @Override
    public boolean updateCredential(RealmModel realm, UserModel user, CredentialInput input) {
        if (!(input instanceof WebAuthnCredentialInput)) {
            return false;
        }

        WebAuthnCredentialInput webAuthnInput = (WebAuthnCredentialInput) input;

        // Store credential via external API
        return client.storeCredential(
            user.getId(),
            webAuthnInput.getCredentialId(),
            webAuthnInput.getPublicKey(),
            webAuthnInput.getAttestationObject()
        );
    }
}

// File: ExternalCredentialClient.java
public class ExternalCredentialClient {
    private final String apiUrl;
    private final String apiKey;

    public boolean hasCredentials(String userId) {
        HttpResponse<String> response = Unirest.get(apiUrl + "/users/" + userId + "/credentials")
            .header("Authorization", "Bearer " + apiKey)
            .asString();

        return response.getStatus() == 200 &&
               !response.getBody().contains("\"credentials\":[]");
    }

    public boolean verifyCredential(String userId, String credentialId,
            String signature, String authData) {

        HttpResponse<JsonNode> response = Unirest.post(apiUrl + "/verify")
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .body(Map.of(
                "userId", userId,
                "credentialId", credentialId,
                "signature", signature,
                "authenticatorData", authData
            ))
            .asJson();

        return response.getStatus() == 200 &&
               response.getBody().getObject().getBoolean("valid");
    }

    public boolean storeCredential(String userId, String credentialId,
            String publicKey, String attestationObject) {

        HttpResponse<JsonNode> response = Unirest.post(apiUrl + "/credentials")
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .body(Map.of(
                "userId", userId,
                "credentialId", credentialId,
                "publicKey", publicKey,
                "attestationObject", attestationObject
            ))
            .asJson();

        return response.getStatus() == 201;
    }
}
```

---

## 4. Event Listeners

### Pattern: Audit Logging & Analytics

**Use Case**: Track WebAuthn usage for security monitoring and analytics

#### Implementation
```java
// File: WebAuthnEventListenerProviderFactory.java
@AutoService(EventListenerProviderFactory.class)
public class WebAuthnEventListenerProviderFactory implements EventListenerProviderFactory {

    @Override
    public String getId() {
        return "webauthn-event-listener";
    }

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new WebAuthnEventListenerProvider(session);
    }
}

// File: WebAuthnEventListenerProvider.java
public class WebAuthnEventListenerProvider implements EventListenerProvider {

    private static final Logger LOG = Logger.getLogger(WebAuthnEventListenerProvider.class);

    private final KeycloakSession session;
    private final AnalyticsService analyticsService;

    public WebAuthnEventListenerProvider(KeycloakSession session) {
        this.session = session;
        this.analyticsService = new AnalyticsService();
    }

    @Override
    public void onEvent(Event event) {
        // Track custom WebAuthn events
        if (isWebAuthnEvent(event)) {
            handleWebAuthnEvent(event);
        }
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
        // Track admin operations on WebAuthn credentials
        if (isWebAuthnAdminEvent(adminEvent)) {
            handleWebAuthnAdminEvent(adminEvent);
        }
    }

    private boolean isWebAuthnEvent(Event event) {
        return event.getType().name().startsWith("WEBAUTHN_") ||
               "CUSTOM_WEBAUTHN_REGISTRATION".equals(event.getType().name()) ||
               "CUSTOM_WEBAUTHN_AUTHENTICATION".equals(event.getType().name());
    }

    private void handleWebAuthnEvent(Event event) {
        String eventType = event.getType().name();

        switch (eventType) {
            case "CUSTOM_WEBAUTHN_REGISTRATION":
                handleRegistration(event);
                break;

            case "CUSTOM_WEBAUTHN_AUTHENTICATION":
                handleAuthentication(event);
                break;

            case "CUSTOM_WEBAUTHN_CREDENTIAL_DELETED":
                handleCredentialDeletion(event);
                break;

            default:
                LOG.debugf("Unhandled WebAuthn event: %s", eventType);
        }
    }

    private void handleRegistration(Event event) {
        String userId = event.getUserId();
        String credentialId = event.getDetails().get("credential_id");
        String credentialType = event.getDetails().get("credential_type");
        String aaguid = event.getDetails().get("aaguid");

        // Send to analytics
        analyticsService.trackRegistration(
            userId,
            credentialId,
            credentialType,
            aaguid,
            event.getIpAddress(),
            extractUserAgent(event)
        );

        // Check for suspicious patterns
        if (isSuspiciousRegistration(event)) {
            sendSecurityAlert("Suspicious WebAuthn registration detected", event);
        }

        LOG.infof("[AUDIT] WebAuthn Registration - User: %s, CredentialID: %s, Type: %s",
            userId, credentialId, credentialType);
    }

    private void handleAuthentication(Event event) {
        String userId = event.getUserId();
        String credentialId = event.getDetails().get("credential_id");
        boolean success = "success".equals(event.getDetails().get("result"));

        analyticsService.trackAuthentication(
            userId,
            credentialId,
            success,
            event.getIpAddress(),
            extractUserAgent(event)
        );

        if (!success) {
            handleFailedAuthentication(event);
        }

        LOG.infof("[AUDIT] WebAuthn Authentication - User: %s, CredentialID: %s, Result: %s",
            userId, credentialId, success ? "SUCCESS" : "FAILURE");
    }

    private void handleFailedAuthentication(Event event) {
        String userId = event.getUserId();
        String errorCode = event.getDetails().get("error_code");

        // Track failed attempts
        incrementFailedAttempts(userId);

        // Check for account lockout
        int failedAttempts = getFailedAttempts(userId);
        if (failedAttempts >= 5) {
            lockAccount(userId);
            sendSecurityAlert("Account locked due to multiple failed WebAuthn attempts", event);
        }

        // Detect anomalies
        if ("SIGN_COUNT_MISMATCH".equals(errorCode)) {
            sendSecurityAlert("Potential authenticator clone detected", event);
        }
    }

    private boolean isSuspiciousRegistration(Event event) {
        String userId = event.getUserId();

        // Check 1: Too many registrations in short time
        int recentRegistrations = analyticsService.getRecentRegistrationCount(userId, 1); // Last hour
        if (recentRegistrations > 5) {
            return true;
        }

        // Check 2: Registration from unusual location
        String ipAddress = event.getIpAddress();
        if (!isKnownLocation(userId, ipAddress)) {
            return true;
        }

        return false;
    }

    private void sendSecurityAlert(String message, Event event) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("message", message);
        alert.put("userId", event.getUserId());
        alert.put("ipAddress", event.getIpAddress());
        alert.put("timestamp", Instant.now());
        alert.put("eventDetails", event.getDetails());

        // Send to security monitoring system
        securityMonitoringService.sendAlert(alert);

        // Send email to user
        emailService.sendSecurityAlert(event.getUserId(), message);
    }
}
```

---

## 5. Custom Validation Rules

### Pattern: Business Logic Validators

```java
// File: WebAuthnValidationRules.java
@ApplicationScoped
public class WebAuthnValidationRules {

    /**
     * Rule: Prevent registration of software authenticators for admin users
     */
    @ValidationRule(name = "admin-hardware-only")
    public ValidationResult validateAdminHardwareOnly(WebAuthnCredential credential, UserModel user) {
        if (!user.getGroups().stream().anyMatch(g -> g.getName().equals("admin"))) {
            return ValidationResult.success();
        }

        String aaguid = credential.getMetadata().getAaguid();
        if (isSoftwareAuthenticator(aaguid)) {
            return ValidationResult.failure(
                "ADMIN_HARDWARE_REQUIRED",
                "Admin users must use hardware security keys"
            );
        }

        return ValidationResult.success();
    }

    /**
     * Rule: Require biometric authentication for financial operations
     */
    @ValidationRule(name = "financial-biometric-required")
    public ValidationResult validateBiometricForFinancial(AuthenticatorData authData,
            String operation) {

        if (!operation.startsWith("financial_")) {
            return ValidationResult.success();
        }

        byte flags = authData.getFlags();
        boolean userVerified = (flags & 0x04) != 0;

        if (!userVerified) {
            return ValidationResult.failure(
                "BIOMETRIC_REQUIRED",
                "Financial operations require biometric authentication"
            );
        }

        return ValidationResult.success();
    }

    /**
     * Rule: Prevent registration of authenticators from untrusted manufacturers
     */
    @ValidationRule(name = "trusted-manufacturers-only")
    public ValidationResult validateTrustedManufacturer(String aaguid) {
        Set<String> trustedAAGUIDs = loadTrustedAAGUIDs();

        if (!trustedAAGUIDs.contains(aaguid)) {
            return ValidationResult.failure(
                "UNTRUSTED_MANUFACTURER",
                "This authenticator is not from a trusted manufacturer"
            );
        }

        return ValidationResult.success();
    }

    private Set<String> loadTrustedAAGUIDs() {
        // Load from realm attributes or configuration
        return Set.of(
            "2fc0579f-8113-47ea-b116-bb5a8db9202a", // YubiKey 5 Series
            "f8a011f3-8c0a-4d15-8006-17111f9edc7d"  // Titan Security Key
        );
    }
}
```

---

## 6. Third-Party Integration

### Pattern: Integration with Identity Providers

**Use Case**: Sync WebAuthn credentials across multiple IDPs

```java
// File: WebAuthnFederationMapper.java
@AutoService(ProtocolMapper.class)
public class WebAuthnFederationMapper extends AbstractOIDCProtocolMapper
        implements OIDCAccessTokenMapper, OIDCIDTokenMapper {

    public static final String PROVIDER_ID = "webauthn-federation-mapper";

    @Override
    public String getDisplayCategory() {
        return "WebAuthn Mapper";
    }

    @Override
    public String getDisplayType() {
        return "WebAuthn Credentials to Token";
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    protected void setClaim(IDToken token, ProtocolMapperModel mappingModel,
            UserSessionModel userSession, KeycloakSession keycloakSession,
            ClientSessionContext clientSessionCtx) {

        UserModel user = userSession.getUser();

        // Add WebAuthn credentials to ID token
        List<WebAuthnCredential> credentials = getWebAuthnCredentials(user);

        List<Map<String, Object>> credentialClaims = credentials.stream()
            .map(this::credentialToClaim)
            .collect(Collectors.toList());

        token.setOtherClaims("webauthn_credentials", credentialClaims);
    }

    private Map<String, Object> credentialToClaim(WebAuthnCredential credential) {
        return Map.of(
            "credential_id", credential.getCredentialId(),
            "type", credential.getTypeAsString(),
            "created_at", credential.getMetadata().getCreatedAt(),
            "aaguid", credential.getMetadata().getAaguid()
        );
    }
}
```

---

## Summary

This guide covered six key extension patterns:

1. **Custom Credential Types**: Platform-specific credentials (Apple, Windows, Android)
2. **Custom Authenticators**: Hybrid WebAuthn+Password flows
3. **Provider Integration**: External credential storage
4. **Event Listeners**: Audit logging and analytics
5. **Custom Validation Rules**: Business logic enforcement
6. **Third-Party Integration**: Federation and token mapping

**Next Steps:**
- Review patterns relevant to your use case
- Implement with TDD approach
- Add comprehensive tests
- Document configuration requirements

---

**Document Version**: 1.0
**Last Updated**: 2025-11-30
