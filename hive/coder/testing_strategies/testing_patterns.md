# Testing Strategies and Patterns - Keycloak Custom

## Overview
Analysis of testing approaches used in the Keycloak custom extensions project.

## Testing Infrastructure

### Test Frameworks

**JUnit 5 (Jupiter)**
```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter-engine</artifactId>
    <version>5.12.1</version>
    <scope>test</scope>
</dependency>
```
- **Version**: 5.12.1
- **Engine**: Jupiter (JUnit 5)
- **Benefits**: Modern assertions, parameterized tests, nested tests, lifecycle callbacks

### Testcontainers Framework

**Core Testcontainers**
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.20.6</version>
    <scope>test</scope>
</dependency>
```

**Keycloak-Specific Container**
```xml
<dependency>
    <groupId>com.github.dasniko</groupId>
    <artifactId>testcontainers-keycloak</artifactId>
    <version>3.7.0</version>
    <scope>test</scope>
</dependency>
```

**PostgreSQL Container**
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.21.1</version>
    <scope>test</scope>
</dependency>
```

### Testing Approach

**Integration Testing Focus**
- No unit tests for extensions found
- Focus on integration tests with real Keycloak instance
- Uses Testcontainers for reproducible environments
- Docker-based testing infrastructure

## Test Categories

### Maven Surefire Plugin (Unit Tests)
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

**Configuration:**
- **Excludes**: Tests tagged with `@Tag("integration")`
- **Phase**: `test` phase (normal build)
- **Purpose**: Fast unit tests, no external dependencies
- **Current Usage**: No unit tests present in extensions

### Maven Failsafe Plugin (Integration Tests)
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <version>3.2.5</version>
    <configuration>
        <systemPropertyVariables>
            <java.util.logging.manager>org.jboss.logmanager.LogManager</java.util.logging.manager>
        </systemPropertyVariables>
        <includes>
            <include>**/*</include>
        </includes>
        <groups>integration</groups>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

**Configuration:**
- **Includes**: Only tests tagged with `@Tag("integration")`
- **Phase**: `verify` phase (after `package`)
- **Goals**: `integration-test` and `verify`
- **Purpose**: Full system integration tests

## Integration Test Pattern

### Test Class Structure

**KeycloakCustomContainerTest.java**
```java
@Tag("integration")  // Integration test marker
@Testcontainers      // Enables Testcontainers lifecycle
class KeycloakCustomContainerTest {

    private static SystemUnderTest sut;

    @BeforeAll
    static void beforeAll() {
        sut = SystemUnderTest.start();  // Start containers once
    }

    @AfterAll
    static void afterAll() {
        sut.stop();  // Cleanup containers
    }

    protected SystemUnderTest sut() {
        return sut;  // Access system under test
    }

    @Test
    void test_startup() {
        Assertions.assertTrue(sut().keycloak.isRunning());
    }

    @Test
    void test_import_realm() {
        // Test realm configuration import
    }
}
```

### Pattern Analysis

**1. Test Class Level Annotations**
```java
@Tag("integration")
```
- **Purpose**: Categorize test for selective execution
- **Maven Integration**: Failsafe plugin runs only these tests
- **Benefit**: Separate fast/slow tests, CI/CD optimization

```java
@Testcontainers
```
- **Purpose**: Enable Testcontainers extension for JUnit 5
- **Features**: Automatic container lifecycle management
- **Cleanup**: Ensures containers stopped after tests

**2. Lifecycle Management**
```java
@BeforeAll
static void beforeAll() {
    sut = SystemUnderTest.start();
}
```
- **Pattern**: Setup once for all tests
- **Performance**: Containers started once, shared across tests
- **Cost**: Slower startup, faster individual tests
- **Alternative**: `@BeforeEach` for isolation (slower but safer)

```java
@AfterAll
static void afterAll() {
    sut.stop();
}
```
- **Pattern**: Cleanup after all tests
- **Resource Management**: Stops Docker containers
- **Reliability**: Ensures cleanup even on test failures

**3. System Under Test Pattern**
```java
private static SystemUnderTest sut;

protected SystemUnderTest sut() {
    return sut;
}
```
- **Encapsulation**: Controlled access to SUT
- **Flexibility**: Can add logic in accessor method
- **Inheritance**: Protected for subclass access

## System Under Test (SUT) Pattern

### SystemUnderTest.java

**Class Purpose:**
- Encapsulates entire test environment
- Manages multiple containers (Keycloak, PostgreSQL)
- Provides unified interface for tests
- Handles networking between containers

### Container Architecture

```java
public class SystemUnderTest {

    private Network network;
    public PostgreSQLContainer postgres;
    public KeycloakCustomContainer keycloak;

    public static SystemUnderTest start() {
        final SystemUnderTest sut = new SystemUnderTest(Network.newNetwork());
        sut.startComponents();
        return sut;
    }

    private void startComponents() {
        startPostgres(network);
        startKeycloak(postgres);
    }
}
```

### Pattern: Builder Pattern for Test Environment

**Step 1: Create Network**
```java
Network.newNetwork()
```
- Creates Docker network for container communication
- Allows containers to communicate by network alias
- Isolated network per test run

**Step 2: Start PostgreSQL**
```java
private PostgreSQLContainer startPostgres(Network network) {
    postgres = new PostgreSQLContainer<>(DOCKER_IMAGE_NAME_POSTGRES)
            .withLogConsumer(new Slf4jLogConsumer(LOGGER))
            .withNetwork(network)
            .withNetworkAliases(NETWORK_ALIAS_POSTGRES)
            .withDatabaseName(DATABASE_NAME_POSTGRES)
            .withUsername("postgres")
            .withPassword("postgres");
    try {
        postgres.start();
        return postgres;
    } catch (Exception e) {
        System.err.println(postgres.getLogs());
        throw e;
    }
}
```

**Patterns:**
- **Fluent API**: Chained configuration methods
- **Network Aliasing**: `NETWORK_ALIAS_POSTGRES` = "postgres"
- **Log Consumption**: Real-time log streaming via SLF4J
- **Error Handling**: Dump logs on startup failure

**Step 3: Start Keycloak**
```java
private KeycloakCustomContainer startKeycloak(PostgreSQLContainer postgres) {
    keycloak = new KeycloakCustomContainer()
            .withLogConsumer(new Slf4jLogConsumer(LOGGER))
            .withNetwork(network)
            .withEnv(getKeycloakEnvs());
    try {
        keycloak.start();
        return keycloak;
    } catch (Exception e) {
        System.err.println(keycloak.getLogs());
        throw e;
    }
}
```

**Environment Configuration:**
```java
private Map<String, String> getKeycloakEnvs() {
    HashMap<String, String> envs = new HashMap<>();
    envs.put("KC_HTTP_ENABLED", "true");
    envs.put("KC_HOSTNAME_STRICT_HTTPS", "false");
    envs.put("REALM_MASTER_SSL_REQUIRED", "none");
    envs.put("KC_BOOTSTRAP_ADMIN_CLIENT_ID", "temp-admin");
    envs.put("KC_BOOTSTRAP_ADMIN_CLIENT_SECRET", "admin");
    envs.put("KEYCLOAK_CONFIG_CLI_CLIENT_ID", "keycloak-config-cli");
    envs.put("KEYCLOAK_CONFIG_CLI_CLIENT_SECRET", "keycloak-config-cli");
    envs.put("KEYCLOAK_GRANTTYPE", "client_credentials");
    envs.put("KC_DB", "postgres");
    envs.put("KC_DB_USERNAME", postgres.getUsername());
    envs.put("KC_DB_PASSWORD", postgres.getPassword());
    envs.put("KC_DB_URL", String.format("jdbc:postgresql://%s:5432/%s?loggerLevel=OFF",
        NETWORK_ALIAS_POSTGRES, DATABASE_NAME_POSTGRES));
    envs.put("KC_LOG_LEVEL", "info");
    return envs;
}
```

**Configuration Patterns:**
1. **HTTP Mode**: Testing without TLS complexity
2. **Hostname Strictness**: Disabled for container environments
3. **Admin Bootstrap**: Automated admin client creation
4. **Database Connection**: Uses network alias for container communication
5. **Config CLI**: Pre-configured client for realm import
6. **Logger Level**: Reduced logging for test performance

## Custom Container: KeycloakCustomContainer

### Class Structure

```java
public class KeycloakCustomContainer extends ExtendableKeycloakContainer<KeycloakCustomContainer> {

    public static final String DEFAULT_DOCKER_IMAGE_NAME =
        "ghcr.io/inventage/keycloak-custom/com.inventage.keycloak.custom.container:latest";

    private static final String DEFAULT_WAIT_LOG_REGEX = ".*KEYCLOAK SETUP FINISHED.*";
    private static final long DEFAULT_STARTUP_TIMEOUT = 3;

    private final WaitStrategy waitStrategy;
    private final long startupTimeout;

    public KeycloakCustomContainer() {
        this(DEFAULT_DOCKER_IMAGE_NAME);
    }

    public KeycloakCustomContainer(String dockerImageName) {
        super(dockerImageName);
        this.startupTimeout = DEFAULT_STARTUP_TIMEOUT;
        this.waitStrategy = new LogMessageWaitStrategy()
                .withRegEx(DEFAULT_WAIT_LOG_REGEX)
                .withTimes(1)
                .withStartupTimeout(Duration.ofMinutes(this.startupTimeout));
        this.withLogConsumer(new Slf4jLogConsumer(LOGGER));
    }

    @Override
    protected void configure() {
        super.configure();
        setWaitStrategy(this.waitStrategy);
    }
}
```

### Pattern Analysis

**1. Template Method Pattern**
```java
@Override
protected void configure() {
    super.configure();  // Call parent configuration
    setWaitStrategy(this.waitStrategy);  // Add custom configuration
}
```
- **Parent**: Handles standard Keycloak setup
- **Custom**: Adds custom wait strategy
- **Extension Point**: `configure()` method

**2. Wait Strategy Pattern**
```java
new LogMessageWaitStrategy()
    .withRegEx(DEFAULT_WAIT_LOG_REGEX)
    .withTimes(1)
    .withStartupTimeout(Duration.ofMinutes(this.startupTimeout))
```
- **Type**: Log message monitoring
- **Pattern**: Regex match on log output
- **Expected**: "KEYCLOAK SETUP FINISHED" message
- **Timeout**: 3 minutes maximum wait
- **Purpose**: Ensures all custom extensions loaded before tests run

**Benefits:**
- **Reliability**: Tests start only when Keycloak fully ready
- **Custom Extensions**: Waits for extension deployment
- **Realm Import**: Waits for keycloak-config-cli completion
- **Failure Detection**: Timeout prevents hanging tests

**3. Generic Type Parameter**
```java
public class KeycloakCustomContainer extends ExtendableKeycloakContainer<KeycloakCustomContainer>
```
- **Pattern**: Curiously Recurring Template Pattern (CRTP)
- **Purpose**: Type-safe fluent API
- **Benefit**: Method chaining returns correct type

## Test Cases

### Test 1: Startup Verification
```java
@Test
void test_startup() {
    Assertions.assertTrue(sut().keycloak.isRunning());
}
```

**Pattern:** Smoke Test
- **Purpose**: Verify basic container functionality
- **Scope**: Minimal test, confirms environment ready
- **Fast**: Quick validation
- **Fail Fast**: If this fails, other tests will fail too

### Test 2: Realm Import Verification
```java
@Test
void test_import_realm() {
    Keycloak keycloakAdminClient = KeycloakBuilder.builder()
            .serverUrl(sut().keycloak.getAuthServerUrl())
            .realm("master")
            .clientId("admin-cli")
            .username(sut().keycloak.getAdminUsername())
            .password(sut().keycloak.getAdminPassword())
            .build();

    Optional<RealmRepresentation> example1 = keycloakAdminClient.realms()
        .findAll()
        .stream()
        .filter(realmRepresentation -> realmRepresentation.getRealm().equals("example1"))
        .findFirst();

    Assertions.assertTrue(example1.isPresent(),
        "Realm `example1` should exist. Realm import via keycloak-config-cli failed.");
}
```

**Patterns:**

**1. Admin Client Pattern**
```java
Keycloak keycloakAdminClient = KeycloakBuilder.builder()
    .serverUrl(sut().keycloak.getAuthServerUrl())
    .realm("master")
    .clientId("admin-cli")
    .username(sut().keycloak.getAdminUsername())
    .password(sut().keycloak.getAdminPassword())
    .build();
```
- **Builder**: Fluent API for client construction
- **Dynamic URL**: Uses container's exposed URL
- **Admin Realm**: Authenticates to master realm
- **Admin CLI**: Uses built-in admin client

**2. Stream-Based Search**
```java
Optional<RealmRepresentation> example1 = keycloakAdminClient.realms()
    .findAll()
    .stream()
    .filter(realm -> realm.getRealm().equals("example1"))
    .findFirst();
```
- **Functional Style**: Java Streams API
- **Optional**: Type-safe null handling
- **Filter**: Declarative realm search

**3. Assertion with Message**
```java
Assertions.assertTrue(example1.isPresent(),
    "Realm `example1` should exist. Realm import via keycloak-config-cli failed.");
```
- **Descriptive**: Clear failure message
- **Context**: Explains what failed and why
- **Debugging**: Points to specific failure cause

## Testing Gaps Analysis

### Missing Unit Tests

**No Unit Tests Found For:**
1. NoOperationAuthenticator
2. NoOperationFormAuthenticator
3. NoOperationFormModel
4. NoOperationProtocolMapper
5. InvitationClasspathThemeProviderFactory

### Why Unit Tests Are Missing

**Possible Reasons:**
1. **Keycloak SPI Complexity**: Hard to mock Keycloak framework
2. **Integration Focus**: Extensions only work with real Keycloak
3. **Simple Logic**: Minimal business logic to unit test
4. **Framework Dependency**: Most logic in framework, not extension

### Unit Test Challenges

**Example: Testing NoOperationAuthenticator**
```java
// Requires mocking:
// - AuthenticationFlowContext
// - KeycloakSession
// - RealmModel
// - UserModel
// - HttpRequest
// - LoginFormsProvider

@Test
void testAuthenticate() {
    // Mock creation complexity
    AuthenticationFlowContext context = mock(AuthenticationFlowContext.class);
    KeycloakSession session = mock(KeycloakSession.class);
    // ... many more mocks

    NoOperationAuthenticator authenticator = new NoOperationAuthenticator();
    authenticator.authenticate(context);

    verify(context).success();  // Verify behavior
}
```

**Problems:**
- **Mock Complexity**: Many interdependent Keycloak classes
- **Framework Coupling**: Hard to isolate extension logic
- **Low Value**: Testing trivial pass-through logic
- **Maintenance**: Mocks break when Keycloak API changes

### Recommended Unit Testing Strategy

**What to Unit Test:**
1. **Business Logic**: Complex validation, calculations
2. **Data Transformations**: Model conversions, claim calculations
3. **Utilities**: Helper classes, formatters, parsers
4. **Configuration**: Config parsing and validation

**Example: Testable Business Logic**
```java
// Extractable for unit testing
public class UserValidator {
    public ValidationResult validate(UserData data) {
        // Pure business logic, no Keycloak dependencies
        if (data.getEmail() == null) {
            return ValidationResult.error("Email required");
        }
        // More validation
        return ValidationResult.success();
    }
}

// Unit test (easy)
@Test
void testValidation() {
    UserValidator validator = new UserValidator();
    UserData data = new UserData();

    ValidationResult result = validator.validate(data);

    assertTrue(result.hasErrors());
    assertEquals("Email required", result.getFirstError());
}
```

### Integration Test Coverage

**Current Coverage:**
- [x] Container startup
- [x] Realm import
- [ ] Authentication flow execution
- [ ] Custom authenticator registration
- [ ] Protocol mapper claim injection
- [ ] Theme template rendering
- [ ] Form submission handling

**Recommended Additional Tests:**

**1. Authenticator Registration Test**
```java
@Test
void test_custom_authenticators_registered() {
    var authenticators = adminClient.serverInfo()
        .getInfo()
        .getProviders()
        .get("authenticator");

    assertTrue(authenticators.contains("no-operation-authenticator"));
    assertTrue(authenticators.contains("no-operation-form-authenticator"));
}
```

**2. Protocol Mapper Test**
```java
@Test
void test_protocol_mapper_adds_claim() {
    // Configure client with custom mapper
    // Request token
    // Decode token
    // Verify custom claim present
    assertEquals("claimValue", token.getOtherClaims().get("claimName"));
}
```

**3. Form Rendering Test**
```java
@Test
void test_form_authenticator_renders_template() {
    // Configure authentication flow
    // Trigger authentication
    // Verify form rendered
    // Submit form
    // Verify success
}
```

## Test Execution

### Local Development
```bash
# Unit tests only (fast)
mvn test

# Integration tests only
mvn verify -DskipTests

# All tests
mvn verify

# Skip all tests
mvn install -DskipTests
```

### CI/CD Pipeline
```yaml
# Typical CI pipeline
stages:
  - build
  - test
  - integration-test
  - deploy

build:
  script: mvn clean compile

unit-test:
  script: mvn test
  artifacts:
    reports:
      junit: target/surefire-reports/*.xml

integration-test:
  script: mvn verify -DskipTests
  services:
    - docker:dind  # Docker-in-Docker for Testcontainers
  artifacts:
    reports:
      junit: target/failsafe-reports/*.xml
```

## Best Practices Applied

### 1. Separate Test Categories
- Unit tests: Fast, no external dependencies
- Integration tests: Slower, full environment

### 2. Testcontainers Benefits
- **Reproducible**: Same environment every time
- **Isolated**: Each run gets fresh containers
- **Realistic**: Real Keycloak, not mocks
- **CI-Friendly**: Works in Docker-enabled CI

### 3. Lifecycle Management
- Containers started once per test class (performance)
- Automatic cleanup (reliability)
- Network isolation (test independence)

### 4. Logging Integration
- Container logs streamed to test output
- Errors dump full logs
- Easier debugging of test failures

### 5. Environment Configuration
- Test-specific settings (HTTP, no TLS)
- Predictable credentials
- Fast startup configuration

## Recommended Improvements

### 1. Add Unit Tests for Business Logic
```java
// Extract testable logic
public class ClaimValueCalculator {
    public String calculateClaimValue(UserModel user, ProtocolMapperModel config) {
        // Pure logic, easy to test
    }
}
```

### 2. Increase Integration Test Coverage
- Test each custom authenticator in flow
- Verify protocol mapper claims
- Test theme template rendering
- Validate error handling

### 3. Add Performance Tests
```java
@Test
void test_authentication_performance() {
    long start = System.currentTimeMillis();
    for (int i = 0; i < 100; i++) {
        authenticateUser();
    }
    long duration = System.currentTimeMillis() - start;
    assertTrue(duration < 5000, "100 authentications should complete in <5s");
}
```

### 4. Add Security Tests
```java
@Test
void test_sql_injection_prevention() {
    String maliciousInput = "'; DROP TABLE users; --";
    // Verify input sanitized
}

@Test
void test_xss_prevention() {
    String xssInput = "<script>alert('XSS')</script>";
    // Verify HTML escaped
}
```

### 5. Add Contract Tests
```java
@Test
void test_authenticator_contract() {
    Authenticator auth = factory.create(session);

    // Verify all interface methods implemented
    assertNotNull(auth);
    assertDoesNotThrow(() -> auth.authenticate(context));
    assertDoesNotThrow(() -> auth.action(context));
    assertDoesNotThrow(() -> auth.close());
}
```

## Conclusion

**Testing Strengths:**
- Solid integration testing foundation
- Testcontainers for reproducible environments
- Proper test categorization
- Good lifecycle management

**Testing Gaps:**
- No unit tests for extensions
- Limited integration test coverage
- No performance testing
- No security testing
- No contract testing

**Overall Assessment:**
The testing strategy is appropriate for demonstration extensions but would need significant expansion for production use. The Testcontainers-based integration testing provides a strong foundation to build upon.
