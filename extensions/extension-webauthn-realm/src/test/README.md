# WebAuthn Extension Test Suite

## Overview

This directory contains a comprehensive TDD (Test-Driven Development) test suite for the Keycloak WebAuthn extension. The suite includes 63+ tests covering unit testing, integration testing, and end-to-end scenarios.

## Directory Structure

```
src/test/
├── java/
│   └── com/inventage/keycloak/webauthn/
│       ├── fixtures/              # Test fixtures and helpers
│       │   ├── WebAuthnTestFixtures.java
│       │   └── package-info.java
│       ├── integration/           # Integration tests with Testcontainers
│       │   └── WebAuthnIntegrationTest.java
│       ├── manager/               # Credential manager tests
│       │   └── WebAuthnCredentialManagerTest.java
│       ├── service/               # Service layer tests
│       │   ├── WebAuthnRegistrationServiceTest.java
│       │   └── WebAuthnAuthenticationServiceTest.java
│       └── util/                  # Utility tests
│           ├── Base64UtilTest.java
│           └── ChallengeGeneratorTest.java
└── resources/
    ├── testcontainers-compose.yml  # Docker Compose for integration tests
    └── test-realm.json             # Test realm configuration
```

## Test Classes

### Unit Tests

#### 1. WebAuthnRegistrationServiceTest (11 tests)
Tests for WebAuthn registration flow including:
- Challenge generation for valid users
- Credential verification and storage
- Credential type classification (passwordless vs 2FA)
- Error handling (expired challenges, invalid signatures, user not found)
- User verification and attestation options

**Key Test Methods:**
- `testGenerateChallenge_Success()`
- `testGenerateChallenge_UserNotFound()`
- `testVerifyAndStoreCredential_Success_Passwordless()`
- `testVerifyAndStoreCredential_Success_TwoFactor()`
- `testVerifyAndStoreCredential_ExpiredChallenge()`
- `testVerifyAndStoreCredential_InvalidSignature()`
- `testClassifyCredentialType_Passwordless()`
- `testClassifyCredentialType_TwoFactor()`
- `testClassifyCredentialType_DefaultType()`

#### 2. WebAuthnAuthenticationServiceTest (7 tests)
Tests for WebAuthn authentication flow including:
- Challenge generation with existing credentials
- Assertion verification
- Clone detection via signature counter
- Error handling (invalid signatures, expired challenges, missing credentials)

**Key Test Methods:**
- `testGenerateChallenge_WithCredentials()`
- `testVerifyAssertion_Valid()`
- `testVerifyAssertion_InvalidSignature()`
- `testVerifyAssertion_SignCountNotIncreased()` (clone detection)
- `testVerifyAssertion_ExpiredChallenge()`
- `testVerifyAssertion_CredentialNotFound()`

#### 3. WebAuthnCredentialManagerTest (12 tests)
Tests for credential lifecycle management including:
- Credential creation and storage
- Credential retrieval (by ID, all for user)
- Credential updates (signature counter, metadata)
- Credential deletion (single, all)
- Validation and error handling

**Key Test Methods:**
- `testCreateCredential_Success()`
- `testGetCredentialById_Found()`
- `testGetAllCredentials_MultipleCredentials()`
- `testUpdateSignatureCounter_Success()`
- `testDeleteCredential_Success()`
- `testDeleteAllCredentials_Success()`
- `testUpdateMetadata_Success()`

#### 4. Base64UtilTest (10 tests)
Tests for Base64 encoding/decoding utilities:
- Base64URL encoding/decoding (WebAuthn standard)
- Standard Base64 with padding
- Binary data handling
- Edge cases (empty, null, invalid input)
- Format conversion

**Key Test Methods:**
- `testEncodeBase64Url_String()`
- `testDecodeBase64Url_Valid()`
- `testEncodeBase64Url_BinaryData()`
- `testEncodeBase64Url_WebAuthnChallenge()`
- `testEncodeBase64Url_SpecialCharacters()`

#### 5. ChallengeGeneratorTest (13 tests)
Tests for cryptographic challenge generation:
- Challenge generation with various lengths
- URL-safe encoding
- Uniqueness and randomness validation
- Thread safety for concurrent generation
- WebAuthn specification compliance

**Key Test Methods:**
- `testGenerateChallenge_DefaultLength()`
- `testGenerateChallenge_Uniqueness()`
- `testGenerateChallenge_CryptographicallySecurity()`
- `testGenerateChallenge_ThreadSafety()`
- `testGenerateChallenge_WebAuthnSpecCompliance()`

### Integration Tests

#### 6. WebAuthnIntegrationTest (10 tests)
End-to-end integration tests using Testcontainers with Keycloak 26.4.6:
- Container setup and health checks
- Full registration flow
- Full authentication flow
- Multiple credentials per user
- Credential deletion
- Token generation
- Database persistence

**Key Test Methods:**
- `testKeycloakContainerRunning()`
- `testFullRegistrationFlow()`
- `testFullAuthenticationFlow()`
- `testMultipleCredentials()`
- `testDeleteCredential()`
- `testGenerateAccessToken()`

## Test Fixtures

### WebAuthnTestFixtures
Provides reusable mock data and helpers:

**Challenge Generation:**
- `generateChallenge()` - Random 32-byte challenge
- `generateChallenge(int length)` - Custom length challenge

**Mock Credentials:**
- `createMockCredential()` - Mock WebAuthn credential
- `createMockCredential(String id, String label)` - Custom credential

**WebAuthn Data:**
- `createMockAttestationObject()` - Registration attestation
- `createMockClientDataJSON(challenge, type)` - Client data
- `createMockAuthenticatorData(signCount)` - Authenticator data
- `createMockSignature()` - ECDSA signature

**Complete Data Builders:**
- `createRegistrationData(challenge)` - Full registration payload
- `createAssertionData(challenge, signCount)` - Full assertion payload
- `createRelyingPartyConfig()` - RP configuration
- `createCredentialMetadata()` - Credential metadata

## Running Tests

### All Tests
```bash
mvn test
```

### Specific Test Class
```bash
mvn test -Dtest=WebAuthnRegistrationServiceTest
```

### Integration Tests Only
```bash
mvn verify -DskipUTs
```

### With Coverage Report
```bash
mvn clean verify jacoco:report
```

### Single Test Method
```bash
mvn test -Dtest=WebAuthnRegistrationServiceTest#testGenerateChallenge_Success
```

## Test Framework Stack

- **JUnit Jupiter 5.12**: Test framework with modern annotations
- **Mockito 5.14.2**: Mocking framework for Keycloak dependencies
- **AssertJ 3.27.3**: Fluent assertions for readable tests
- **Testcontainers**: Docker-based integration testing
  - Keycloak 26.4.6 container
  - PostgreSQL 17 Alpine container

## Test Coverage Goals

| Metric | Target | Description |
|--------|--------|-------------|
| Statements | >80% | Code statement coverage |
| Branches | >75% | Conditional branch coverage |
| Functions | >80% | Method/function coverage |
| Lines | >80% | Line coverage |

## TDD Principles

All tests follow TDD best practices:

1. **Test First**: Tests written before implementation
2. **Red-Green-Refactor**: Tests fail initially, then pass with implementation
3. **Comprehensive**: Edge cases, errors, and happy paths covered
4. **Independent**: No inter-test dependencies
5. **Clear Naming**: Descriptive test names (Given-When-Then style)
6. **Arrange-Act-Assert**: Consistent test structure

## Integration Test Setup

### Docker Containers

The integration tests use Testcontainers to spin up:

1. **PostgreSQL Container**
   - Image: `postgres:17-alpine`
   - Database: `keycloak`
   - User/Password: `keycloak/keycloak`

2. **Keycloak Container**
   - Image: `quay.io/keycloak/keycloak:26.4.6`
   - Admin: `admin/admin`
   - Pre-configured test realm
   - WebAuthn extension auto-loaded

### Test Realm

The `test-realm.json` provides:
- Test realm: `test-realm`
- Pre-configured users:
  - `testuser` (with password)
  - `webauthnuser` (for WebAuthn testing)
  - `adminuser` (admin role)
- Test OAuth2 clients
- WebAuthn policies (passwordless and 2FA)

## Test Data Constants

Common test data used across tests:

```java
TEST_RP_ID = "test.example.com"
TEST_RP_NAME = "Test Application"
TEST_USER_ID = "test-user-123"
TEST_USERNAME = "testuser"
TEST_USER_EMAIL = "testuser@example.com"
TEST_CREDENTIAL_ID = "test-credential-id-456"
```

## Mocking Strategy

### KeycloakSession Mock
```java
@Mock
private KeycloakSession session;

@Mock
private RealmModel realm;

@Mock
private UserModel user;

@Mock
private UserCredentialManager credentialManager;
```

### Mock Setup Pattern
```java
@BeforeEach
void setUp() {
    when(session.getContext()).thenReturn(mock(KeycloakContext.class));
    when(session.getContext().getRealm()).thenReturn(realm);
    when(session.users()).thenReturn(mock(UserProvider.class));
    // ... additional setup
}
```

## Common Test Patterns

### Arrange-Act-Assert Pattern
```java
@Test
void testSomething() {
    // Arrange
    String input = "test data";
    when(mock.method()).thenReturn(expected);

    // Act
    String result = service.doSomething(input);

    // Assert
    assertThat(result).isEqualTo(expected);
    verify(mock, times(1)).method();
}
```

### Parameterized Tests
```java
@ParameterizedTest
@ValueSource(strings = {"value1", "value2", "value3"})
void testWithMultipleValues(String value) {
    // Test logic with different values
}
```

## Error Scenarios Tested

- User not found
- Expired challenges (5+ minutes old)
- Invalid signatures
- Duplicate credential IDs
- Clone detection (signature counter not increasing)
- Missing credentials
- Null/empty input handling
- Invalid Base64 encoding

## Next Steps

After implementing the production code:

1. Run tests: `mvn test`
2. Check coverage: `mvn jacoco:report`
3. Fix failing tests by implementing services
4. Refactor with confidence (tests as safety net)
5. Add more tests for edge cases discovered

## Continuous Integration

Recommended CI/CD pipeline:

```yaml
test:
  stage: test
  script:
    - mvn clean test
    - mvn jacoco:report
  artifacts:
    reports:
      junit: target/surefire-reports/TEST-*.xml
      cobertura: target/site/jacoco/jacoco.xml
```

## Contributing

When adding new tests:

1. Follow existing patterns and naming conventions
2. Use fixtures from `WebAuthnTestFixtures` when possible
3. Add comprehensive assertions using AssertJ
4. Mock external dependencies (Keycloak APIs)
5. Test both happy paths and error scenarios
6. Update this README with new test descriptions

## References

- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [AssertJ Documentation](https://assertj.github.io/doc/)
- [Testcontainers Documentation](https://www.testcontainers.org/)
- [WebAuthn Specification](https://www.w3.org/TR/webauthn-2/)

## License

Same as parent project.
