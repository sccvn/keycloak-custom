# Helm Configuration Analysis - Keycloak Custom

## Executive Summary

The Keycloak Custom project uses Helm charts for Kubernetes deployment orchestration. The Helm configuration is built using Maven resource filtering and depends on the upstream Codecentric Keycloak Helm chart (keycloakx v7.1.4).

## Chart Structure

### Chart.yaml Analysis

**Location:** `/helm/src/main/resources/Chart.yaml`

```yaml
name: keycloak-custom-chart
version: ${project.version}  # Maven-filtered version
apiVersion: v2

dependencies:
  - name: keycloakx
    version: 7.1.4
    repository: "https://codecentric.github.io/helm-charts"
```

**Key Findings:**
- **Chart Name:** `keycloak-custom-chart`
- **API Version:** Helm v3 (apiVersion: v2)
- **Versioning Strategy:** Dynamic version from Maven POM (${project.version})
- **Upstream Dependency:** Codecentric's keycloakx chart v7.1.4
- **Build Pattern:** Chart is generated during Maven build process

### Values.yaml Analysis

**Location:** `/helm/src/main/resources/values.yaml`

#### 1. Startup Command Configuration

```yaml
command:
  - "/opt/keycloak/bin/kc-with-setup.sh"
  - "--verbose"
  - "start"
  - "--http-enabled=true"
  - "--http-port=8080"
  - "--hostname-strict=false"
  - "--spi-events-listener-jboss-logging-success-level=info"
  - "--spi-events-listener-jboss-logging-error-level=warn"
```

**Purpose:**
- Uses custom startup script `kc-with-setup.sh` instead of standard Keycloak launcher
- Enables HTTP on port 8080 for development/internal use
- Disables strict hostname validation (not suitable for production)
- Configures event logging at info/warn levels

#### 2. Container Image Configuration

```yaml
image:
  repository: ${docker.registry}${docker.image.name}
  tag: ${project.version}
```

**Maven-Filtered Values:**
- `docker.registry`: `ghcr.io/inventage/keycloak-custom/`
- `docker.image.name`: `com.inventage.keycloak.custom.container`
- Resulting image: `ghcr.io/inventage/keycloak-custom/com.inventage.keycloak.custom.container:${version}`

#### 3. HTTP Configuration

```yaml
http:
  relativePath: "/"
```

**Purpose:** Keycloak serves from root path without context path prefix

#### 4. Database Readiness Check

```yaml
dbchecker:
  enabled: true
```

**Purpose:** Ensures PostgreSQL database is ready before Keycloak starts

#### 5. Database Configuration

```yaml
database:
  vendor: postgres
  hostname: postgresql
  port: 5432
  username: keycloak
  password: keycloak  # WARNING: Default credentials
  database: keycloak
```

**Findings:**
- Database: PostgreSQL
- Default credentials (should be overridden in production)
- Hostname references Kubernetes service name `postgresql`
- Standard PostgreSQL port 5432

#### 6. Environment Variables Configuration

**Java Options:**
```yaml
extraEnv: |
  - name: JAVA_OPTS_APPEND
    value: >-
      -Djava.awt.headless=true
      -Djgroups.dns.query={{ include "keycloak.fullname" . }}-headless
```

**Purpose:**
- Headless Java mode (no GUI)
- JGroups DNS-based discovery using Kubernetes headless service

**Bootstrap Admin Configuration:**
```yaml
  - name: KC_BOOTSTRAP_ADMIN_USERNAME
    value: temp-admin
  - name: KC_BOOTSTRAP_ADMIN_CLIENT_ID
    value: temp-client-admin
```

**Purpose:** Creates temporary admin user and client for initial setup

**Keycloak Config CLI Integration:**
```yaml
  - name: KEYCLOAK_GRANTTYPE
    value: client_credentials
  - name: KEYCLOAK_CLIENTID
    value: temp-client-admin
  - name: KEYCLOAK_CLIENTSECRET
    valueFrom:
      secretKeyRef:
        name: keycloak-custom-secret-vars
        key: KC_BOOTSTRAP_ADMIN_CLIENT_SECRET
```

**Purpose:**
- Configures keycloak-config-cli for automated realm configuration
- Uses OAuth2 client credentials grant type
- Secret stored in Kubernetes Secret resource

#### 7. ConfigMap and Secret References

```yaml
extraEnvFrom: |
  - configMapRef:
      name: keycloak-custom-config-vars
  - secretRef:
      name: keycloak-custom-secret-vars
```

**Purpose:**
- Loads environment variables from ConfigMap for non-sensitive config
- Loads secrets from Secret resource for credentials and keys
- Allows externalized configuration management

#### 8. Security Context

```yaml
securityContext:
  # readOnlyRootFilesystem: true  # Commented due to Keycloak issue #11286
  allowPrivilegeEscalation: false
  capabilities:
    drop:
      - ALL
```

**Security Features:**
- Drops all Linux capabilities
- Prevents privilege escalation
- Read-only root filesystem disabled (Keycloak limitation)

## Helm Test Resources

### Local Development ConfigMap

**Location:** `/helm/src/test/resources/local/keycloak-custom-config-vars.local.yaml`

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: keycloak-custom-config-vars
  namespace: default
data:
  EXAMPLE1_DISPLAY_NAME: "Example One (minikube)"
```

**Purpose:** Example ConfigMap for local Minikube testing

### Local Development Secret

**Location:** `/helm/src/test/resources/local/keycloak-custom-secret-vars.local.yaml`

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: keycloak-custom-secret-vars
  namespace: default
type: Opaque
stringData:
  KC_BOOTSTRAP_ADMIN_PASSWORD: "admin"
  KC_BOOTSTRAP_ADMIN_CLIENT_SECRET: "admin"
```

**Purpose:** Example Secret for local Minikube testing with default credentials

### PostgreSQL Values

**Location:** `/helm/src/test/resources/postgresql/values.minikube.yaml`

```yaml
global:
  postgresql:
    auth:
      username: keycloak
      password: keycloak
      database: keycloak
```

**Purpose:** PostgreSQL Helm chart values for Minikube deployment

## Helm Build and Deployment Process

### Maven Integration

The Helm chart is built and deployed using the `helm-maven-plugin`:

**Configuration Highlights:**
- **Plugin Version:** 6.13.0
- **Helm Version:** 3.19.2
- **Repository:** Codecentric Helm Charts (https://codecentric.github.io/helm-charts)
- **Chart Version:** Dynamically set from Maven ${project.version}
- **Registry:** OCI registry at `ghcr.io/inventage/keycloak-custom`

### Build Lifecycle

1. **init** - Initialize Helm client and download dependencies
2. **lint** - Validate chart syntax and structure
3. **package** - Create .tgz chart archive
4. **registry-login** - Authenticate to GitHub Container Registry
5. **push** - Push chart to OCI registry

### Authentication

```xml
<username>${env.GITHUB_ACTOR}</username>
<password>${env.GITHUB_TOKEN}</password>
```

Uses GitHub Actions environment variables for authentication

## Dependency Management

### Upstream Chart Dependency

- **Chart:** keycloakx from Codecentric
- **Version:** 7.1.4
- **Repository:** https://codecentric.github.io/helm-charts
- **Purpose:** Provides base Keycloak Kubernetes resources (StatefulSet, Service, Ingress)

### Dependency Resolution

Helm dependencies are downloaded during the Maven `helm:init` phase and stored in the `charts/` subdirectory of the packaged chart.

## Configuration Parameter Categories

### 1. Startup and Runtime Parameters
- Custom startup command with setup script
- HTTP enablement and port configuration
- Hostname validation settings
- Event logging levels

### 2. Image and Registry Parameters
- Docker image repository and tag
- GitHub Container Registry integration
- Multi-architecture support (linux/amd64, linux/arm64)

### 3. Database Parameters
- PostgreSQL vendor selection
- Connection parameters (host, port, database)
- Credentials (username, password)
- Database readiness checking

### 4. Security Parameters
- Bootstrap admin credentials
- Client credentials for config-cli
- Kubernetes Secret integration
- Pod security context

### 5. Environment Configuration
- Java VM options
- JGroups clustering configuration
- ConfigMap and Secret mounting
- Custom environment variables

## Best Practices and Recommendations

### Security Considerations

1. **Default Credentials:** Database and admin credentials are hardcoded defaults
   - **Recommendation:** Override with secure values in production
   - **Method:** Use Kubernetes Secrets with strong passwords

2. **HTTP Enabled:** HTTP is enabled alongside HTTPS
   - **Recommendation:** Disable HTTP in production
   - **Method:** Set `--http-enabled=false` and use reverse proxy

3. **Hostname Strict Mode Disabled:** `--hostname-strict=false`
   - **Recommendation:** Enable strict mode in production
   - **Risk:** Potential hostname spoofing attacks

### High Availability Configuration

The chart includes JGroups DNS-based discovery for clustering:

```yaml
-Djgroups.dns.query={{ include "keycloak.fullname" . }}-headless
```

This enables:
- Kubernetes-native service discovery
- Automatic cluster formation
- Session replication across pods

**Recommendation:** Use with `replicaCount > 1` for HA deployments

### Resource Management

The values.yaml does not specify resource limits. The upstream keycloakx chart provides defaults, but production deployments should define:

```yaml
resources:
  requests:
    memory: "1Gi"
    cpu: "500m"
  limits:
    memory: "2Gi"
    cpu: "2000m"
```

### Monitoring and Observability

Metrics and health endpoints are enabled in build configuration:
- `/metrics` - Prometheus metrics endpoint
- `/health` - Health check endpoint

**Integration Points:**
- Prometheus ServiceMonitor for metrics scraping
- Kubernetes liveness/readiness probes using /health

## Version Compatibility Matrix

| Component | Version | Purpose |
|-----------|---------|---------|
| Keycloak | 26.4.6 | IAM platform |
| Helm Chart (upstream) | keycloakx 7.1.4 | Kubernetes deployment |
| Helm Client | 3.19.2 | Chart packaging |
| PostgreSQL | 16-alpine | Database backend |
| Keycloak Config CLI | 26.1.0_v6.4.0 | Automated configuration |
| Java | 21 | Runtime environment |

## Chart Customization Workflow

1. **Modify values.yaml** - Update configuration parameters
2. **Update ConfigMaps/Secrets** - Add custom environment variables
3. **Maven Build** - Filter resources and generate chart
   ```bash
   mvn clean install
   ```
4. **Local Testing** - Deploy to Minikube
   ```bash
   helm install keycloak-custom ./helm/target/chart
   ```
5. **CI/CD Deployment** - GitHub Actions pushes to registry

## Generated Chart Structure

After Maven build, the chart is generated at:
`/helm/src/generated/keycloak-custom-chart/`

Contains:
- Chart.yaml (version-filtered)
- values.yaml (property-filtered)
- charts/ (upstream dependencies)
- templates/ (from keycloakx dependency)

## Summary

The Keycloak Custom Helm configuration provides:
- Automated deployment to Kubernetes
- Integration with upstream Keycloak Helm chart
- Maven-based build and versioning
- GitHub Container Registry storage
- Custom startup scripts for automated setup
- PostgreSQL database integration
- ConfigMap/Secret-based configuration
- Security context enforcement
- High availability clustering support

The configuration follows infrastructure-as-code principles with version control, automated builds, and declarative configuration management.
