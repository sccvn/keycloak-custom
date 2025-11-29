# TDD Implementation Guide for WebAuthn Extension

## Overview

This guide provides step-by-step instructions for implementing the WebAuthn extension using Test-Driven Development (TDD). All tests have been written first - now implement the production code to make them pass.

## Test Suite Status

✅ **Complete Test Suite**: 72 test methods across 6 test classes
✅ **Test Coverage**: Unit tests, integration tests, and fixtures
✅ **TDD Compliance**: All tests written before implementation
✅ **Framework**: JUnit Jupiter 5.12, Mockito 5.14.2, AssertJ 3.27.3

## Implementation Order (Red-Green-Refactor)

### Phase 1: Utility Classes (Start Here)

These are the simplest components with no external dependencies.

#### 1.1 Base64Util Implementation
**Test File**: `src/test/java/com/inventage/keycloak/webauthn/util/Base64UtilTest.java`
**Implementation File**: `src/main/java/com/inventage/keycloak/webauthn/util/Base64Util.java`

**Tests to Pass**: 10 tests

```java
public class Base64Util {
    public static String encodeBase64Url(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    public static byte[] decodeBase64Url(String encoded) {
        if (encoded == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }
        try {
            return Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Base64URL string", e);
        }
    }

    public static String encodeBase64(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }
        return Base64.getEncoder().encodeToString(data);
    }

    public static byte[] decodeBase64(String encoded) {
        if (encoded == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }
        return Base64.getDecoder().decode(encoded);
    }

    public static String convertBase64ToBase64Url(String base64) {
        return base64.replace('+', '-')
                     .replace('/', '_')
                     .replaceAll("=+$", "");
    }

    public static String convertBase64UrlToBase64(String base64Url) {
        String base64 = base64Url.replace('-', '+')
                                  .replace('_', '/');
        // Add padding if needed
        int padding = (4 - (base64.length() % 4)) % 4;
        return base64 + "=".repeat(padding);
    }
}
```

**Run Tests**:
```bash
mvn test -Dtest=Base64UtilTest
```

#### 1.2 ChallengeGenerator Implementation
**Test File**: `src/test/java/com/inventage/keycloak/webauthn/util/ChallengeGeneratorTest.java`
**Implementation File**: `src/main/java/com/inventage/keycloak/webauthn/util/ChallengeGenerator.java`

**Tests to Pass**: 13 tests

```java
public class ChallengeGenerator {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int DEFAULT_LENGTH = 32;
    private static final int MIN_LENGTH = 16;
    private static final int MAX_LENGTH = 64;

    public static String generate() {
        return generate(DEFAULT_LENGTH);
    }

    public static String generate(int length) {
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new IllegalArgumentException(
                String.format("Challenge length must be between %d and %d bytes",
                    MIN_LENGTH, MAX_LENGTH)
            );
        }

        byte[] challenge = new byte[length];
        SECURE_RANDOM.nextBytes(challenge);
        return Base64Util.encodeBase64Url(challenge);
    }

    public static String generateId() {
        long timestamp = System.currentTimeMillis();
        byte[] random = new byte[8];
        SECURE_RANDOM.nextBytes(random);

        // Combine timestamp and random bytes
        String timestampStr = String.valueOf(timestamp);
        String randomStr = Base64Util.encodeBase64Url(random);
        return timestampStr + "-" + randomStr;
    }
}
```

**Run Tests**:
```bash
mvn test -Dtest=ChallengeGeneratorTest
```

### Phase 2: Manager Layer

#### 2.1 WebAuthnCredentialManager Implementation
**Test File**: `src/test/java/com/inventage/keycloak/webauthn/manager/WebAuthnCredentialManagerTest.java`
**Implementation File**: `src/main/java/com/inventage/keycloak/webauthn/manager/WebAuthnCredentialManager.java`

**Tests to Pass**: 12 tests

```java
public class WebAuthnCredentialManager {
    private final KeycloakSession session;
    private static final String CREDENTIAL_TYPE = "webauthn";

    public WebAuthnCredentialManager(KeycloakSession session) {
        this.session = session;
    }

    public boolean createCredential(String userId, Map<String, Object> credentialData,
                                    Map<String, String> metadata) {
        UserModel user = session.users().getUserById(session.getContext().getRealm(), userId);
        if (user == null) {
            throw new WebAuthnException("User not found");
        }

        String credentialId = (String) credentialData.get("credentialId");

        // Check for duplicate
        if (user.credentialManager().getStoredCredentialById(credentialId) != null) {
            throw new WebAuthnException("Credential already exists");
        }

        CredentialModel credential = new CredentialModel();
        credential.setId(credentialId);
        credential.setType(CREDENTIAL_TYPE);
        credential.setUserLabel(metadata.get("userLabel"));
        credential.setCreatedDate(System.currentTimeMillis());

        // Serialize credential data as JSON
        credential.setCredentialData(serializeCredentialData(credentialData));

        return user.credentialManager().createStoredCredential(credential);
    }

    public Optional<CredentialModel> getCredentialById(String userId, String credentialId) {
        UserModel user = session.users().getUserById(session.getContext().getRealm(), userId);
        if (user == null) {
            return Optional.empty();
        }

        CredentialModel credential = user.credentialManager().getStoredCredentialById(credentialId);
        return Optional.ofNullable(credential);
    }

    public List<CredentialModel> getAllCredentials(String userId) {
        UserModel user = session.users().getUserById(session.getContext().getRealm(), userId);
        if (user == null) {
            return Collections.emptyList();
        }

        return user.credentialManager().getStoredCredentialsStream()
            .filter(cred -> CREDENTIAL_TYPE.equals(cred.getType()))
            .sorted(Comparator.comparing(CredentialModel::getCreatedDate).reversed())
            .collect(Collectors.toList());
    }

    public boolean updateSignatureCounter(String userId, String credentialId, int newCounter) {
        Optional<CredentialModel> credOpt = getCredentialById(userId, credentialId);
        if (credOpt.isEmpty()) {
            return false;
        }

        CredentialModel credential = credOpt.get();
        Map<String, Object> data = deserializeCredentialData(credential.getCredentialData());
        data.put("signatureCount", newCounter);
        credential.setCredentialData(serializeCredentialData(data));

        UserModel user = session.users().getUserById(session.getContext().getRealm(), userId);
        return user.credentialManager().updateStoredCredential(credential);
    }

    public boolean deleteCredential(String userId, String credentialId) {
        UserModel user = session.users().getUserById(session.getContext().getRealm(), userId);
        if (user == null) {
            return false;
        }

        return user.credentialManager().removeStoredCredentialById(credentialId);
    }

    public int deleteAllCredentials(String userId) {
        List<CredentialModel> credentials = getAllCredentials(userId);
        UserModel user = session.users().getUserById(session.getContext().getRealm(), userId);

        int count = 0;
        for (CredentialModel cred : credentials) {
            if (user.credentialManager().removeStoredCredentialById(cred.getId())) {
                count++;
            }
        }
        return count;
    }

    public boolean updateMetadata(String userId, String credentialId, Map<String, String> metadata) {
        Optional<CredentialModel> credOpt = getCredentialById(userId, credentialId);
        if (credOpt.isEmpty()) {
            return false;
        }

        CredentialModel credential = credOpt.get();
        credential.setUserLabel(metadata.get("userLabel"));

        UserModel user = session.users().getUserById(session.getContext().getRealm(), userId);
        return user.credentialManager().updateStoredCredential(credential);
    }

    public long countCredentials(String userId) {
        return getAllCredentials(userId).size();
    }

    public boolean hasCredentials(String userId) {
        return countCredentials(userId) > 0;
    }

    private String serializeCredentialData(Map<String, Object> data) {
        // Use Jackson or similar JSON library
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new WebAuthnException("Failed to serialize credential data", e);
        }
    }

    private Map<String, Object> deserializeCredentialData(String json) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new WebAuthnException("Failed to deserialize credential data", e);
        }
    }
}
```

**Run Tests**:
```bash
mvn test -Dtest=WebAuthnCredentialManagerTest
```

### Phase 3: Service Layer

#### 3.1 WebAuthnRegistrationService Implementation
**Test File**: `src/test/java/com/inventage/keycloak/webauthn/service/WebAuthnRegistrationServiceTest.java`
**Implementation File**: `src/main/java/com/inventage/keycloak/webauthn/service/WebAuthnRegistrationService.java`

**Tests to Pass**: 11 tests

**Key Methods to Implement**:
- `generateRegistrationOptions(userId, rpConfig)` - Create challenge and options
- `verifyAndStoreCredential(userId, registrationData, challenge)` - Verify attestation
- `classifyCredentialType(userId, type)` - Determine passwordless vs 2FA

**Implementation Notes**:
1. Use WebAuthn4J for attestation verification
2. Validate challenge expiration (max 60 seconds)
3. Check signature using WebAuthn4J's `WebAuthnManager`
4. Store credential using `WebAuthnCredentialManager`

**Run Tests**:
```bash
mvn test -Dtest=WebAuthnRegistrationServiceTest
```

#### 3.2 WebAuthnAuthenticationService Implementation
**Test File**: `src/test/java/com/inventage/keycloak/webauthn/service/WebAuthnAuthenticationServiceTest.java`
**Implementation File**: `src/main/java/com/inventage/keycloak/webauthn/service/WebAuthnAuthenticationService.java`

**Tests to Pass**: 7 tests

**Key Methods to Implement**:
- `generateAuthenticationOptions(userId, rpConfig)` - Create challenge
- `verifyAssertion(userId, assertionData, challenge)` - Verify signature and counter

**Implementation Notes**:
1. Retrieve user's credentials for `allowCredentials`
2. Use WebAuthn4J for assertion verification
3. Check signature counter to detect cloned authenticators
4. Update signature counter after successful verification

**Run Tests**:
```bash
mvn test -Dtest=WebAuthnAuthenticationServiceTest
```

### Phase 4: Integration Testing

#### 4.1 Run Integration Tests
**Test File**: `src/test/java/com/inventage/keycloak/webauthn/integration/WebAuthnIntegrationTest.java`

**Prerequisites**:
- Docker installed and running
- Extension JAR built (`mvn package`)

**Run Tests**:
```bash
mvn verify -Dtest=WebAuthnIntegrationTest
```

**Tests to Pass**: 10 end-to-end integration tests

## TDD Workflow

### Red Phase (Current State)
✅ All tests are written and failing (red)
✅ Tests define expected behavior
✅ No production code exists yet

### Green Phase (Your Task)
🔨 Implement production code to make tests pass
🔨 Start with simplest tests first
🔨 Make minimal changes to pass each test

### Refactor Phase
🔧 Improve code quality while keeping tests green
🔧 Extract common code to utilities
🔧 Optimize performance
🔧 Improve readability

## Development Workflow

### Step-by-Step Process

1. **Pick a Test Class** (start with Base64Util)
2. **Run the Tests** - they should fail (red)
   ```bash
   mvn test -Dtest=Base64UtilTest
   ```
3. **Implement Just Enough** to pass one test
4. **Run Tests Again** - one should pass (green)
5. **Refactor** if needed (keep tests green)
6. **Repeat** for next test in the class
7. **Move to Next Class** when all tests pass

### Example TDD Session

```bash
# 1. Run tests (should fail)
mvn test -Dtest=Base64UtilTest
# Result: All tests fail ❌

# 2. Implement Base64Util.encodeBase64Url()
# ... write code ...

# 3. Run tests again
mvn test -Dtest=Base64UtilTest#testEncodeBase64Url_String
# Result: 1 test passes ✅

# 4. Implement next method
# ... write code ...

# 5. Run tests
mvn test -Dtest=Base64UtilTest
# Result: More tests pass ✅

# 6. Continue until all pass
mvn test -Dtest=Base64UtilTest
# Result: All 10 tests pass ✅✅✅

# 7. Move to next class
mvn test -Dtest=ChallengeGeneratorTest
# ... repeat process ...
```

## Dependencies Required

Add to production code as needed:

```xml
<!-- WebAuthn4J for verification -->
<dependency>
    <groupId>com.webauthn4j</groupId>
    <artifactId>webauthn4j-core</artifactId>
    <version>0.29.3.RELEASE</version>
</dependency>

<!-- Jackson for JSON -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.18.2</version>
</dependency>
```

## Troubleshooting

### Tests Still Failing?

1. **Check Test Output**: Read the assertion error messages
2. **Review Test Code**: Understand what behavior is expected
3. **Debug**: Add breakpoints in your implementation
4. **Check Mocks**: Ensure mocks are set up correctly
5. **Verify Dependencies**: All required libraries imported?

### Common Issues

**Issue**: NullPointerException in tests
**Solution**: Check mock setup in `@BeforeEach` methods

**Issue**: AssertionError with "expected X but was Y"
**Solution**: Your implementation doesn't match expected behavior

**Issue**: Tests pass locally but fail in CI
**Solution**: Check for timing issues, use proper test isolation

## Progress Tracking

Create a checklist to track implementation:

- [ ] Base64Util (10 tests)
- [ ] ChallengeGenerator (13 tests)
- [ ] WebAuthnCredentialManager (12 tests)
- [ ] WebAuthnRegistrationService (11 tests)
- [ ] WebAuthnAuthenticationService (7 tests)
- [ ] WebAuthnIntegrationTest (10 tests)

## Success Criteria

✅ All 72 tests pass
✅ Code coverage >80%
✅ No test failures in CI
✅ Integration tests pass with Testcontainers
✅ No code smells or technical debt

## Next Steps After Implementation

1. **Run Full Test Suite**:
   ```bash
   mvn clean verify
   ```

2. **Generate Coverage Report**:
   ```bash
   mvn jacoco:report
   ```

3. **Review Coverage**:
   Open `target/site/jacoco/index.html`

4. **Add More Tests** for edge cases discovered

5. **Integrate with Keycloak**:
   - Create authenticator SPIs
   - Add REST endpoints
   - Configure authentication flows

## Resources

- [Test Suite Documentation](/home/tuanna47/workspace/keycloak-custom/extensions/extension-webauthn-realm/src/test/README.md)
- [Test Summary](/home/tuanna47/workspace/keycloak-custom/extensions/extension-webauthn-realm/docs/TEST_SUITE_SUMMARY.md)
- [WebAuthn4J Documentation](https://github.com/webauthn4j/webauthn4j)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)

## Getting Help

If stuck:
1. Review test code to understand expected behavior
2. Check test fixtures in `WebAuthnTestFixtures`
3. Run single test method to isolate issue
4. Add debug logging to understand flow
5. Consult WebAuthn specification for clarification

---

**Remember**: The tests define the contract. Your implementation must satisfy that contract. Start simple, make tests pass, then refactor. Trust the TDD process!
