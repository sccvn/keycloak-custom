# Unit Testing Strategy - Keycloak Custom

## Overview
Unit testing strategy for Keycloak Custom extensions, focusing on SPI implementations and business logic validation.

## Testing Framework Stack

### Core Frameworks
- **JUnit Jupiter 5.12.1**: Modern testing framework with parameterized tests and lifecycle management
- **Maven Surefire Plugin 3.5.0**: Unit test execution during `mvn test` phase
- **JBoss LogManager 3.0.6.Final**: Logging infrastructure for tests
- **SLF4J JBoss LogManager**: Logging adapter for Quarkus compatibility

### Key Configuration
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.5.0</version>
    <configuration>
        <excludedGroups>integration</excludedGroups>
        <systemPropertyVariables>
            <java.util.logging.manager>org.jboss.logmanager.LogManager</java.util.logging.manager>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

## Test Organization

### Current Test Structure
```
container/src/test/java/
├── sut/
│   ├── SystemUnderTest.java          # Test infrastructure setup
│   └── KeycloakCustomContainer.java  # Custom testcontainer wrapper
└── KeycloakCustomContainerTest.java  # Integration tests (@Tag("integration"))
```

### Recommended Unit Test Structure
```
extensions/extension-{name}/src/test/java/
├── com/inventage/keycloak/{extension}/
│   ├── infrastructure/
│   │   ├── authenticator/
│   │   │   ├── {Extension}AuthenticatorTest.java
│   │   │   └── {Extension}FactoryTest.java
│   │   ├── protocolmapper/
│   │   │   └── {Extension}ProtocolMapperTest.java
│   │   └── theme/
│   │       └── {Extension}ThemeProviderTest.java
│   └── domain/
│       └── {BusinessLogic}Test.java
```

## Unit Test Patterns

### 1. Authenticator Unit Tests

```java
package com.inventage.keycloak.noopauthenticator.infrastructure.authenticator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NoOperationAuthenticator Unit Tests")
class NoOperationAuthenticatorTest {

    private NoOperationAuthenticator authenticator;
    private AuthenticationFlowContext mockContext;
    private KeycloakSession mockSession;
    private RealmModel mockRealm;
    private UserModel mockUser;

    @BeforeEach
    void setUp() {
        authenticator = new NoOperationAuthenticator();
        mockContext = mock(AuthenticationFlowContext.class);
        mockSession = mock(KeycloakSession.class);
        mockRealm = mock(RealmModel.class);
        mockUser = mock(UserModel.class);
    }

    @Test
    @DisplayName("authenticate should call success on context")
    void authenticate_shouldCallSuccess() {
        // Act
        authenticator.authenticate(mockContext);

        // Assert
        verify(mockContext, times(1)).success();
        verifyNoMoreInteractions(mockContext);
    }

    @Test
    @DisplayName("requiresUser should return false")
    void requiresUser_shouldReturnFalse() {
        // Act
        boolean result = authenticator.requiresUser();

        // Assert
        assertFalse(result, "NoOp authenticator should not require user");
    }

    @Test
    @DisplayName("configuredFor should return false for any configuration")
    void configuredFor_shouldReturnFalse() {
        // Act
        boolean result = authenticator.configuredFor(mockSession, mockRealm, mockUser);

        // Assert
        assertFalse(result, "NoOp authenticator is never configured");
    }

    @Test
    @DisplayName("setRequiredActions should not throw exception")
    void setRequiredActions_shouldNotThrow() {
        // Act & Assert
        assertDoesNotThrow(() ->
            authenticator.setRequiredActions(mockSession, mockRealm, mockUser)
        );
    }

    @Test
    @DisplayName("close should not throw exception")
    void close_shouldNotThrow() {
        // Act & Assert
        assertDoesNotThrow(() -> authenticator.close());
    }
}
```

### 2. Factory Unit Tests

```java
@DisplayName("NoOperationAuthenticatorFactory Unit Tests")
class NoOperationAuthenticatorFactoryTest {

    private NoOperationAuthenticatorFactory factory;
    private KeycloakSession mockSession;

    @BeforeEach
    void setUp() {
        factory = new NoOperationAuthenticatorFactory();
        mockSession = mock(KeycloakSession.class);
    }

    @Test
    @DisplayName("create should return new authenticator instance")
    void create_shouldReturnNewInstance() {
        // Act
        Authenticator result = factory.create(mockSession);

        // Assert
        assertNotNull(result);
        assertInstanceOf(NoOperationAuthenticator.class, result);
    }

    @Test
    @DisplayName("getId should return consistent identifier")
    void getId_shouldReturnIdentifier() {
        // Act
        String id = factory.getId();

        // Assert
        assertNotNull(id);
        assertEquals("no-operation-authenticator", id);
    }

    @Test
    @DisplayName("getDisplayType should return user-friendly name")
    void getDisplayType_shouldReturnDisplayName() {
        // Act
        String displayType = factory.getDisplayType();

        // Assert
        assertNotNull(displayType);
        assertTrue(displayType.length() > 0);
    }

    @Test
    @DisplayName("isConfigurable should return expected value")
    void isConfigurable_shouldReturnBoolean() {
        // Act
        boolean configurable = factory.isConfigurable();

        // Assert - document expected behavior
        assertFalse(configurable, "NoOp factory should not be configurable");
    }
}
```

### 3. Protocol Mapper Unit Tests

```java
@DisplayName("NoOperationProtocolMapper Unit Tests")
class NoOperationProtocolMapperTest {

    private NoOperationProtocolMapper mapper;
    private ProtocolMapperModel mockMapperModel;
    private KeycloakSession mockSession;
    private UserSessionModel mockUserSession;
    private ClientSessionContext mockClientSession;

    @BeforeEach
    void setUp() {
        mapper = new NoOperationProtocolMapper();
        mockMapperModel = mock(ProtocolMapperModel.class);
        mockSession = mock(KeycloakSession.class);
        mockUserSession = mock(UserSessionModel.class);
        mockClientSession = mock(ClientSessionContext.class);
    }

    @Test
    @DisplayName("transformAccessToken should not modify token")
    void transformAccessToken_shouldNotModifyToken() {
        // Arrange
        AccessToken token = new AccessToken();
        token.subject("test-user");

        // Act
        AccessToken result = mapper.transformAccessToken(
            token, mockMapperModel, mockSession, mockUserSession, mockClientSession
        );

        // Assert
        assertSame(token, result, "Token should not be modified");
        assertEquals("test-user", result.getSubject());
    }

    @Test
    @DisplayName("getProtocol should return OIDC protocol")
    void getProtocol_shouldReturnOIDC() {
        // Act
        String protocol = mapper.getProtocol();

        // Assert
        assertEquals("openid-connect", protocol);
    }
}
```

## Test Coverage Strategy

### Coverage Targets
- **Statement Coverage**: ≥80%
- **Branch Coverage**: ≥75%
- **Method Coverage**: ≥85%
- **Class Coverage**: ≥90%

### Coverage Tool Integration
```xml
<!-- Add to pom.xml -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        <execution>
            <id>jacoco-check</id>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>PACKAGE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Mocking Strategy

### Keycloak Model Mocking
```java
// Use Mockito for Keycloak SPI interfaces
@ExtendWith(MockitoExtension.class)
class AuthenticatorTest {
    @Mock private KeycloakSession session;
    @Mock private RealmModel realm;
    @Mock private UserModel user;
    @Mock private AuthenticationFlowContext context;

    // Or use MockitoAnnotations.openMocks(this) in @BeforeEach
}
```

### Avoiding Over-Mocking
- Mock only external dependencies (Keycloak SPIs)
- Test actual business logic without mocks when possible
- Use test doubles for complex state management
- Consider embedded H2 database for data layer tests

## Test Data Management

### Test Data Builders
```java
public class UserModelBuilder {
    private String username = "test-user";
    private String email = "test@example.com";
    private boolean enabled = true;

    public UserModelBuilder withUsername(String username) {
        this.username = username;
        return this;
    }

    public UserModelBuilder withEmail(String email) {
        this.email = email;
        return this;
    }

    public UserModel build(KeycloakSession session, RealmModel realm) {
        UserModel user = session.users().addUser(realm, username);
        user.setEmail(email);
        user.setEnabled(enabled);
        return user;
    }
}
```

## Parameterized Tests

### Testing Multiple Scenarios
```java
@ParameterizedTest
@MethodSource("provideAuthenticationScenarios")
@DisplayName("authenticate should handle various scenarios")
void authenticate_handlesMultipleScenarios(
    String scenario,
    Map<String, String> attributes,
    boolean expectedSuccess
) {
    // Arrange
    when(mockContext.getAuthenticationSession().getAuthNote(anyString()))
        .thenReturn(attributes.get("authNote"));

    // Act
    authenticator.authenticate(mockContext);

    // Assert
    if (expectedSuccess) {
        verify(mockContext).success();
    } else {
        verify(mockContext).failure(any());
    }
}

private static Stream<Arguments> provideAuthenticationScenarios() {
    return Stream.of(
        Arguments.of("valid_auth", Map.of("authNote", "valid"), true),
        Arguments.of("invalid_auth", Map.of("authNote", "invalid"), false),
        Arguments.of("missing_auth", Map.of(), false)
    );
}
```

## Test Naming Conventions

### BDD-Style Naming
```java
// Given_When_Then pattern
@Test
void givenValidUser_whenAuthenticate_thenSuccessIsCalled() { }

// Should pattern (recommended)
@Test
void authenticate_shouldCallSuccess_whenUserIsValid() { }

// Display names for clarity
@Test
@DisplayName("Should successfully authenticate valid user with proper credentials")
void testValidAuthentication() { }
```

## Assertion Libraries

### JUnit 5 Assertions
```java
// Basic assertions
assertEquals(expected, actual);
assertNotNull(object);
assertTrue(condition);
assertThrows(Exception.class, () -> method());

// Grouped assertions
assertAll("User validation",
    () -> assertEquals("John", user.getFirstName()),
    () -> assertEquals("Doe", user.getLastName()),
    () -> assertTrue(user.isEnabled())
);

// Timeout assertions
assertTimeout(Duration.ofSeconds(1), () -> {
    authenticator.authenticate(context);
});
```

### AssertJ for Fluent Assertions (Recommended)
```xml
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <version>3.25.3</version>
    <scope>test</scope>
</dependency>
```

```java
// Fluent assertions
assertThat(result)
    .isNotNull()
    .hasFieldOrPropertyWithValue("username", "test")
    .extracting("email")
    .isEqualTo("test@example.com");
```

## Test Lifecycle

### Setup and Teardown
```java
@BeforeAll
static void setUpClass() {
    // One-time setup for all tests
    System.setProperty("keycloak.profile.feature.token_exchange", "enabled");
}

@BeforeEach
void setUp() {
    // Setup before each test
    authenticator = new NoOperationAuthenticator();
}

@AfterEach
void tearDown() {
    // Cleanup after each test
    reset(mockContext);
}

@AfterAll
static void tearDownClass() {
    // One-time cleanup
    System.clearProperty("keycloak.profile.feature.token_exchange");
}
```

## Continuous Testing

### Maven Execution
```bash
# Run unit tests only (excludes @Tag("integration"))
mvn clean test

# Run with coverage
mvn clean test jacoco:report

# Run specific test class
mvn test -Dtest=NoOperationAuthenticatorTest

# Run specific test method
mvn test -Dtest=NoOperationAuthenticatorTest#authenticate_shouldCallSuccess
```

### IDE Integration
- IntelliJ IDEA: Right-click test class → Run with Coverage
- VS Code: Use Java Test Runner extension
- Eclipse: Run As → JUnit Test

## Best Practices

1. **Test Independence**: Each test should run independently
2. **Fast Execution**: Unit tests should run in milliseconds
3. **Clear Failure Messages**: Use descriptive assertion messages
4. **One Assertion Per Test**: Focus on single behavior (when practical)
5. **Test Behavior, Not Implementation**: Focus on what, not how
6. **Avoid Test Logic**: Tests should be simple and linear
7. **Mock External Dependencies**: Never call external systems in unit tests
8. **Use @DisplayName**: Make test intent clear
9. **Follow AAA Pattern**: Arrange, Act, Assert
10. **Clean Up Resources**: Use try-with-resources or @AfterEach

## Next Steps

1. Add unit tests for all authenticator implementations
2. Implement JaCoCo coverage reporting
3. Add unit tests for protocol mappers
4. Create test data builders for complex entities
5. Set up mutation testing with PIT
6. Integrate coverage reporting in CI/CD pipeline
