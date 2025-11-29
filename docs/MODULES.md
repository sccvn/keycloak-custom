# Keycloak Custom - Module Documentation

## Module Overview

The Keycloak Custom project is organized as a Maven multi-module build with the following structure:

```
keycloak-custom (root aggregator)
├── server (dependency download)
├── config (configuration & setup)
├── extensions (custom SPI implementations)
│   ├── extension-no-op-authenticator
│   └── extension-no-op-protocol-mapper
├── themes (custom UI themes)
│   ├── inventage
│   └── inventage.v2
├── container (Docker image building)
├── docker-compose (local development environment)
└── helm (Kubernetes deployment)
```

---

## Module Details

### 1. Server Module

**Purpose**: Base Keycloak 26.4.6 distribution preparation

**Location**: `server/`

**Key Files**:
- `pom.xml` - Downloads Keycloak distribution

**Configuration**:
```xml
<dependency>
  <groupId>org.keycloak</groupId>
  <artifactId>keycloak-server</artifactId>
  <version>26.4.6</version>
</dependency>
```

**Build Artifacts**:
- Keycloak 26.4.6 JAR dependencies
- Keycloak runtime libraries

**Dependents**:
- `extensions` (requires Keycloak classpath)
- `container` (includes in Docker image)

**Notes**:
- Provides foundation for all extensions
- Runtime: Quarkus-based
- Java version: 21+

---

### 2. Config Module

**Purpose**: Configuration files, realm definitions, and setup scripts

**Location**: `config/`

**Subdirectories**:
```
config/
├── pom.xml
└── src/main/resources/
    ├── keycloak.conf                    ← Server configuration
    ├── keycloak-config-cli/
    │   ├── realms/
    │   │   └── master-realm.json        ← Realm definition
    │   └── users/
    │       └── default-users.json       ← User definitions
    └── scripts/
        └── kc-with-setup.sh             ← Container startup script
```

**Configuration Files**:

#### keycloak.conf
Runtime configuration for Keycloak server:
```properties
# Database
db=postgres
db-url=jdbc:postgresql://postgres:5432/keycloak
db-username=keycloak
db-password=keycloak

# HTTP/HTTPS
http-enabled=false
https-port=8443

# Hostname
hostname=localhost
hostname-strict=false

# Logging
log-level=INFO

# Themes
theme-cache-themes=false
theme-cache-templates=false
```

#### master-realm.json
Realm configuration (OIDC/OAuth2 clients, user federation):
```json
{
  "realm": "master",
  "enabled": true,
  "users": [...],
  "clients": [...]
}
```

#### default-users.json
User definitions with initial credentials:
```json
{
  "username": "admin",
  "password": "admin",
  "enabled": true,
  "emailVerified": true
}
```

#### kc-with-setup.sh
Container initialization script:
1. Checks for initial admin setup
2. Runs keycloak-config-cli for realm import
3. Starts Keycloak service
4. Waits for health endpoint

**Build Artifacts**:
- Configuration JAR
- Setup scripts (executable)

**Dependents**:
- `container` (copies configs into Docker image)

**Configuration Management**:
- Build-time defaults in config module
- Runtime overrides via environment variables
- Realm definitions via keycloak-config-cli

**Security Considerations**:
- Default credentials (admin/admin) hardcoded
- HTTP disabled in production config
- Database password in plaintext (use Kubernetes Secrets)

---

### 3. Extensions Module

**Purpose**: Custom SPI implementations for Keycloak

**Location**: `extensions/`

#### 3.1 extension-no-op-authenticator

**Purpose**: Demonstration authenticator and theme provider

**Location**: `extensions/extension-no-op-authenticator/`

**Classes**:

##### NoOperationAuthenticator
- **Interface**: `Authenticator`
- **Purpose**: Pass-through authentication (no validation)
- **Methods**:
  - `authenticate()` - Mark user as authenticated without validation
  - `action()` - Handle form submissions
  - `requiresUser()` - Returns true (requires authenticated user)
  - `configuredFor()` - Returns true (can be configured)

**Pattern**: Null Object Pattern (intentional no-op)

**Usage**:
```java
// Flow: User → Authenticator → Authenticated
// No credential validation performed
```

**Security Note**: For demonstration only, not production-ready

---

##### NoOperationFormAuthenticator
- **Interface**: `Authenticator`
- **Purpose**: Form-based authentication with MVC pattern
- **Methods**:
  - `authenticate()` - Display login form
  - `action()` - Process form submission
  - `challenge()` - Render login form template

**Pattern**: MVC (Model-View-Controller) with Form Processing

**Template**: `login.ftl` (Freemarker template)

**Features**:
- Custom login form rendering
- Form validation and error handling
- Session management integration

---

##### NoOperationAuthenticatorFactory
- **Interface**: `AuthenticatorFactory`
- **Purpose**: Register authenticators with Keycloak SPI
- **Uses**: `@AutoService` annotation for auto-discovery

**Configuration**:
```java
@AutoService(Authenticator.class)
public class NoOperationAuthenticatorFactory
    implements AuthenticatorFactory {

  public Authenticator create(KeycloakSession session) {
    return new NoOperationAuthenticator();
  }
}
```

**Auto-Discovery**: Keycloak loads via Java SPI mechanism

---

##### InvitationClasspathThemeProviderFactory
- **Interface**: `ThemeProvider`
- **Purpose**: Load custom theme resources from classpath
- **Pattern**: Theme Resource Loading

**Features**:
- Load theme templates from JAR classpath
- Theme caching and versioning
- Resource fallback mechanism

**Usage**: Enables custom Inventage theme resources

---

#### 3.2 extension-no-op-protocol-mapper

**Purpose**: Custom OIDC claims injection

**Location**: `extensions/extension-no-op-protocol-mapper/`

**Classes**:

##### NoOperationProtocolMapper
- **Interface**: `ProtocolMapper`
- **Purpose**: Inject custom claims into OAuth2/OIDC tokens
- **Protocols**: OIDC, OAuth2

**Methods**:
```java
// Called during token generation
public void setAttribute(ProtocolMapperModel mapping,
                         ProtocolMappingRepresentation rep) {
  // Configure claims to inject
}

public String transformAttributeStatement(
    AttributeStatementType attributeStatement,
    ProtocolMapperModel mapping,
    KeycloakSession session,
    ClientSessionContext context,
    SubjectConfirmationDataType subjectConfirmation) {
  // Add custom claims to token
  return claims;
}
```

**Configuration**:
- Map protocol: `oidc`
- Mapper type: Custom claims injection
- Claims: Configurable per client

**Token Types**: ID Token, Access Token, UserInfo endpoint

**Example**:
```json
{
  "protocolMapper": "oidc-custom-claim-mapper",
  "config": {
    "claim.name": "custom_claim",
    "claim.value": "custom_value",
    "token.claim": true
  }
}
```

---

### 4. Themes Module

**Purpose**: Custom UI themes for Keycloak

**Location**: `themes/`

#### 4.1 inventage Theme

**Location**: `themes/inventage/`

**Structure**:
```
inventage/
├── login/
│   ├── login.ftl              ← Login page template
│   ├── theme.properties       ← Translations
│   └── style.css              ← Styling
├── account/
│   ├── account.ftl
│   ├── theme.properties
│   └── style.css
└── theme.json                 ← Theme metadata
```

**Features**:
- Custom login form with branding
- Custom account management interface
- Internationalization (i18n) support
- Brand colors and logos

**Template Language**: Freemarker (`.ftl`)

**Usage**: Applied via realm configuration

---

#### 4.2 inventage.v2 Theme

**Location**: `themes/inventage.v2/`

**Purpose**: Updated version of Inventage theme

**Improvements**:
- Modern UI design
- Enhanced mobile responsiveness
- Improved accessibility
- Updated branding

**Build**: Both themes packaged into `themes.jar`

**Deployment**: JAR copied into container image

---

### 5. Container Module

**Purpose**: Build multi-architecture Docker image

**Location**: `container/`

**Key Files**:
- `Dockerfile` - Multi-stage build
- `pom.xml` - fabric8 Docker Maven plugin

**Dockerfile Stages**:

#### Stage 1: Builder
```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS builder
# Copy POM and download dependencies
# Build all Maven modules
# Copy artifacts to staging directory
```

**Purpose**: Compile all code and prepare artifacts

#### Stage 2: Runtime
```dockerfile
FROM quay.io/keycloak/keycloak:26.4.6
# Copy provider JARs (extensions)
# Copy theme JARs
# Copy configuration files
# Expose ports 8080, 8443
# Set startup script
```

**Base Image**: `quay.io/keycloak/keycloak:26.4.6`

**Artifacts Copied**:
- Extension JARs → `/opt/keycloak/providers/`
- Theme JARs → `/opt/keycloak/themes/`
- Configuration → `/opt/keycloak/conf/`

**Image Optimization**:
- Multi-stage reduces final size
- Alpine base reduces attack surface
- Layer caching for faster builds

**Build Configuration** (pom.xml):
```xml
<plugin>
  <groupId>io.fabric8</groupId>
  <artifactId>docker-maven-plugin</artifactId>
  <version>0.45.0</version>
  <configuration>
    <images>
      <image>
        <name>ghcr.io/inventage/keycloak-custom:${project.version}</name>
        <build>
          <dockerFile>Dockerfile</dockerFile>
          <args>
            <BUILDKIT_INLINE_CACHE>1</BUILDKIT_INLINE_CACHE>
          </args>
        </build>
      </image>
    </images>
  </configuration>
</plugin>
```

**Multi-Architecture Build**:
- Supports: `linux/amd64`, `linux/arm64`
- Uses Docker buildx
- Pushes to GHCR

---

### 6. Docker Compose Module

**Purpose**: Local development environment with full stack

**Location**: `docker-compose/`

**Structure**:
```
docker-compose/
├── pom.xml
└── src/main/resources/
    ├── docker-compose.yml
    ├── keycloak-custom.env
    ├── postgres/
    │   └── init-db.sh
    └── mailpit/
        └── docker-compose-mailpit.yml
```

**Services**:

#### Keycloak Service
```yaml
keycloak:
  image: ghcr.io/inventage/keycloak-custom:${VERSION}
  ports:
    - "8080:8080"
    - "8443:8443"
  environment:
    KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak
    KC_DB_USERNAME: keycloak
    KC_DB_PASSWORD: keycloak
    KC_HOSTNAME: localhost
  depends_on:
    - postgres
  networks:
    - keycloak-network
```

#### PostgreSQL Service
```yaml
postgres:
  image: postgres:15-alpine
  environment:
    POSTGRES_DB: keycloak
    POSTGRES_USER: keycloak
    POSTGRES_PASSWORD: keycloak
  volumes:
    - postgres-data:/var/lib/postgresql/data
  networks:
    - keycloak-network
```

#### Mailpit Service (Email Testing)
```yaml
mailpit:
  image: axllent/mailpit:latest
  ports:
    - "1025:1025"    # SMTP
    - "8025:8025"    # Web UI
  networks:
    - keycloak-network
```

**Networking**:
- Custom bridge network: `keycloak-network`
- Service discovery via DNS (container name)
- External ports: 8080, 8443, 1025, 8025

**Volumes**:
- PostgreSQL: `postgres-data` (persistent)

**Access Points**:
- Admin Console: `http://localhost:8080/admin`
- Realm Endpoint: `http://localhost:8080/auth/realms/master`
- Metrics: `http://localhost:8080/metrics`
- Health: `http://localhost:8080/health`
- Mailpit Web: `http://localhost:8025`

**Startup Flow**:
1. PostgreSQL initializes
2. Keycloak container starts
3. kc-with-setup.sh executes
4. keycloak-config-cli imports realm
5. Keycloak service starts
6. Health checks validate readiness

**Environment Configuration**:
- File: `keycloak-custom.env`
- Variables: Database, Keycloak, email settings

---

### 7. Helm Module

**Purpose**: Kubernetes deployment package

**Location**: `helm/`

**Structure**:
```
helm/
├── pom.xml
└── src/main/resources/
    ├── Chart.yaml
    ├── values.yaml
    ├── templates/
    │   ├── deployment.yaml
    │   ├── service.yaml
    │   ├── configmap.yaml
    │   ├── secret.yaml
    │   └── ingress.yaml
    └── README.md
```

**Chart Metadata**:
```yaml
apiVersion: v2
name: keycloak-custom
description: Keycloak with custom extensions
version: 26.4.6
appVersion: 26.4.6
```

**Key Templates**:

#### Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ .Release.Name }}-keycloak
spec:
  replicas: {{ .Values.replicaCount }}
  template:
    spec:
      containers:
      - name: keycloak
        image: {{ .Values.image.repository }}:{{ .Values.image.tag }}
        ports:
        - containerPort: 8080
        - containerPort: 8443
        env:
        - name: KC_DB_URL
          value: {{ .Values.database.url }}
        livenessProbe:
          httpGet:
            path: /health/live
            port: 8080
        readinessProbe:
          httpGet:
            path: /health/ready
            port: 8080
        resources:
          requests:
            memory: {{ .Values.resources.requests.memory }}
            cpu: {{ .Values.resources.requests.cpu }}
          limits:
            memory: {{ .Values.resources.limits.memory }}
            cpu: {{ .Values.resources.limits.cpu }}
```

#### Service
```yaml
apiVersion: v1
kind: Service
metadata:
  name: {{ .Release.Name }}-keycloak
spec:
  type: {{ .Values.service.type }}
  ports:
  - port: 80
    targetPort: 8080
    name: http
  - port: 443
    targetPort: 8443
    name: https
  selector:
    app: keycloak
```

#### ConfigMap (Non-sensitive configuration)
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ .Release.Name }}-keycloak-config
data:
  keycloak.conf: |
    {{ .Files.Get "keycloak.conf" }}
```

#### Secret (Sensitive data)
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: {{ .Release.Name }}-keycloak-secret
type: Opaque
data:
  db-password: {{ .Values.database.password | b64enc }}
  admin-password: {{ .Values.admin.password | b64enc }}
```

**Values (Configuration)**:
```yaml
# Default values from values.yaml

replicaCount: 1
image:
  repository: ghcr.io/inventage/keycloak-custom
  tag: "26.4.6"

database:
  url: jdbc:postgresql://external-postgres:5432/keycloak
  username: keycloak
  password: <override-required>

resources:
  requests:
    memory: "512Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "1000m"

service:
  type: ClusterIP
  port: 80

ingress:
  enabled: false
  className: "nginx"
  hosts:
    - host: keycloak.example.com
```

**Usage**:
```bash
# Install
helm install keycloak oci://ghcr.io/inventage/keycloak-custom/helm:26.4.6

# Upgrade with custom values
helm upgrade keycloak oci://ghcr.io/inventage/keycloak-custom/helm:26.4.6 \
  -f custom-values.yaml

# Access
kubectl port-forward svc/keycloak-keycloak 8080:80
```

**High Availability**:
- Scale replicas: `helm upgrade keycloak ... --set replicaCount=3`
- Requires external PostgreSQL with replication
- Session affinity: Recommended for stateful apps

---

## Dependency Graph

```
server (base)
  ↓
extensions ← requires server
  ├── extension-no-op-authenticator
  └── extension-no-op-protocol-mapper
  ↓
container ← includes extensions + server + themes
  ├── docker-compose
  └── helm
```

**Build Order**:
1. server (foundations)
2. config (setup)
3. extensions (SPI implementations)
4. themes (UI)
5. container (Docker image)
6. docker-compose (local development)
7. helm (Kubernetes deployment)

---

## Build System

### Maven Multi-Module

**Root POM** (`pom.xml`):
```xml
<packaging>pom</packaging>
<modules>
  <module>server</module>
  <module>config</module>
  <module>extensions/extension-no-op-authenticator</module>
  <module>extensions/extension-no-op-protocol-mapper</module>
  <module>themes/inventage</module>
  <module>themes/inventage.v2</module>
  <module>container</module>
  <module>docker-compose</module>
  <module>helm</module>
</modules>
```

### Build Commands

```bash
# Clean build with all modules
mvn clean install

# Build specific module
mvn clean install -pl :extension-no-op-authenticator

# Skip tests
mvn clean install -DskipTests

# Docker image
mvn clean install -pl :container docker:build

# Helm chart
mvn clean install -pl :helm
```

---

## Testing Strategy

### Unit Tests
- Location: `src/test/java/`
- Framework: JUnit Jupiter 5.12.1
- Coverage: Authenticators, mappers, factories
- **Status**: No unit tests currently implemented

### Integration Tests
- Location: `src/test/java/`
- Framework: Testcontainers 3.7.0
- Coverage: Full container + Keycloak + extensions
- Tests:
  1. Container startup validation
  2. Realm configuration import
  3. Extension loading verification

### E2E Tests
- **Status**: Not implemented
- Recommendation: Playwright for browser testing

---

## Deployment Workflow

### Local Development
```bash
docker-compose -f docker-compose/src/main/resources/docker-compose.yml up -d
# Access: http://localhost:8080
```

### Staging/Production (Kubernetes)
```bash
helm install keycloak oci://ghcr.io/inventage/keycloak-custom/helm:26.4.6 \
  -f production-values.yaml \
  -n keycloak \
  --create-namespace
```

### CI/CD Pipeline
- Trigger: Push to main
- Build: Maven clean install
- Docker: Multi-arch image build + push to GHCR
- Helm: Chart packaging + publish to registry
- Artifact: Available for deployment

---

## Maintenance

### Adding New Extensions
1. Create module: `extensions/extension-<name>/`
2. Implement SPI (e.g., Authenticator)
3. Add `@AutoService` annotation
4. Update root POM with module
5. Build: `mvn clean install`
6. Extensions auto-loaded in container

### Updating Keycloak Version
1. Update `server/pom.xml` version
2. Update `container/Dockerfile` base image
3. Test extension compatibility
4. Update Helm chart version
5. Rebuild and publish

### Troubleshooting

| Issue | Cause | Solution |
|-------|-------|----------|
| Extensions not loading | JAR not in providers/ | Verify Dockerfile COPY commands |
| Database connection failed | PostgreSQL not running | Start postgres service |
| Realm import fails | YAML syntax error | Validate JSON with jsonlint |
| Container won't start | Keycloak config invalid | Check keycloak.conf syntax |

---

## References

- [Keycloak Server Development](https://www.keycloak.org/docs/latest/server_development/)
- [SPI Development](https://www.keycloak.org/docs/latest/server_development/index.html#_server_developer_guide)
- [Helm Chart Docs](./helm/README.md)
- [Docker Compose Docs](./docker-compose/README.md)
