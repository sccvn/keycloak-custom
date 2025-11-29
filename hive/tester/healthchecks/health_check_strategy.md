# Health Check Strategy - Keycloak Custom

## Overview
Comprehensive health check and readiness probe strategy for Keycloak Custom to ensure reliable deployment and operation in containerized environments.

## Health Check Endpoints

### Built-in Health Endpoints

**Configuration**: `config/src/main/resources/keycloak/conf/keycloak.conf`

```properties
# Enable health check endpoints
health-enabled=true
```

### Available Health Endpoints

#### 1. Overall Health Check
**Endpoint**: `GET /health`

**Response**:
```json
{
  "status": "UP",
  "checks": [
    {
      "name": "Database connections health check",
      "status": "UP"
    },
    {
      "name": "Keycloak services health check",
      "status": "UP"
    }
  ]
}
```

**Status Codes**:
- `200 OK` - All health checks passed
- `503 Service Unavailable` - One or more health checks failed
- `500 Internal Server Error` - Health check framework failure

#### 2. Liveness Probe
**Endpoint**: `GET /health/live`

**Purpose**: Determines if the application is running and not in a deadlock state

**Response**:
```json
{
  "status": "UP",
  "checks": [
    {
      "name": "Liveness check",
      "status": "UP"
    }
  ]
}
```

**Behavior**:
- Returns `UP` if Keycloak process is responsive
- Returns `DOWN` if application is deadlocked or unresponsive
- Container orchestrator should **restart** container if this fails

#### 3. Readiness Probe
**Endpoint**: `GET /health/ready`

**Purpose**: Determines if the application is ready to accept traffic

**Response**:
```json
{
  "status": "UP",
  "checks": [
    {
      "name": "Database connections health check",
      "status": "UP",
      "data": {
        "connection": "established",
        "pool_size": 20,
        "active_connections": 5
      }
    },
    {
      "name": "Keycloak startup check",
      "status": "UP",
      "data": {
        "startup_complete": true
      }
    }
  ]
}
```

**Behavior**:
- Returns `UP` only when all dependencies are ready
- Returns `DOWN` during startup or if dependencies fail
- Container orchestrator should **remove from load balancer** if this fails

#### 4. Startup Probe
**Endpoint**: `GET /health/started`

**Purpose**: Indicates if the application has completed its startup sequence

**Response**:
```json
{
  "status": "UP",
  "checks": [
    {
      "name": "Startup check",
      "status": "UP",
      "data": {
        "startup_time_ms": 45230
      }
    }
  ]
}
```

## Kubernetes Health Check Configuration

### Helm Values Configuration

**File**: `helm/src/main/resources/values.yaml`

```yaml
# Recommended health check configuration
livenessProbe:
  httpGet:
    path: /health/live
    port: http
  initialDelaySeconds: 60      # Wait for initial startup
  periodSeconds: 30            # Check every 30 seconds
  timeoutSeconds: 5            # 5 second timeout
  successThreshold: 1          # Must succeed once to be considered healthy
  failureThreshold: 3          # Restart after 3 consecutive failures

readinessProbe:
  httpGet:
    path: /health/ready
    port: http
  initialDelaySeconds: 30      # Start checking early
  periodSeconds: 10            # Check frequently
  timeoutSeconds: 5
  successThreshold: 1
  failureThreshold: 3          # Remove from service after 3 failures

startupProbe:
  httpGet:
    path: /health/started
    port: http
  initialDelaySeconds: 0       # Start immediately
  periodSeconds: 5             # Check every 5 seconds
  timeoutSeconds: 5
  successThreshold: 1
  failureThreshold: 60         # Allow up to 5 minutes for startup (60 * 5s)
```

### Production-Grade Configuration

```yaml
# For production with longer startup times
startupProbe:
  httpGet:
    path: /health/started
    port: http
  initialDelaySeconds: 0
  periodSeconds: 10
  timeoutSeconds: 5
  successThreshold: 1
  failureThreshold: 30         # Allow up to 5 minutes (30 * 10s)

livenessProbe:
  httpGet:
    path: /health/live
    port: http
  initialDelaySeconds: 0       # Startup probe handles initial delay
  periodSeconds: 30
  timeoutSeconds: 10
  successThreshold: 1
  failureThreshold: 5          # More tolerant to transient issues

readinessProbe:
  httpGet:
    path: /health/ready
    port: http
  initialDelaySeconds: 0
  periodSeconds: 5             # Check frequently for quick recovery
  timeoutSeconds: 5
  successThreshold: 2          # Require 2 successes to mark ready
  failureThreshold: 3
```

## Custom Health Checks

### Database Connection Health Check

```java
package com.inventage.keycloak.health;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.Connection;

@Readiness
@ApplicationScoped
public class DatabaseHealthCheck implements HealthCheck {

    @Inject
    DataSource dataSource;

    @Override
    public HealthCheckResponse call() {
        try (Connection connection = dataSource.getConnection()) {
            boolean isValid = connection.isValid(5);

            if (isValid) {
                return HealthCheckResponse
                    .named("Database connection check")
                    .up()
                    .withData("database", "PostgreSQL")
                    .withData("connection", "established")
                    .build();
            } else {
                return HealthCheckResponse
                    .named("Database connection check")
                    .down()
                    .withData("database", "PostgreSQL")
                    .withData("connection", "invalid")
                    .build();
            }
        } catch (Exception e) {
            return HealthCheckResponse
                .named("Database connection check")
                .down()
                .withData("error", e.getMessage())
                .build();
        }
    }
}
```

### External Dependency Health Check

```java
@Readiness
@ApplicationScoped
public class ExternalServiceHealthCheck implements HealthCheck {

    private static final String EXTERNAL_SERVICE_URL = "https://api.example.com/health";

    @Override
    public HealthCheckResponse call() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(EXTERNAL_SERVICE_URL))
                .timeout(Duration.ofSeconds(5))
                .build();

            HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 200) {
                return HealthCheckResponse
                    .named("External service check")
                    .up()
                    .withData("service", "External API")
                    .withData("status_code", response.statusCode())
                    .build();
            } else {
                return HealthCheckResponse
                    .named("External service check")
                    .down()
                    .withData("service", "External API")
                    .withData("status_code", response.statusCode())
                    .build();
            }
        } catch (Exception e) {
            return HealthCheckResponse
                .named("External service check")
                .down()
                .withData("error", e.getMessage())
                .build();
        }
    }
}
```

### Cache Health Check

```java
@Liveness
@ApplicationScoped
public class CacheHealthCheck implements HealthCheck {

    @Inject
    KeycloakSession session;

    @Override
    public HealthCheckResponse call() {
        try {
            // Check if cache is accessible
            InfinispanConnectionProvider infinispanProvider =
                session.getProvider(InfinispanConnectionProvider.class);

            Cache<String, Object> cache = infinispanProvider.getCache("sessions");

            if (cache != null && cache.getStatus() == ComponentStatus.RUNNING) {
                return HealthCheckResponse
                    .named("Cache health check")
                    .up()
                    .withData("cache_status", "running")
                    .withData("cache_size", cache.size())
                    .build();
            } else {
                return HealthCheckResponse
                    .named("Cache health check")
                    .down()
                    .withData("cache_status", "not_running")
                    .build();
            }
        } catch (Exception e) {
            return HealthCheckResponse
                .named("Cache health check")
                .down()
                .withData("error", e.getMessage())
                .build();
        }
    }
}
```

### Memory Health Check

```java
@Liveness
@ApplicationScoped
public class MemoryHealthCheck implements HealthCheck {

    private static final double MEMORY_THRESHOLD = 0.9; // 90%

    @Override
    public HealthCheckResponse call() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        double usageRatio = (double) usedMemory / maxMemory;

        if (usageRatio < MEMORY_THRESHOLD) {
            return HealthCheckResponse
                .named("Memory health check")
                .up()
                .withData("used_memory_mb", usedMemory / (1024 * 1024))
                .withData("max_memory_mb", maxMemory / (1024 * 1024))
                .withData("usage_percent", String.format("%.2f", usageRatio * 100))
                .build();
        } else {
            return HealthCheckResponse
                .named("Memory health check")
                .down()
                .withData("used_memory_mb", usedMemory / (1024 * 1024))
                .withData("max_memory_mb", maxMemory / (1024 * 1024))
                .withData("usage_percent", String.format("%.2f", usageRatio * 100))
                .withData("reason", "Memory usage exceeds threshold")
                .build();
        }
    }
}
```

## Docker Health Check

### Dockerfile Health Check

**File**: `container/src/main/resources/Dockerfile`

```dockerfile
FROM quay.io/keycloak/keycloak:26.4.6

# ... existing configuration ...

# Health check configuration
HEALTHCHECK --interval=30s \
            --timeout=10s \
            --start-period=60s \
            --retries=3 \
            CMD curl -f http://localhost:8080/health/live || exit 1

ENTRYPOINT ["/opt/keycloak/bin/kc-with-setup.sh"]
CMD ["start", "--optimized"]
```

### Docker Compose Health Check

```yaml
version: '3.8'

services:
  keycloak:
    image: keycloak-custom:latest
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health/ready"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
    depends_on:
      postgres:
        condition: service_healthy

  postgres:
    image: postgres:16-alpine
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U keycloak"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 10s
```

## Health Check Testing

### Integration Test for Health Endpoints

```java
@Tag("integration")
@Testcontainers
class HealthCheckIntegrationTest {

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
    @DisplayName("Health endpoint should return UP status")
    void testHealthEndpoint() {
        given()
            .baseUri(sut.getBaseUrl())
        .when()
            .get("/health")
        .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("checks", not(empty()));
    }

    @Test
    @DisplayName("Liveness probe should return UP when container is running")
    void testLivenessProbe() {
        given()
            .baseUri(sut.getBaseUrl())
        .when()
            .get("/health/live")
        .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test
    @DisplayName("Readiness probe should return UP when all dependencies are ready")
    void testReadinessProbe() {
        given()
            .baseUri(sut.getBaseUrl())
        .when()
            .get("/health/ready")
        .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("checks.find { it.name == 'Database connections health check' }.status",
                equalTo("UP"));
    }

    @Test
    @DisplayName("Startup probe should return UP after initialization")
    void testStartupProbe() {
        given()
            .baseUri(sut.getBaseUrl())
        .when()
            .get("/health/started")
        .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test
    @DisplayName("Health check should include database connection status")
    void testDatabaseHealthCheck() {
        given()
            .baseUri(sut.getBaseUrl())
        .when()
            .get("/health/ready")
        .then()
            .statusCode(200)
            .body("checks.find { it.name == 'Database connections health check' }.status",
                equalTo("UP"))
            .body("checks.find { it.name == 'Database connections health check' }.data.connection",
                equalTo("established"));
    }
}
```

### Simulating Health Check Failures

```java
@Test
@DisplayName("Readiness probe should fail when database is unavailable")
void testReadinessFailureOnDatabaseDown() {
    // Arrange - Stop database container
    sut.postgres.stop();

    // Wait for health check to detect failure
    await().atMost(60, TimeUnit.SECONDS)
        .pollInterval(2, TimeUnit.SECONDS)
        .until(() -> {
            Response response = given()
                .baseUri(sut.getBaseUrl())
                .get("/health/ready");
            return response.statusCode() == 503;
        });

    // Assert
    given()
        .baseUri(sut.getBaseUrl())
    .when()
        .get("/health/ready")
    .then()
        .statusCode(503)
        .body("status", equalTo("DOWN"))
        .body("checks.find { it.name == 'Database connections health check' }.status",
            equalTo("DOWN"));

    // Cleanup - Restart database
    sut.postgres.start();
}
```

## Monitoring Health Check Metrics

### Prometheus Metrics for Health Checks

```promql
# Health check success rate
sum(rate(health_check_total{status="up"}[5m])) /
sum(rate(health_check_total[5m]))

# Health check duration
histogram_quantile(0.95, rate(health_check_duration_seconds_bucket[5m]))

# Failed health checks
health_check_total{status="down"}
```

### Grafana Dashboard Panels

**Health Check Status Panel**:
```json
{
  "title": "Health Check Status",
  "targets": [
    {
      "expr": "health_check_status{job='keycloak'}",
      "legendFormat": "{{check_name}}"
    }
  ],
  "type": "stat",
  "options": {
    "colorMode": "background",
    "graphMode": "none",
    "reduceOptions": {
      "calcs": ["lastNotNull"]
    }
  }
}
```

## Best Practices

1. **Separate Liveness and Readiness**: Use different endpoints for different purposes
2. **Fast Health Checks**: Health checks should complete in < 1 second
3. **Appropriate Timeouts**: Set realistic timeouts for dependencies
4. **Graceful Degradation**: Return partial health when non-critical components fail
5. **Avoid Side Effects**: Health checks should be read-only operations
6. **Include Dependency Checks**: Check all critical dependencies (DB, cache, external APIs)
7. **Log Health Check Failures**: Help debugging by logging check failures
8. **Test Health Checks**: Include health check validation in integration tests
9. **Monitor Health Metrics**: Track health check performance and failures
10. **Document Check Behavior**: Clearly document what each check validates

## Troubleshooting Health Checks

### Common Issues

#### 1. Health Check Timeout
**Symptom**: Health checks occasionally timeout
**Solution**:
- Increase probe timeout settings
- Optimize health check queries
- Add connection pooling for health checks

#### 2. Flapping Health Status
**Symptom**: Health status rapidly changes between UP and DOWN
**Solution**:
- Increase `successThreshold` in readiness probe
- Increase probe intervals
- Add circuit breaker pattern to health checks

#### 3. Slow Startup Detection
**Symptom**: Containers restart during normal startup
**Solution**:
- Increase `failureThreshold` in startup probe
- Increase `initialDelaySeconds` in liveness probe
- Use startup probe to delay liveness checks

#### 4. False Negatives
**Symptom**: Health check reports DOWN but service is working
**Solution**:
- Review health check logic
- Check for transient failures
- Add retry logic to health checks

## Health Check Checklist

- [ ] Liveness probe configured with appropriate thresholds
- [ ] Readiness probe checks all critical dependencies
- [ ] Startup probe allows sufficient time for initialization
- [ ] Custom health checks implemented for critical components
- [ ] Health checks complete in < 1 second
- [ ] Health check failures are logged
- [ ] Health check metrics are monitored
- [ ] Integration tests validate health endpoints
- [ ] Documentation includes health check behavior
- [ ] Alerts configured for persistent health check failures

## Next Steps

1. Implement custom health checks for all extensions
2. Add health check integration tests
3. Configure health check alerts in Prometheus
4. Create health check dashboard in Grafana
5. Document health check troubleshooting procedures
6. Test health check behavior under failure scenarios
7. Optimize health check performance
