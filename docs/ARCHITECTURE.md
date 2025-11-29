# Keycloak Custom - Architecture Guide

## Overview

Keycloak Custom is a multi-module Maven monorepo that extends Keycloak 26.4.6 with custom SPI implementations, themes, and automated deployment configurations. It supports multiple deployment targets: local development (Docker Compose), containerized (Docker), and cloud-native (Kubernetes/Helm).

**Project Maturity**: Production-grade foundation with custom extensions requiring security hardening before production deployment.

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                  Keycloak Custom (26.4.6)                   │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │           Core Layer (Keycloak 26.4.6)               │   │
│  │  - Quarkus Runtime                                   │   │
│  │  - Authentication & Authorization                    │   │
│  │  - OIDC/OAuth2/SAML Support                         │   │
│  └──────────────────────────────────────────────────────┘   │
│                         ▲                                     │
│  ┌──────────────────────┴──────────────────────────────┐   │
│  │         Custom Layers (Built on SPI)                │   │
│  ├──────────────────────────────────────────────────────┤   │
│  │  • No-Op Authenticator (authentication flow)        │   │
│  │  • No-Op Protocol Mapper (OIDC claims)             │   │
│  │  • Custom Themes (Inventage, Inventage.v2)         │   │
│  │  • Theme Provider (resource fallback)              │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │      Configuration & Deployment Layer               │   │
│  ├──────────────────────────────────────────────────────┤   │
│  │  • Build Configuration (Maven)                       │   │
│  │  • Realm Configuration (JSON, CLI)                   │   │
│  │  • Container Orchestration (Docker)                  │   │
│  │  • Kubernetes Deployment (Helm)                      │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## Module Structure

### Build Layer (Maven Modules)

```
keycloak-custom/
├── pom.xml (root - aggregator)
├── server/                          ← Keycloak 26.4.6 base
│   └── pom.xml (downloads/runs base)
│
├── config/                          ← Configuration & setup
│   ├── src/main/resources/
│   │   ├── keycloak-config-cli/     ← Realm & user definitions
│   │   └── keycloak.conf            ← Runtime configuration
│   └── pom.xml
│
├── extensions/                      ← Custom SPI implementations
│   ├── extension-no-op-authenticator/
│   │   ├── pom.xml
│   │   └── src/main/java/
│   │       ├── NoOperationAuthenticator
│   │       ├── NoOperationFormAuthenticator
│   │       ├── NoOperationAuthenticatorFactory
│   │       └── InvitationClasspathThemeProvider
│   │
│   └── extension-no-op-protocol-mapper/
│       ├── pom.xml
│       └── src/main/java/
│           └── NoOperationProtocolMapper
│
├── themes/                          ← Custom UI themes
│   ├── inventage/
│   │   ├── login/
│   │   ├── account/
│   │   └── pom.xml
│   │
│   └── inventage.v2/
│       ├── login/
│       ├── account/
│       └── pom.xml
│
├── container/                       ← Docker image builder
│   ├── Dockerfile (multi-stage)
│   └── pom.xml (fabric8 plugin)
│
├── docker-compose/                  ← Local testing environment
│   ├── src/main/resources/
│   │   ├── keycloak-custom.yml      ← Keycloak service
│   │   ├── postgres/                ← Database config
│   │   └── mailpit/                 ← Email testing
│   └── pom.xml
│
└── helm/                            ← Kubernetes deployment
    ├── src/main/resources/          ← Helm chart template
    │   └── values.yaml              ← Configuration values
    └── pom.xml (kokuwa plugin)
```

---

## Deployment Architecture

### 1. Docker Compose (Development)

**Environment**: Local development with full Keycloak stack

**Services**:
- **Keycloak** (Port 8080/8443)
  - Multi-architecture build (arm64/amd64)
  - Custom extensions loaded via JAR
  - Custom themes mounted as classpath resources
  - PostgreSQL persistence

- **PostgreSQL** (Port 5432)
  - Automatic database initialization
  - Volume-based persistence
  - Configured for Keycloak

- **Mailpit** (Port 1025/8025)
  - Email testing (SMTP port 1025)
  - Web UI for email inspection (port 8025)
  - For email flow testing

**Configuration Flow**:
```
1. Container startup (Dockerfile CMD)
2. kc-with-setup.sh executes
3. keycloak-config-cli runs realm import
4. Keycloak starts with loaded configuration
5. Health checks validate readiness
```

### 2. Kubernetes/Helm (Production)

**Chart**: `keycloak-custom` (OCI registry)

**Key Components**:
- **Deployment**: Keycloak replicas (default 1, scalable)
- **ConfigMap**: Non-sensitive configuration (realm exports)
- **Secret**: Sensitive data (passwords, API keys)
- **Service**: Internal/external access (ClusterIP/LoadBalancer)
- **Ingress**: Optional external routing

**Dependency**: Requires external PostgreSQL database

**Configuration Sources** (in order):
1. `values.yaml` (default configuration)
2. Helm overrides (`-f custom-values.yaml`)
3. Environment variables (runtime overrides)

### 3. CI/CD Pipeline (GitHub Actions)

**Trigger**: Push to main or manual dispatch

**Build Steps**:
1. Maven build + JUnit tests
2. Multi-arch Docker image build (arm64/amd64)
3. Push to GHCR (ghcr.io/inventage/keycloak-custom)
4. Helm chart generation and packaging
5. Artifact publication (OCI registry)

**Artifacts**:
- Docker image: `ghcr.io/inventage/keycloak-custom:VERSION`
- Helm chart: `oci://ghcr.io/inventage/keycloak-custom/helm:VERSION`

---

## Technology Stack

### Runtime
| Component | Version | Purpose |
|-----------|---------|---------|
| Keycloak | 26.4.6 | Identity & Access Management |
| Quarkus | Latest | Lightweight Java runtime |
| Java | 21 (Temurin) | Language runtime |
| PostgreSQL | 15+ | Persistent datastore |

### Build & Deployment
| Tool | Version | Purpose |
|------|---------|---------|
| Maven | 3.6.0+ | Build orchestration |
| Docker | Latest | Container runtime |
| Helm | 3.19.2 | K8s package manager |
| fabric8 | 0.45.0 | Docker Maven plugin |
| kokuwa | 6.13.0 | Helm Maven plugin |

### Testing & Observability
| Tool | Version | Purpose |
|------|---------|---------|
| JUnit Jupiter | 5.12.1 | Unit testing framework |
| Testcontainers | 3.7.0 | Container-based integration tests |
| Prometheus | N/A | Metrics collection (enabled) |
| OpenTelemetry | N/A | Distributed tracing (optional) |

---

## Data Flow

### Authentication Flow (No-Op Authenticator)
```
User Request
    ↓
Keycloak Auth Endpoint
    ↓
Auth Flow Executor
    ↓
[NoOperationAuthenticator]
    │ - Validates credential type
    │ - Passes through (no-op)
    │ - Sets AuthenticationFlowContext
    ↓
User Authenticated
    ↓
Token Generation
    ↓
Protocol Mapper Applied
    │ [NoOperationProtocolMapper]
    │ - Injects claims into token
    │ - Returns modified token
    ↓
Token Response
```

### Protocol Mapping (OAuth2/OIDC)
```
Authenticated User
    ↓
Token Generation
    ↓
OIDC Token Mapper Pipeline
    ↓
[NoOperationProtocolMapper]
    │ - Reads protocol (OIDC)
    │ - Reads protocol mapper config
    │ - Injects custom claims
    │ - Returns modified token
    ↓
Signed Token
    ↓
Client Application
```

---

## Security Architecture

### Authentication & Authorization
- **Keycloak SPI-based**: All auth flows use Service Provider Interface
- **Protocol Support**: OIDC, OAuth2, SAML
- **Custom Authenticators**: No-Op pattern for simple demonstrations
- **Session Management**: Keycloak-managed sessions with configurable timeout

### Data Protection
- **Transport**: HTTPS (production required)
- **At-Rest**: PostgreSQL encryption (operator-managed)
- **Secrets**: Kubernetes Secrets for sensitive configuration
- **Logging**: Structured logging with optional redaction

### Current Security Gaps (Pre-Production Fixes Required)
1. **Default Credentials**: admin/admin hardcoded (change immediately)
2. **HTTP Enabled**: Development allows HTTP (disable for production)
3. **Self-Signed TLS**: Certificate validation disabled
4. **No Rate Limiting**: Brute-force attacks not prevented
5. **No Input Validation**: Authenticators lack validation

See [SECURITY.md](./SECURITY.md) for detailed hardening recommendations.

---

## Scalability Considerations

### Horizontal Scaling
- **Keycloak Pods**: Scale via `replicas` in Helm values
- **Shared Database**: PostgreSQL must be externally hosted
- **Session Replication**: Configured via Keycloak config
- **Load Balancing**: Kubernetes Service with selector

### Performance Optimization
- **Container**: Multi-stage Dockerfile minimizes image size
- **Caching**: Keycloak default realm/client caching enabled
- **Database**: Index optimization on user lookup tables
- **Metrics**: Prometheus endpoint for performance monitoring

### Resource Management
**Recommended Settings** (Helm values):
```yaml
resources:
  requests:
    cpu: 250m
    memory: 512Mi
  limits:
    cpu: 1000m
    memory: 1Gi
```

---

## Monitoring & Observability

### Metrics
- **Endpoint**: `/metrics` (Prometheus format)
- **Metrics Available**: JVM, HTTP, Keycloak-specific
- **Integration**: Deploy Prometheus + Grafana separately

### Health Checks
- **Liveness**: `/health/live` - process still running
- **Readiness**: `/health/ready` - ready to accept traffic
- **Startup**: `/health/startup` - initialization complete

### Logging
- **Level**: Configurable via `keycloak.conf`
- **Format**: Structured (JSON via logging addon)
- **Aggregation**: Pipe to EFK/Loki for centralized logging

---

## Configuration Management

### Build-Time Configuration
- **File**: `config/src/main/resources/keycloak.conf`
- **Source**: Packaged in container image
- **Override**: Environment variables at runtime
- **Scope**: Database, TLS, cache, themes

### Runtime Configuration
- **Realm Import**: `keycloak-config-cli` JSON files
- **Users**: Created via Admin API
- **Clients**: OIDC/OAuth2/SAML client registrations
- **Mappers**: Custom protocol mappers

### Environment Variables
| Variable | Purpose | Default |
|----------|---------|---------|
| `KC_DB_URL` | PostgreSQL connection | `jdbc:postgresql://postgres:5432/keycloak` |
| `KC_DB_USERNAME` | Database user | `keycloak` |
| `KC_DB_PASSWORD` | Database password | `keycloak` |
| `KC_HOSTNAME` | External hostname | `localhost` |
| `KC_HTTP_ENABLED` | Allow HTTP | `false` (production) |
| `KC_LOG_LEVEL` | Log level | `INFO` |

---

## Development Workflow

### Local Development Setup
```bash
# Clone repository
git clone <repo>
cd keycloak-custom

# Build with tests
mvn clean install

# Start Docker Compose environment
docker-compose -f docker-compose/src/main/resources/docker-compose.yml up -d

# Access Keycloak
# Admin Console: http://localhost:8080/admin
# Metrics: http://localhost:8080/metrics
# Health: http://localhost:8080/health
```

### Adding Custom Extensions
1. Create new module under `extensions/`
2. Implement SPI interface (e.g., `Authenticator`, `ProtocolMapper`)
3. Add factory class with `@AutoService` annotation
4. Build: `mvn clean install`
5. Extensions auto-loaded on container startup

---

## Maintenance & Upgrades

### Keycloak Version Upgrade
1. Update version in `server/pom.xml`
2. Update base image in `container/Dockerfile`
3. Test extensions compatibility
4. Rebuild and test Docker image
5. Update Helm chart version
6. Deploy to staging, then production

### Extension Updates
1. Modify extension source code
2. Run tests: `mvn clean test`
3. Build: `mvn clean install`
4. Extensions auto-repackaged in container
5. Deploy new container image

### Database Migrations
- Keycloak auto-migrates on startup
- Test migrations in staging environment first
- Backup database before production upgrades

---

## Troubleshooting

### Container Won't Start
- Check logs: `docker logs <container>`
- Verify PostgreSQL connectivity
- Confirm database exists and is initialized
- Check Keycloak configuration syntax

### Authentication Failing
- Check Keycloak logs for auth flow errors
- Verify realm and client configuration
- Confirm authenticator is deployed and enabled
- Check protocol mapper configuration

### Kubernetes Deployment Issues
- Verify Helm values are correct
- Check cluster resources available
- Confirm PostgreSQL external database reachable
- Review pod events: `kubectl describe pod <pod>`

---

## References

- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Keycloak SPI Guide](https://www.keycloak.org/docs/latest/server_development/)
- [Helm Chart Repository](./helm/README.md)
- [Docker Compose Setup](./docker-compose/README.md)
- [Security Hardening](./SECURITY.md)
- [Deployment Guide](./DEPLOYMENT.md)
- [Testing Strategy](./docs/TESTING.md)
