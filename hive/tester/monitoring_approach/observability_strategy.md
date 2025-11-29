# Observability Strategy - Keycloak Custom

## Overview
Comprehensive observability strategy for Keycloak Custom covering metrics, logging, tracing, and health monitoring.

## Observability Pillars

### 1. Metrics (What is happening?)
- Application performance metrics
- Business metrics (logins, token issuance)
- System metrics (CPU, memory, network)
- Custom extension metrics

### 2. Logging (What happened?)
- Structured application logs
- Audit logs for compliance
- Security event logs
- Debug logs for troubleshooting

### 3. Tracing (Where is time spent?)
- Distributed request tracing
- Authentication flow tracing
- Database query tracing
- External integration tracing

### 4. Health Checks (Is it working?)
- Liveness probes
- Readiness probes
- Dependency health checks

## Metrics Strategy

### Built-in Keycloak Metrics

**Configuration**: `config/src/main/resources/keycloak/conf/keycloak.conf`

```properties
# Enable Prometheus metrics endpoint
metrics-enabled=true

# Metrics available at: http://keycloak:8080/metrics
```

### Metrics Endpoint

```bash
# Access metrics
curl http://localhost:8080/metrics

# Example metrics output:
# HELP vendor_cpu_availableProcessors Displays the number of processors available
# TYPE vendor_cpu_availableProcessors gauge
vendor_cpu_availableProcessors 8.0

# HELP base_gc_time_total Displays the approximate accumulated time elapsed
# TYPE base_gc_time_total gauge
base_gc_time_total_seconds{name="G1 Young Generation"} 0.123

# Keycloak-specific metrics
keycloak_logins_total{realm="example1",client_id="test-client"} 1523
keycloak_login_failures_total{realm="example1",error="invalid_credentials"} 42
```

### Key Metrics to Monitor

#### Application Metrics
- `keycloak_logins_total` - Total successful logins by realm/client
- `keycloak_login_failures_total` - Failed login attempts
- `keycloak_registrations_total` - User registrations
- `keycloak_code_to_token_total` - Authorization code exchanges
- `keycloak_refresh_token_total` - Token refreshes
- `keycloak_client_credentials_total` - Client credential grants

#### Performance Metrics
- `base_jvm_uptime_seconds` - Application uptime
- `base_gc_time_total_seconds` - Garbage collection time
- `vendor_memory_usedHeap_bytes` - Heap memory usage
- `vendor_memory_committedHeap_bytes` - Committed heap memory
- `vendor_cpu_availableProcessors` - Available CPU cores
- `vendor_cpu_processCpuLoad_percent` - CPU load percentage

#### Database Metrics
- `db_connection_pool_size` - Database connection pool size
- `db_connection_pool_active` - Active database connections
- `db_query_duration_seconds` - Database query duration

### Custom Extension Metrics

**Example**: Adding custom metrics to authenticator

```java
package com.inventage.keycloak.noopauthenticator.infrastructure.authenticator;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.keycloak.models.KeycloakContext;

public class NoOperationAuthenticator implements Authenticator {

    private static final String METRIC_PREFIX = "keycloak.custom.noop_authenticator";

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        MeterRegistry registry = getMeterRegistry(context);

        // Counter for authentication attempts
        Counter.builder(METRIC_PREFIX + ".attempts")
            .tag("realm", context.getRealm().getName())
            .tag("status", "success")
            .description("NoOp authenticator invocations")
            .register(registry)
            .increment();

        // Timer for authentication duration
        Timer.builder(METRIC_PREFIX + ".duration")
            .tag("realm", context.getRealm().getName())
            .description("NoOp authenticator execution time")
            .register(registry)
            .record(() -> {
                // Authentication logic
                context.success();
            });
    }

    private MeterRegistry getMeterRegistry(AuthenticationFlowContext context) {
        KeycloakContext kc = context.getSession().getContext();
        return kc.getContextObject(MeterRegistry.class);
    }
}
```

## Logging Strategy

### Log Levels Configuration

**Current Configuration**: `container/src/test/java/sut/SystemUnderTest.java`

```java
envs.put("KC_LOG_LEVEL", "info");
```

**Production Configuration**:
```bash
# Environment variables
KC_LOG_LEVEL=info                    # Root log level: off, fatal, error, warn, info, debug, trace, all
KC_LOG_CONSOLE_OUTPUT=json           # Log format: default or json
KC_LOG_CONSOLE_COLOR=false           # Disable color in production

# Category-specific logging
KC_LOG_LEVEL_ORG_KEYCLOAK_EVENTS=debug              # Event logging
KC_LOG_LEVEL_ORG_KEYCLOAK_TRANSACTION=warn          # Transaction logging
KC_LOG_LEVEL_ORG_KEYCLOAK_SERVICES=info            # Service logging
```

### Structured JSON Logging

**Recommended Format**:
```json
{
  "@timestamp": "2025-11-29T04:52:11.413Z",
  "level": "INFO",
  "logger": "org.keycloak.events",
  "message": "User login successful",
  "realm": "example1",
  "userId": "12345",
  "clientId": "test-client",
  "ipAddress": "192.168.1.100",
  "sessionId": "session-abc-123",
  "type": "LOGIN",
  "trace_id": "550e8400-e29b-41d4-a716-446655440000",
  "span_id": "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
}
```

### Event Logging Configuration

**Current Configuration**: `helm/src/main/resources/values.yaml`

```yaml
command:
  - "--spi-events-listener-jboss-logging-success-level=info"
  - "--spi-events-listener-jboss-logging-error-level=warn"
```

**Event Categories to Log**:
- **Authentication Events**: LOGIN, LOGOUT, CODE_TO_TOKEN, REFRESH_TOKEN
- **Authorization Events**: PERMISSION_TOKEN, AUTHZ_PERMISSION_GRANTED
- **Account Events**: REGISTER, UPDATE_PROFILE, UPDATE_PASSWORD
- **Admin Events**: CREATE_REALM, UPDATE_CLIENT, DELETE_USER
- **Error Events**: LOGIN_ERROR, INVALID_TOKEN, PERMISSION_DENIED

### Custom Event Listener for Advanced Logging

```java
package com.inventage.keycloak.logging;

import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.jboss.logging.Logger;

public class StructuredEventListener implements EventListenerProvider {

    private static final Logger logger = Logger.getLogger(StructuredEventListener.class);

    @Override
    public void onEvent(Event event) {
        // Structure event data for logging
        Map<String, Object> logData = new HashMap<>();
        logData.put("type", "user_event");
        logData.put("event_type", event.getType().name());
        logData.put("realm", event.getRealmId());
        logData.put("user_id", event.getUserId());
        logData.put("client_id", event.getClientId());
        logData.put("ip_address", event.getIpAddress());
        logData.put("timestamp", event.getTime());
        logData.put("session_id", event.getSessionId());

        if (event.getError() != null) {
            logData.put("error", event.getError());
            logger.warnv("User event failed: {0}", toJson(logData));
        } else {
            logger.infov("User event: {0}", toJson(logData));
        }
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("type", "admin_event");
        logData.put("operation", adminEvent.getOperationType().name());
        logData.put("resource_type", adminEvent.getResourceType().name());
        logData.put("resource_path", adminEvent.getResourcePath());
        logData.put("realm", adminEvent.getRealmId());
        logData.put("auth_realm", adminEvent.getAuthDetails().getRealmId());
        logData.put("auth_client", adminEvent.getAuthDetails().getClientId());
        logData.put("auth_user", adminEvent.getAuthDetails().getUserId());
        logData.put("timestamp", adminEvent.getTime());

        if (adminEvent.getError() != null) {
            logData.put("error", adminEvent.getError());
            logger.warnv("Admin event failed: {0}", toJson(logData));
        } else {
            logger.infov("Admin event: {0}", toJson(logData));
        }
    }
}
```

### Log Aggregation

**Recommended Stack**:
- **Fluentd/Fluent Bit**: Log collection and forwarding
- **Elasticsearch**: Log storage and indexing
- **Kibana**: Log visualization and analysis
- **Grafana Loki**: Alternative lightweight log aggregation

**Fluentd Configuration Example**:
```xml
<source>
  @type tail
  path /opt/keycloak/data/log/*.log
  pos_file /var/log/td-agent/keycloak.pos
  tag keycloak
  <parse>
    @type json
    time_key @timestamp
    time_format %Y-%m-%dT%H:%M:%S.%L%z
  </parse>
</source>

<match keycloak>
  @type elasticsearch
  host elasticsearch
  port 9200
  index_name keycloak-logs
  type_name _doc
  logstash_format true
  logstash_prefix keycloak
  include_tag_key true
  tag_key @log_name
</match>
```

## Distributed Tracing

### OpenTelemetry Integration

**Add Dependencies**:
```xml
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-quarkus</artifactId>
    <version>2.2.0</version>
</dependency>
```

**Configuration**:
```properties
# Environment variables for OpenTelemetry
OTEL_SERVICE_NAME=keycloak-custom
OTEL_TRACES_EXPORTER=jaeger
OTEL_EXPORTER_JAEGER_ENDPOINT=http://jaeger:14250
OTEL_METRICS_EXPORTER=prometheus
OTEL_LOGS_EXPORTER=otlp
```

### Trace Context Propagation

**W3C Trace Context Headers**:
```
traceparent: 00-550e8400e29b41d4a716446655440000-6ba7b81090dad11d-01
tracestate: vendor1=value1,vendor2=value2
```

### Custom Span Creation

```java
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

public class TracedAuthenticator implements Authenticator {

    private final Tracer tracer;

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        Span span = tracer.spanBuilder("authenticate")
            .setAttribute("realm", context.getRealm().getName())
            .setAttribute("client_id", context.getAuthenticationSession().getClient().getClientId())
            .setAttribute("authenticator", "noop-authenticator")
            .startSpan();

        try (Scope scope = span.makeCurrent()) {
            // Authentication logic
            context.success();
            span.setStatus(StatusCode.OK);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, "Authentication failed");
            throw e;
        } finally {
            span.end();
        }
    }
}
```

### Recommended Tracing Backends
- **Jaeger**: Comprehensive distributed tracing
- **Zipkin**: Lightweight tracing solution
- **Grafana Tempo**: Scalable tracing backend
- **AWS X-Ray**: Cloud-native tracing
- **Google Cloud Trace**: GCP tracing solution

## Dashboard and Visualization

### Prometheus + Grafana Setup

**Prometheus Configuration**:
```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'keycloak'
    static_configs:
      - targets: ['keycloak:8080']
    metrics_path: '/metrics'
    scrape_interval: 30s
```

### Grafana Dashboards

**Key Dashboards**:

1. **Keycloak Overview Dashboard**
   - Active sessions
   - Login rate
   - Error rate
   - Response time percentiles (p50, p95, p99)
   - CPU and memory usage

2. **Authentication Flow Dashboard**
   - Login attempts by realm
   - Success/failure ratio
   - Authentication duration
   - Failed login reasons

3. **Performance Dashboard**
   - Request rate
   - Response time
   - Database query performance
   - JVM metrics (heap, GC, threads)

4. **Security Dashboard**
   - Failed login attempts
   - Account lockouts
   - Suspicious activity patterns
   - Geographic login distribution

### Example Grafana Queries

**Login Rate**:
```promql
rate(keycloak_logins_total[5m])
```

**Error Rate**:
```promql
rate(keycloak_login_failures_total[5m]) / rate(keycloak_logins_total[5m])
```

**95th Percentile Response Time**:
```promql
histogram_quantile(0.95, rate(keycloak_http_request_duration_seconds_bucket[5m]))
```

**Active Sessions by Realm**:
```promql
keycloak_user_sessions_total{realm="example1"}
```

## Alerting Strategy

### Alert Rules

**Prometheus Alert Rules**:
```yaml
groups:
  - name: keycloak_alerts
    interval: 30s
    rules:
      - alert: HighLoginFailureRate
        expr: rate(keycloak_login_failures_total[5m]) > 10
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High login failure rate detected"
          description: "Login failure rate is {{ $value }} per second"

      - alert: KeycloakDown
        expr: up{job="keycloak"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Keycloak instance is down"
          description: "Keycloak has been down for more than 1 minute"

      - alert: HighMemoryUsage
        expr: vendor_memory_usedHeap_bytes / vendor_memory_maxHeap_bytes > 0.9
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Keycloak memory usage is high"
          description: "Memory usage is at {{ $value | humanizePercentage }}"

      - alert: DatabaseConnectionPoolExhausted
        expr: db_connection_pool_active / db_connection_pool_size > 0.95
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Database connection pool nearly exhausted"
          description: "{{ $value | humanizePercentage }} of connections in use"
```

### Alert Channels
- **PagerDuty**: Critical production alerts
- **Slack**: Team notifications
- **Email**: Non-urgent notifications
- **Webhook**: Custom integrations

## Audit Logging

### Compliance Requirements

**Events to Audit**:
- All authentication attempts (success/failure)
- User account modifications
- Permission changes
- Client configuration changes
- Realm configuration changes
- Admin operations

### Audit Log Format

```json
{
  "timestamp": "2025-11-29T04:52:11.413Z",
  "event_type": "ADMIN_UPDATE_USER",
  "actor": {
    "user_id": "admin-user-123",
    "ip_address": "192.168.1.50",
    "user_agent": "Mozilla/5.0..."
  },
  "subject": {
    "user_id": "user-456",
    "username": "john.doe"
  },
  "changes": {
    "email": {
      "old": "old@example.com",
      "new": "new@example.com"
    },
    "enabled": {
      "old": false,
      "new": true
    }
  },
  "realm": "example1",
  "result": "SUCCESS",
  "audit_id": "audit-789"
}
```

### Audit Log Retention

**Recommendations**:
- **Production**: 90 days hot storage, 7 years cold storage
- **Staging**: 30 days
- **Development**: 7 days

## Best Practices

1. **Use Structured Logging**: Always log in JSON format for production
2. **Include Correlation IDs**: Track requests across services
3. **Set Appropriate Log Levels**: Avoid debug logs in production
4. **Monitor Business Metrics**: Not just technical metrics
5. **Set Up Alerts**: Define thresholds for critical metrics
6. **Regular Dashboard Reviews**: Keep dashboards up-to-date
7. **Test Observability**: Include observability in testing
8. **Document Metrics**: Maintain metric catalog
9. **Secure Logs**: Sensitive data should not be logged
10. **Regular Log Review**: Audit logs for security events

## Integration with Container Orchestration

### Kubernetes Monitoring

**ServiceMonitor for Prometheus Operator**:
```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: keycloak
  namespace: keycloak
spec:
  selector:
    matchLabels:
      app: keycloak
  endpoints:
    - port: http
      path: /metrics
      interval: 30s
```

**Logging Sidecar**:
```yaml
spec:
  containers:
    - name: fluent-bit
      image: fluent/fluent-bit:2.0
      volumeMounts:
        - name: keycloak-logs
          mountPath: /opt/keycloak/data/log
```

## Next Steps

1. Implement custom metrics in all extensions
2. Set up Prometheus and Grafana
3. Create comprehensive Grafana dashboards
4. Implement OpenTelemetry tracing
5. Configure centralized log aggregation
6. Set up alerting rules
7. Implement audit log retention policy
8. Create runbooks for common alerts
