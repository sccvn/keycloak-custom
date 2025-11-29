# Docker and Container Orchestration Analysis - Keycloak Custom

## Executive Summary

The Keycloak Custom project uses a multi-stage Docker build process with Docker Compose for local development and testing. The container strategy includes custom Keycloak configuration, automated setup scripts, and integration with PostgreSQL and Mailpit services.

## Dockerfile Analysis

### Location
`/container/src/main/resources/Dockerfile`

### Build Strategy: Multi-Stage Build

#### Stage 1: UBI Micro Build (Utility Installation)

```dockerfile
FROM registry.access.redhat.com/ubi9 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs jq tzdata --releasever 9 \
    --setopt install_weak_deps=false --nodocs -y; \
    dnf --installroot /mnt/rootfs clean all
```

**Purpose:**
- Creates a minimal filesystem with utility tools
- Installs `jq` (JSON processor) for scripting
- Installs `tzdata` (timezone data) for timezone configuration
- Uses Red Hat Universal Base Image 9
- Minimizes image size by excluding weak dependencies and documentation

**Key Tools:**
- **jq:** JSON parsing and manipulation in setup scripts
- **tzdata:** Timezone database for Europe/Zurich configuration

#### Stage 2: Main Keycloak Build

```dockerfile
FROM quay.io/keycloak/keycloak:${keycloak.version}
COPY --from=ubi-micro-build /mnt/rootfs /
```

**Base Image:**
- Official Keycloak image from Quay.io
- Version: 26.4.6 (Maven-filtered via ${keycloak.version})
- Based on UBI8/UBI-minimal (Red Hat Universal Base Image)

**Utility Integration:**
- Copies jq and tzdata from previous stage
- Minimal impact on final image size

### Configuration and Setup Files

```dockerfile
COPY ./keycloak/ /opt/keycloak/
RUN /opt/keycloak/bin/kc.sh build
```

**Copied Directories:**
- `bin/` - Custom startup and setup scripts
- `conf/` - Build-time configuration (keycloak.conf)
- `providers/` - Custom Keycloak extensions (JAR files)
- `setup/` - Realm configuration JSON files

**Build Process:**
- Executes `kc.sh build` to optimize Keycloak
- Pre-compiles configuration
- Validates providers
- Generates optimized Quarkus application

### Security and User Configuration

```dockerfile
USER root
RUN ln -sf /usr/share/zoneinfo/Europe/Zurich /etc/localtime
RUN chmod -R g+rwx /opt/keycloak

USER 1000
WORKDIR /opt/keycloak
```

**Security Practices:**
1. **Timezone Configuration:** Symlinks to Europe/Zurich timezone
2. **Group Permissions:** Allows group read/write/execute (OpenShift compatibility)
3. **Non-Root User:** Switches to UID 1000 (keycloak user)
4. **Working Directory:** Sets /opt/keycloak as working directory

**OpenShift Compatibility:**
- Group permissions allow arbitrary UIDs with GID 0
- Follows OpenShift security best practices

### Container Entrypoint and Command

```dockerfile
ENTRYPOINT ["/opt/keycloak/bin/kc-with-setup.sh"]
CMD ["start", "--optimized"]
```

**Startup Flow:**
1. **Entrypoint:** Custom wrapper script `kc-with-setup.sh`
2. **Default CMD:** Starts Keycloak in optimized mode
3. **Setup Integration:** Script runs keycloak-config-cli before starting server

## Docker Compose Analysis

### Main Application Compose File

**Location:** `/docker-compose/src/main/resources/docker-compose.yml`

```yaml
version: '3'

networks:
  keycloak:

services:
  keycloak:
    image: ${docker.registry}${project.groupId}.container:${project.version}
    container_name: ${project.groupId}.container
    ports:
      - "8080:8080"
      - "8443:8443"
    env_file:
      - keycloak.common.env
      - keycloak.specific.env
      - secrets.env
    networks:
      - keycloak
    labels:
      org.labels-schema.group: "iam"
```

#### Service Configuration

**Image:**
- Maven-filtered: `ghcr.io/inventage/keycloak-custom/com.inventage.keycloak.custom.container:${version}`
- Dynamically versioned from POM

**Port Mapping:**
- `8080:8080` - HTTP endpoint
- `8443:8443` - HTTPS endpoint

**Environment Files:**
1. `keycloak.common.env` - Common Keycloak configuration
2. `keycloak.specific.env` - Deployment-specific settings
3. `secrets.env` - Credentials and sensitive data

**Network:**
- Custom bridge network `keycloak`
- Enables service discovery and isolation

**Labels:**
- `org.labels-schema.group: "iam"` - Categorizes as IAM service

### PostgreSQL Development Service

**Location:** `/docker-compose/postgres/docker-compose.yml`

```yaml
version: '3'

services:
  postgres:
    image: postgres:16-alpine
    ports:
      - "15432:5432"
    environment:
      - PGDATA=/var/lib/postgresql/data/pgdata
      - POSTGRES_PASSWORD=postgres
    volumes:
      - ./volume/16/pgdata/:/var/lib/postgresql/data/pgdata
    networks:
      - postgres

networks:
  postgres:
```

#### Database Configuration

**Image:** PostgreSQL 16 Alpine (minimal variant)

**Port Mapping:**
- Host: 15432 (avoids conflict with system PostgreSQL)
- Container: 5432 (standard PostgreSQL port)

**Environment Variables:**
- `PGDATA`: Custom data directory path
- `POSTGRES_PASSWORD`: Default superuser password

**Volume Mount:**
- Local: `./volume/16/pgdata/`
- Container: `/var/lib/postgresql/data/pgdata`
- Purpose: Persistent database storage

**Network:** Isolated `postgres` network

### Mailpit Development Service

**Location:** `/docker-compose/mailpit/docker-compose.yml`

```yaml
version: '3'

services:
  mailpit:
    image: axllent/mailpit:v1.18.6
    restart: unless-stopped
    ports:
      - "1025:1025"  # SMTP port
      - "8025:8025"  # Web port
```

#### Email Testing Configuration

**Image:** Mailpit v1.18.6 (email testing tool)

**Purpose:**
- Catches outbound emails during development
- Provides web UI for email inspection
- No actual email delivery (safe for testing)

**Port Mapping:**
- `1025:1025` - SMTP server for Keycloak to send emails
- `8025:8025` - Web interface for viewing captured emails

**Restart Policy:** `unless-stopped` (survives host reboots)

## Environment Configuration Analysis

### Common Environment File

**Location:** `/docker-compose/src/main/resources/keycloak.common.env`

#### Bootstrap Admin Configuration

```bash
KC_BOOTSTRAP_ADMIN_USERNAME=temp-admin
KC_BOOTSTRAP_ADMIN_PASSWORD=admin
KC_BOOTSTRAP_ADMIN_CLIENT_ID=temp-client-admin
KC_BOOTSTRAP_ADMIN_CLIENT_SECRET=admin
```

**Purpose:**
- Creates initial admin user for first startup
- Creates client with admin realm role
- Used by keycloak-config-cli for automated setup

**Security Note:** Default credentials should be changed immediately

#### Database Configuration

```bash
KC_DB_URL=jdbc:postgresql://localhost:15432/postgres
KC_DB_USERNAME=postgres
KC_DB_PASSWORD=postgres
```

**Connection Details:**
- JDBC URL points to local PostgreSQL on port 15432
- Uses default PostgreSQL database
- Default credentials (development only)

#### Hostname Configuration

```bash
KC_HOSTNAME_STRICT=false
```

**Purpose:**
- Disables strict hostname checking
- Allows dynamic hostname resolution
- Suitable for development, NOT for production

#### HTTP/TLS Configuration

```bash
KC_HTTP_ENABLED=true
KC_HTTPS_CERTIFICATE_FILE=./conf/localhost.pem
KC_HTTPS_CERTIFICATE_KEY_FILE=./conf/localhost-key.pem
```

**HTTP Settings:**
- HTTP enabled (required for keycloak-config-cli)
- Uses default port 8080

**HTTPS Settings:**
- Self-signed certificate for localhost
- Certificate and key stored in conf/ directory
- Uses default port 8443

#### Reverse Proxy Configuration

```bash
KC_PROXY_HEADERS=xforwarded
```

**Purpose:**
- Processes X-Forwarded-* headers
- Enables deployment behind reverse proxy
- Preserves original client IP and protocol

#### Logging Configuration

```bash
KC_LOG_LEVEL=info,org.hibernate.SQL:debug
```

**Log Levels:**
- Root level: INFO
- Hibernate SQL: DEBUG (shows database queries)

#### Keycloak Config CLI Settings

```bash
KEYCLOAK_CONFIG_CLI_CLIENT_ID=keycloak-config-cli
KEYCLOAK_CONFIG_CLI_CLIENT_SECRET=keycloak-config-cli
KEYCLOAK_GRANTTYPE=client_credentials
KEYCLOAK_CLIENTID=${KC_BOOTSTRAP_ADMIN_CLIENT_ID}
KEYCLOAK_CLIENTSECRET=${KC_BOOTSTRAP_ADMIN_CLIENT_SECRET}
```

**Purpose:**
- Configures automated realm configuration tool
- Uses OAuth2 client credentials flow
- References bootstrap client credentials

## Custom Startup Scripts

### Main Startup Script: kc-with-setup.sh

**Location:** `/config/src/main/resources/keycloak/bin/kc-with-setup.sh`

#### Script Structure (213 lines)

**Part 1: Keycloak Launcher (Lines 1-190)**
- Copied from official Keycloak distribution
- Handles JVM configuration
- Processes command-line arguments
- Manages debug mode
- Configures memory settings
- Launches Keycloak process

**Part 2: Custom Setup Integration (Lines 191-213)**

```bash
KEYCLOAK_PID=$!

forwardSigterm() {
    echo "--> SIGTERM received - forwarding to java pid"
    kill -TERM "$KEYCLOAK_PID" 2>/dev/null
}

trap forwardSigterm SIGTERM

source $DIRNAME/keycloak-setup.sh

wait $KEYCLOAK_PID
echo "--> Keycloak process has shut down. Exiting."
```

**Key Features:**
1. **Background Execution:** Keycloak runs in background with `&`
2. **Signal Handling:** Traps SIGTERM and forwards to Keycloak process
3. **Setup Execution:** Sources keycloak-setup.sh during startup
4. **Graceful Shutdown:** Waits for Keycloak process to exit

### Setup Orchestration Script: keycloak-setup.sh

**Location:** `/config/src/main/resources/keycloak/bin/keycloak-setup.sh`

```bash
#!/usr/bin/env bash

trap 'exit' ERR

runKeycloakConfigCli() {
  java -jar "${BASEDIR}"/client/keycloak-config-cli-26.1.0_v6.4.0.jar \
    --keycloak.url=http://localhost:8080/ \
    --keycloak.ssl-verify=true \
    --keycloak.availability-check.enabled=true \
    --keycloak.availability-check.timeout=300s \
    --import.var-substitution.enabled=true \
    --import.managed.client=no-delete \
    --import.managed.client-scope=no-delete \
    --import.managed.client-scope-mapping=no-delete \
    --import.files.locations="${BASEDIR}"/../setup/*.json \
    --logging.level.root=info
}

runKeycloakCli() {
  KCADM="${BASEDIR}"/kcadm.sh
  KCADM_CONFIG="--config /tmp/.keycloak/kcadm.config"

  ${KCADM} config credentials \
    --server http://localhost:8080 \
    --client "${ADMIN_CLIENT_ID}" \
    --secret "${ADMIN_CLIENT_SECRET}" \
    --realm master

  source "${BASEDIR}"/keycloak-cli-helpers.sh
  source "${BASEDIR}"/keycloak-cli-custom.sh
}
```

#### Setup Flow

1. **Keycloak Config CLI:**
   - Waits for Keycloak availability (300s timeout)
   - Imports realm configurations from JSON files
   - Uses variable substitution
   - Configures no-delete policy for clients and scopes

2. **Keycloak Admin CLI:**
   - Authenticates using client credentials
   - Sources helper functions
   - Executes custom configuration scripts

## Build Configuration (keycloak.conf)

**Location:** `/config/src/main/resources/keycloak/conf/keycloak.conf`

### Build-Time Settings

```conf
db=postgres
features=organization
metrics-enabled=true
health-enabled=true
```

**Database:** PostgreSQL vendor selected

**Features:**
- `organization` - Enables organization management feature

**Monitoring:**
- `/metrics` - Prometheus metrics endpoint
- `/health` - Health check endpoint

## Container Build and Registry Integration

### Maven Docker Plugin Configuration

**Plugin:** io.fabric8:docker-maven-plugin:0.45.0

#### Build Configuration

```xml
<image>
  <name>${docker.registry}${docker.image.name}</name>
  <build>
    <tags>
      <tag>latest</tag>
      <tag>${project.version}</tag>
    </tags>
  </build>
</image>
```

**Tags:**
- `latest` - Always points to most recent build
- `${project.version}` - Specific version tag

#### Multi-Architecture Build

```xml
<buildx>
  <platforms>
    <platform>linux/arm64</platform>
    <platform>linux/amd64</platform>
  </platforms>
</buildx>
```

**Supported Architectures:**
- linux/amd64 (x86_64)
- linux/arm64 (ARM 64-bit)

**Build Tool:** Docker Buildx (BuildKit backend)

### Build Lifecycle

1. **build-image** (install phase) - Build Docker image
2. **tag-image** (install phase) - Tag with version and latest
3. **push-image** (deploy phase) - Push to GitHub Container Registry

### GitHub Container Registry Integration

**Registry:** ghcr.io/inventage/keycloak-custom/

**Authentication:**
```xml
<authConfig>
  <username>${env.GITHUB_ACTOR}</username>
  <password>${env.GITHUB_TOKEN}</password>
</authConfig>
```

Uses GitHub Actions environment variables for authentication.

## Service Dependencies and Orchestration

### Development Stack Architecture

```
┌─────────────────────────────────────────────────────┐
│                  Developer Machine                   │
│                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────┐ │
│  │   Keycloak   │  │  PostgreSQL  │  │  Mailpit  │ │
│  │   :8080      │  │   :15432     │  │  :1025    │ │
│  │   :8443      │  │              │  │  :8025    │ │
│  └──────┬───────┘  └──────┬───────┘  └─────┬─────┘ │
│         │                 │                 │       │
│         └─────────────────┴─────────────────┘       │
│              Network: keycloak / postgres           │
└─────────────────────────────────────────────────────┘
```

### Service Communication

**Keycloak → PostgreSQL:**
- JDBC connection to localhost:15432
- Database: postgres
- User: postgres

**Keycloak → Mailpit:**
- SMTP connection to localhost:1025
- Email testing and verification

**Developer → Keycloak:**
- HTTP: http://localhost:8080
- HTTPS: https://localhost:8443

**Developer → Mailpit Web UI:**
- Web Interface: http://localhost:8025

## Container Security Analysis

### Image Security Features

1. **Base Image:** Red Hat Universal Base Image (UBI9)
   - Enterprise-grade Linux distribution
   - Regular security updates
   - CVE scanning and patching

2. **Minimal Dependencies:**
   - Only essential tools (jq, tzdata)
   - No unnecessary packages
   - Reduced attack surface

3. **Non-Root Execution:**
   - Runs as UID 1000 (keycloak user)
   - Drops privileges after setup
   - Follows least privilege principle

4. **Group Permissions:**
   - Group writable for OpenShift compatibility
   - Allows arbitrary UIDs in GID 0

### Configuration Security

**Secrets Management:**
- Uses separate `secrets.env` file
- Should not be committed to version control
- Contains credentials and API keys

**Network Isolation:**
- Services on isolated Docker networks
- No unnecessary port exposure
- Host networking disabled

## Volume and Data Persistence

### PostgreSQL Data Persistence

```yaml
volumes:
  - ./volume/16/pgdata/:/var/lib/postgresql/data/pgdata
```

**Purpose:**
- Persists database across container restarts
- Local filesystem storage
- Version-specific directory (16/)

**Location:** Relative to docker-compose.yml

### Keycloak Configuration

Keycloak configuration is baked into the container image, not mounted as volumes. This ensures:
- Immutable configuration
- Reproducible deployments
- Version-controlled changes

## Development Workflow

### Local Development Setup

1. **Start PostgreSQL:**
   ```bash
   cd docker-compose/postgres
   docker-compose up -d
   ```

2. **Start Mailpit:**
   ```bash
   cd docker-compose/mailpit
   docker-compose up -d
   ```

3. **Build Keycloak Container:**
   ```bash
   mvn clean install
   ```

4. **Start Keycloak:**
   ```bash
   cd docker-compose/src/main/resources
   docker-compose up
   ```

### Container Logs and Debugging

**View Logs:**
```bash
docker-compose logs -f keycloak
```

**Access Container:**
```bash
docker exec -it com.inventage.keycloak.custom.container bash
```

**Check Database Connection:**
```bash
docker exec -it postgres psql -U postgres
```

## Deployment Topology Comparison

| Aspect | Docker Compose | Kubernetes (Helm) |
|--------|---------------|-------------------|
| Orchestration | Single host | Multi-node cluster |
| High Availability | No | Yes (replicas) |
| Load Balancing | Manual | Automatic (Service) |
| Secrets Management | .env files | Kubernetes Secrets |
| Networking | Docker networks | CNI plugins |
| Storage | Local volumes | PersistentVolumes |
| Service Discovery | Container names | DNS (headless service) |
| Scaling | Manual | Horizontal Pod Autoscaler |
| Use Case | Development/Testing | Production |

## Summary

The Docker and container orchestration setup provides:
- **Multi-stage builds** for minimal image size
- **Custom startup scripts** for automated configuration
- **PostgreSQL integration** for data persistence
- **Mailpit integration** for email testing
- **Multi-architecture support** (amd64, arm64)
- **GitHub Container Registry** for image distribution
- **Environment-based configuration** for flexibility
- **Security hardening** with non-root users and minimal dependencies
- **Development-friendly** compose files for local testing

The container strategy emphasizes automation, security, and reproducibility while maintaining developer productivity.
