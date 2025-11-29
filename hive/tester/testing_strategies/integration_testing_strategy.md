# Integration Testing Strategy - Keycloak Custom

## Overview
Integration testing strategy using Testcontainers for full-stack Keycloak extension validation with PostgreSQL database.

## Integration Test Framework

### Core Technologies
- **Testcontainers Keycloak 3.7.0**: Dockerized Keycloak for integration tests
- **Testcontainers PostgreSQL 1.21.1**: Real database for testing
- **Testcontainers JUnit 1.20.6**: TestContainer lifecycle management
- **Maven Failsafe Plugin 3.2.5**: Integration test execution during `mvn verify` phase
- **Keycloak Admin Client**: REST API for realm configuration validation

### Test Identification
```java
@Tag("integration")  // Marker for Maven Failsafe plugin
@Testcontainers      // Enable TestContainers JUnit integration
class KeycloakCustomContainerTest {
    // Integration tests
}
```

## Maven Configuration

### Failsafe Plugin Setup
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
        <groups>integration</groups> <!-- Only tests with @Tag("integration") -->
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

### Build Lifecycle
```
1. mvn package → Builds Docker image (pre-integration-test phase)
2. mvn integration-test → Runs @Tag("integration") tests
3. mvn verify → Checks test results and fails build if needed
```

## Current Integration Test Architecture

### System Under Test (SUT) Pattern

**File**: `container/src/test/java/sut/SystemUnderTest.java`

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

    private PostgreSQLContainer startPostgres(Network network) {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withLogConsumer(new Slf4jLogConsumer(LOGGER))
                .withNetwork(network)
                .withNetworkAliases("postgres")
                .withDatabaseName("postgres")
                .withUsername("postgres")
                .withPassword("postgres");
        postgres.start();
        return postgres;
    }

    private KeycloakCustomContainer startKeycloak(PostgreSQLContainer postgres) {
        keycloak = new KeycloakCustomContainer()
                .withLogConsumer(new Slf4jLogConsumer(LOGGER))
                .withNetwork(network)
                .withEnv(getKeycloakEnvs());
        keycloak.start();
        return keycloak;
    }
}
```

### Container Environment Configuration

```java
private Map<String, String> getKeycloakEnvs() {
    HashMap<String, String> envs = new HashMap<>();
    envs.put("KC_HTTP_ENABLED", "true");
    envs.put("KC_HOSTNAME_STRICT_HTTPS", "false");
    envs.put("REALM_MASTER_SSL_REQUIRED", "none");

    // Bootstrap admin credentials
    envs.put("KC_BOOTSTRAP_ADMIN_CLIENT_ID", "temp-admin");
    envs.put("KC_BOOTSTRAP_ADMIN_CLIENT_SECRET", "admin");

    // Config CLI credentials
    envs.put("KEYCLOAK_CONFIG_CLI_CLIENT_ID", "keycloak-config-cli");
    envs.put("KEYCLOAK_CONFIG_CLI_CLIENT_SECRET", "keycloak-config-cli");
    envs.put("KEYCLOAK_GRANTTYPE", "client_credentials");
    envs.put("KEYCLOAK_CLIENTID", "${KC_BOOTSTRAP_ADMIN_CLIENT_ID}");
    envs.put("KEYCLOAK_CLIENTSECRET", "${KC_BOOTSTRAP_ADMIN_CLIENT_SECRET}");

    // Database configuration
    envs.put("KC_DB", "postgres");
    envs.put("KC_DB_USERNAME", postgres.getUsername());
    envs.put("KC_DB_PASSWORD", postgres.getPassword());
    envs.put("KC_DB_URL", String.format(
        "jdbc:postgresql://%s:5432/%s?loggerLevel=OFF",
        NETWORK_ALIAS_POSTGRES, DATABASE_NAME_POSTGRES
    ));
    envs.put("KC_LOG_LEVEL", "info");

    return envs;
}
```

## Integration Test Patterns

### 1. Container Startup Validation

**File**: `container/src/test/java/KeycloakCustomContainerTest.java`

```java
@Tag("integration")
@Testcontainers
class KeycloakCustomContainerTest {

    private static SystemUnderTest sut;

    @BeforeAll
    static void beforeAll() {
        sut = SystemUnderTest.start();
    }

    @AfterAll
    static void afterAll() {
        sut.stop();
    }

    @Test
    @DisplayName("Keycloak container should start successfully")
    void test_startup() {
        assertTrue(sut.keycloak.isRunning(),
            "Keycloak container must be running");
    }
}
```

### 2. Realm Configuration Validation

```java
@Test
@DisplayName("Imported realm configuration should be present")
void test_import_realm() {
    // Arrange
    Keycloak adminClient = KeycloakBuilder.builder()
            .serverUrl(sut.keycloak.getAuthServerUrl())
            .realm("master")
            .clientId("admin-cli")
            .username(sut.keycloak.getAdminUsername())
            .password(sut.keycloak.getAdminPassword())
            .build();

    // Act
    Optional<RealmRepresentation> example1 = adminClient.realms()
        .findAll()
        .stream()
        .filter(realm -> realm.getRealm().equals("example1"))
        .findFirst();

    // Assert
    assertTrue(example1.isPresent(),
        "Realm 'example1' should exist. Realm import via keycloak-config-cli failed.");
}
```

### 3. Extension SPI Integration Tests

```java
@Test
@DisplayName("Custom authenticator should be registered in authentication flows")
void test_custom_authenticator_registered() {
    // Arrange
    Keycloak adminClient = getAdminClient();
    RealmResource realmResource = adminClient.realm("example1");

    // Act
    List<AuthenticationFlowRepresentation> flows =
        realmResource.flows().getFlows();

    // Assert
    Optional<AuthenticationFlowRepresentation> customFlow = flows.stream()
        .filter(flow -> flow.getAlias().equals("custom-auth-flow"))
        .findFirst();

    assertTrue(customFlow.isPresent(), "Custom authentication flow should exist");

    // Verify authenticator is in the flow
    List<AuthenticationExecutionInfoRepresentation> executions =
        realmResource.flows().getExecutions(customFlow.get().getAlias());

    boolean hasNoOpAuthenticator = executions.stream()
        .anyMatch(exec -> "no-operation-authenticator".equals(exec.getProviderId()));

    assertTrue(hasNoOpAuthenticator,
        "NoOperation authenticator should be in custom flow");
}
```

### 4. Protocol Mapper Integration Tests

```java
@Test
@DisplayName("Custom protocol mapper should transform tokens correctly")
void test_protocol_mapper_token_transformation() {
    // Arrange
    Keycloak adminClient = getAdminClient();
    String testUser = createTestUser(adminClient, "test-user");

    // Act - Authenticate and get token
    AccessTokenResponse tokenResponse = authenticateUser(
        sut.keycloak.getAuthServerUrl(),
        "example1",
        "test-client",
        testUser,
        "password123"
    );

    // Assert - Decode and verify token claims
    AccessToken token = decodeAccessToken(tokenResponse.getToken());

    assertNotNull(token.getOtherClaims().get("custom_claim"),
        "Custom protocol mapper should add custom claim");
    assertEquals("expected_value",
        token.getOtherClaims().get("custom_claim"),
        "Custom claim should have expected value");
}
```

### 5. Theme Integration Tests

```java
@Test
@DisplayName("Custom login theme should be available")
void test_custom_theme_available() {
    // Arrange
    Keycloak adminClient = getAdminClient();
    RealmResource realmResource = adminClient.realm("example1");

    // Act
    RealmRepresentation realm = realmResource.toRepresentation();

    // Assert
    assertEquals("inventage", realm.getLoginTheme(),
        "Custom login theme should be configured");

    // Verify theme resources are accessible
    String loginPageUrl = sut.keycloak.getAuthServerUrl() +
        "/realms/example1/protocol/openid-connect/auth?client_id=test-client&redirect_uri=http://localhost&response_type=code";

    HttpResponse response = HttpClient.newHttpClient()
        .send(HttpRequest.newBuilder()
            .uri(URI.create(loginPageUrl))
            .build(),
        HttpResponse.BodyHandlers.ofString());

    assertTrue(response.body().contains("inventage"),
        "Login page should use custom theme");
}
```

## Database Integration Testing

### PostgreSQL Container Setup

```java
@Test
@DisplayName("Database schema should be created correctly")
void test_database_schema() throws SQLException {
    // Arrange
    String jdbcUrl = sut.postgres.getJdbcUrl();
    Connection connection = DriverManager.getConnection(
        jdbcUrl,
        sut.postgres.getUsername(),
        sut.postgres.getPassword()
    );

    // Act
    DatabaseMetaData metaData = connection.getMetaData();
    ResultSet tables = metaData.getTables(null, "public", "%", new String[]{"TABLE"});

    // Assert - Verify Keycloak tables exist
    List<String> tableNames = new ArrayList<>();
    while (tables.next()) {
        tableNames.add(tables.getString("TABLE_NAME"));
    }

    assertTrue(tableNames.contains("realm"),
        "Keycloak realm table should exist");
    assertTrue(tableNames.contains("user_entity"),
        "Keycloak user_entity table should exist");
    assertTrue(tableNames.contains("authentication_flow"),
        "Authentication flow table should exist");

    connection.close();
}
```

### Data Persistence Tests

```java
@Test
@DisplayName("User data should persist across container restarts")
void test_user_persistence() {
    // Arrange
    Keycloak adminClient = getAdminClient();
    String userId = createTestUser(adminClient, "persistent-user");

    // Act - Restart Keycloak (not database)
    sut.keycloak.stop();
    sut.keycloak = sut.startKeycloak(sut.postgres);

    adminClient = getAdminClient(); // Reconnect

    // Assert
    UserRepresentation user = adminClient.realm("example1")
        .users()
        .get(userId)
        .toRepresentation();

    assertNotNull(user, "User should persist after Keycloak restart");
    assertEquals("persistent-user", user.getUsername());
}
```

## Network Configuration Testing

### Container Networking

```java
@Test
@DisplayName("Keycloak should communicate with PostgreSQL via Docker network")
void test_container_networking() {
    // Arrange
    String postgresHost = "postgres"; // Network alias

    // Act - Keycloak logs should show successful DB connection
    String keycloakLogs = sut.keycloak.getLogs();

    // Assert
    assertTrue(keycloakLogs.contains("Database connection successful") ||
               keycloakLogs.contains("Schema created"),
        "Keycloak should successfully connect to PostgreSQL via network alias");
}
```

## Setup Script Integration Tests

### keycloak-config-cli Validation

```java
@Test
@DisplayName("keycloak-setup.sh should import all realm configurations")
void test_setup_script_execution() {
    // Arrange
    Keycloak adminClient = getAdminClient();

    // Act
    List<RealmRepresentation> realms = adminClient.realms().findAll();

    // Assert
    List<String> realmNames = realms.stream()
        .map(RealmRepresentation::getRealm)
        .collect(Collectors.toList());

    assertAll("All configured realms should be imported",
        () -> assertTrue(realmNames.contains("master"),
            "Master realm should exist"),
        () -> assertTrue(realmNames.contains("example1"),
            "Example1 realm should be imported via keycloak-config-cli")
    );
}
```

### Bootstrap Client Creation

```java
@Test
@DisplayName("Setup script should create permanent service account client")
void test_permanent_service_account_created() {
    // Arrange
    Keycloak adminClient = getAdminClient();
    RealmResource masterRealm = adminClient.realm("master");

    // Act
    List<ClientRepresentation> clients = masterRealm.clients().findAll();

    // Assert
    Optional<ClientRepresentation> configCliClient = clients.stream()
        .filter(client -> "keycloak-config-cli".equals(client.getClientId()))
        .findFirst();

    assertTrue(configCliClient.isPresent(),
        "Permanent keycloak-config-cli client should be created");
    assertTrue(configCliClient.get().isServiceAccountsEnabled(),
        "Config CLI client should have service account enabled");
}
```

## Performance Integration Tests

### Startup Time Validation

```java
@Test
@DisplayName("Keycloak should start within acceptable time")
void test_startup_performance() {
    // Arrange
    long maxStartupTimeSeconds = 120;

    // Act
    long startTime = System.currentTimeMillis();
    SystemUnderTest testSut = SystemUnderTest.start();
    long endTime = System.currentTimeMillis();
    long startupTimeSeconds = (endTime - startTime) / 1000;

    // Assert
    assertTrue(startupTimeSeconds < maxStartupTimeSeconds,
        String.format("Startup time %ds exceeds maximum %ds",
            startupTimeSeconds, maxStartupTimeSeconds));

    testSut.stop();
}
```

## Best Practices

1. **Container Reuse**: Use `@BeforeAll` to start containers once per test class
2. **Network Isolation**: Each test suite should use its own Docker network
3. **Log Collection**: Always attach log consumers for debugging
4. **Resource Cleanup**: Ensure containers stop in `@AfterAll`
5. **Wait Strategies**: Use appropriate wait strategies (health checks, log patterns)
6. **Parallel Execution**: Be cautious with parallel test execution due to Docker resource limits
7. **CI/CD Optimization**: Consider using Testcontainers Cloud for faster CI builds
8. **Database State**: Reset database state between tests if needed
9. **Environment Variables**: Use realistic environment configuration
10. **Test Data Isolation**: Each test should create its own test data

## Execution Commands

```bash
# Run integration tests only
mvn verify -DskipUnitTests

# Run all tests (unit + integration)
mvn clean verify

# Run integration tests with Docker build
mvn clean verify -DmultiArchBuild=false

# Skip integration tests
mvn clean install -DskipITs

# Run specific integration test
mvn verify -Dit.test=KeycloakCustomContainerTest
```

## CI/CD Integration

### GitHub Actions Configuration
```yaml
- name: Run Integration Tests
  run: ./mvnw verify -DskipUnitTests
  env:
    TESTCONTAINERS_RYUK_DISABLED: true  # For CI environments
```

## Troubleshooting

### Common Issues

1. **Container Won't Start**
   - Check Docker daemon is running
   - Verify port conflicts (8080, 5432)
   - Review container logs via `container.getLogs()`

2. **Database Connection Failures**
   - Ensure network aliases are correct
   - Verify JDBC URL format
   - Check PostgreSQL container is ready

3. **Slow Test Execution**
   - Use container reuse across tests
   - Enable Testcontainers image caching
   - Consider using `localstack` for AWS services

## Next Steps

1. Add integration tests for all custom authenticators
2. Implement end-to-end authentication flow tests
3. Add performance benchmarking tests
4. Test multi-realm scenarios
5. Add tests for realm import/export functionality
6. Implement chaos engineering tests (container failures)
