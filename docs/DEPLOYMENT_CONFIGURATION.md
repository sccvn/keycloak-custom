# Keycloak Custom - Deployment Configuration Documentation

## Overview

This document provides comprehensive deployment configuration details for the Keycloak Custom project, covering Docker Compose, Kubernetes/Helm, and CI/CD pipeline setup.

---

## Table of Contents

1. [Docker Configuration](#docker-configuration)
2. [Kubernetes/Helm Configuration](#kuberneteshelm-configuration)
3. [Environment Variables](#environment-variables)
4. [CI/CD Pipeline](#cicd-pipeline)
5. [Deployment Procedures](#deployment-procedures)
6. [Monitoring & Health Checks](#monitoring--health-checks)
7. [Troubleshooting](#troubleshooting)

---

## Docker Configuration

### Container Build

**Base Image:** `quay.io/keycloak/keycloak:26.4.6`

**Dockerfile Location:** `/home/tuanna47/workspace/keycloak-custom/container/src/main/resources/Dockerfile`

**Build Process:**
1. Uses Red Hat UBI9 base for additional utilities (jq, tzdata)
2. Copies custom Keycloak configuration, providers, and setup scripts
3. Runs Keycloak build process: `/opt/keycloak/bin/kc.sh build`
4. Sets timezone to Europe/Zurich
5. Entrypoint: `/opt/keycloak/bin/kc-with-setup.sh`

**Container Image:**
- Registry: `ghcr.io/inventage/keycloak-custom/`
- Image Name: `com.inventage.keycloak.custom.container`
- Version: `1.0.0-SNAPSHOT`

### Docker Compose Services

#### Main Keycloak Service

**Location:** `/home/tuanna47/workspace/keycloak-custom/docker-compose/src/main/resources/docker-compose.yml`

```yaml
services:
  keycloak:
    image: ghcr.io/inventage/keycloak-custom/com.inventage.keycloak.custom.container:1.0.0-SNAPSHOT
    container_name: com.inventage.keycloak.custom.container
    ports:
      - "8080:8080"  # HTTP
      - "8443:8443"  # HTTPS
    env_file:
      - keycloak.common.env
      - keycloak.specific.env
      - secrets.env
    networks:
      - keycloak
```

**Exposed Ports:**
- `8080`: HTTP endpoint (enabled in dev mode)
- `8443`: HTTPS endpoint (with TLS certificates)

#### PostgreSQL Database Service

**Location:** `/home/tuanna47/workspace/keycloak-custom/docker-compose/postgres/docker-compose.yml`

```yaml
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
```

**Database Configuration:**
- Version: PostgreSQL 16 Alpine
- External Port: `15432` (mapped to internal 5432)
- Data Persistence: Local volume mount at `./volume/16/pgdata/`
- Default credentials: postgres/postgres (CHANGE IN PRODUCTION)

#### Mailpit Service (Email Testing)

**Location:** `/home/tuanna47/workspace/keycloak-custom/docker-compose/mailpit/docker-compose.yml`

```yaml
services:
  mailpit:
    image: axllent/mailpit:v1.18.6
    restart: unless-stopped
    ports:
      - "1025:1025"  # SMTP
      - "8025:8025"  # Web UI
```

**Email Testing:**
- SMTP Server: `localhost:1025`
- Web Interface: `http://localhost:8025`
- Purpose: Captures outbound emails for development/testing

### Volume Mounts

**Keycloak Container:**
- `/opt/keycloak/`: Application directory
- `/opt/keycloak/conf/`: Configuration files
- `/opt/keycloak/providers/`: Custom SPI providers
- `/opt/keycloak/themes/`: Custom themes
- `/opt/keycloak/setup/`: Realm configuration JSON files

**PostgreSQL Container:**
- `./volume/16/pgdata/`: Persistent database storage

---

## Kubernetes/Helm Configuration

### Helm Chart

**Chart Information:**
- Name: `keycloak-custom-chart`
- Version: `1.0.0-SNAPSHOT`
- API Version: `v2`
- Dependency: Codecentric Keycloakx chart v7.1.4

**Chart Location:** `/home/tuanna47/workspace/keycloak-custom/helm/src/main/resources/`

### Deployment Configuration

**Values File:** `/home/tuanna47/workspace/keycloak-custom/helm/src/main/resources/values.yaml`

#### Command & Arguments

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

#### Image Configuration

```yaml
image:
  repository: ghcr.io/inventage/keycloak-custom/com.inventage.keycloak.custom.container
  tag: 1.0.0-SNAPSHOT
```

#### Database Configuration

```yaml
database:
  vendor: postgres
  hostname: postgresql
  port: 5432
  username: keycloak
  password: keycloak  # Use secrets in production
  database: keycloak
```

#### Database Health Check

```yaml
dbchecker:
  enabled: true
```

Purpose: Ensures database is ready before Keycloak starts

#### Extra Environment Variables

**From ConfigMap:**
```yaml
extraEnvFrom: |
  - configMapRef:
      name: keycloak-custom-config-vars
  - secretRef:
      name: keycloak-custom-secret-vars
```

**Inline Environment Variables:**
```yaml
extraEnv: |
  - name: JAVA_OPTS_APPEND
    value: >-
      -Djava.awt.headless=true
      -Djgroups.dns.query={{ include "keycloak.fullname" . }}-headless

  # Bootstrap Admin User
  - name: KC_BOOTSTRAP_ADMIN_USERNAME
    value: temp-admin

  # Bootstrap Admin Client
  - name: KC_BOOTSTRAP_ADMIN_CLIENT_ID
    value: temp-client-admin

  # Keycloak Config CLI Settings
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

#### Security Context

```yaml
securityContext:
  allowPrivilegeEscalation: false
  capabilities:
    drop:
      - ALL
```

Implements least-privilege security model.

### Kubernetes Resources

#### ConfigMap

**File:** `/home/tuanna47/workspace/keycloak-custom/helm/src/test/resources/local/keycloak-custom-config-vars.local.yaml`

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: keycloak-custom-config-vars
  namespace: default
data:
  EXAMPLE1_DISPLAY_NAME: "Example One (minikube)"
```

#### Secret

**File:** `/home/tuanna47/workspace/keycloak-custom/helm/src/test/resources/local/keycloak-custom-secret-vars.local.yaml`

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

**⚠️ PRODUCTION WARNING:** These are example values. Use proper secret management (e.g., Sealed Secrets, External Secrets Operator, Vault) in production.

---

## Environment Variables

### Bootstrap Admin Configuration

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `KC_BOOTSTRAP_ADMIN_USERNAME` | Temporary admin username for initial setup | `temp-admin` | Yes (first run) |
| `KC_BOOTSTRAP_ADMIN_PASSWORD` | Temporary admin password | `admin` | Yes (first run) |
| `KC_BOOTSTRAP_ADMIN_CLIENT_ID` | Temporary admin client ID | `temp-client-admin` | Yes (first run) |
| `KC_BOOTSTRAP_ADMIN_CLIENT_SECRET` | Temporary admin client secret | `admin` | Yes (first run) |

### Permanent Configuration Client

| Variable | Description | Default |
|----------|-------------|---------|
| `KEYCLOAK_CONFIG_CLI_CLIENT_ID` | Permanent config client ID | `keycloak-config-cli` |
| `KEYCLOAK_CONFIG_CLI_CLIENT_SECRET` | Permanent config client secret | `keycloak-config-cli` |

### Keycloak Config CLI Settings

| Variable | Description | Default |
|----------|-------------|---------|
| `KEYCLOAK_GRANTTYPE` | OAuth grant type | `client_credentials` |
| `KEYCLOAK_CLIENTID` | Client ID for config operations | `${KC_BOOTSTRAP_ADMIN_CLIENT_ID}` |
| `KEYCLOAK_CLIENTSECRET` | Client secret for config operations | `${KC_BOOTSTRAP_ADMIN_CLIENT_SECRET}` |

**⚠️ After First Setup:** Change `KEYCLOAK_CLIENTID` and `KEYCLOAK_CLIENTSECRET` to use the permanent client.

### Database Configuration

**File:** `/home/tuanna47/workspace/keycloak-custom/docker-compose/src/main/resources/keycloak.common.env`

| Variable | Description | Default |
|----------|-------------|---------|
| `KC_DB_URL` | JDBC connection URL | `jdbc:postgresql://localhost:15432/postgres` |
| `KC_DB_USERNAME` | Database username | `postgres` |
| `KC_DB_PASSWORD` | Database password | `postgres` |

**Build Configuration:**
```bash
db=postgres  # Set in keycloak.conf
```

### HTTP/TLS Configuration

| Variable | Description | Default |
|----------|-------------|---------|
| `KC_HTTP_ENABLED` | Enable HTTP endpoint | `true` |
| `KC_HTTP_PORT` | HTTP port | `8080` |
| `KC_HTTPS_PORT` | HTTPS port | `8443` |
| `KC_HTTPS_CERTIFICATE_FILE` | TLS certificate path | `./conf/localhost.pem` |
| `KC_HTTPS_CERTIFICATE_KEY_FILE` | TLS key path | `./conf/localhost-key.pem` |
| `KC_HOSTNAME_STRICT` | Strict hostname validation | `false` |

### Reverse Proxy Configuration

| Variable | Description | Default |
|----------|-------------|---------|
| `KC_PROXY_HEADERS` | Proxy mode | `xforwarded` |

Supports `X-Forwarded-*` headers for reverse proxy deployments.

### Logging Configuration

| Variable | Description | Default |
|----------|-------------|---------|
| `KC_LOG_LEVEL` | Log level configuration | `info,org.hibernate.SQL:debug` |

### Java Options

```bash
JAVA_OPTS_APPEND="-Djava.awt.headless=true -Djgroups.dns.query=..."
```

Container memory settings:
- `MaxRAMPercentage=70`: Maximum heap 70% of container memory
- `MinRAMPercentage=70`: Minimum heap 70% of container memory
- `InitialRAMPercentage=50`: Initial heap 50% of container memory

### Feature Flags

**File:** `/home/tuanna47/workspace/keycloak-custom/server/target/keycloak/conf/keycloak.conf`

```properties
features=organization
```

Enables Keycloak organization feature.

---

## CI/CD Pipeline

### GitHub Actions Workflow

**File:** `/home/tuanna47/workspace/keycloak-custom/.github/workflows/build-pipeline.yml`

#### Trigger Events

```yaml
on:
  push:
    branches:
      - main
  pull_request:
    branches:
      - main
```

#### Build Steps

1. **Checkout Code**
   ```yaml
   - uses: actions/checkout@v3
   ```

2. **Setup JDK 21**
   ```yaml
   - name: Set up JDK 21
     uses: actions/setup-java@v3
     with:
       java-version: "21"
       distribution: "temurin"
       cache: maven
   ```

3. **Version Replacement**
   ```bash
   # Replace SNAPSHOT with: YYYYMMDDHHMM-BUILD_NUMBER-GIT_HASH
   snapshot_version=$(./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout)
   major_minor_patch=$(echo "${snapshot_version}" | sed 's/SNAPSHOT//g')
   date=$(date -u +%Y%m%d%H%M)
   release_candidate_version=${major_minor_patch}${date}-${{github.run_number}}-${GITHUB_SHA::8}
   ./mvnw versions:set -DnewVersion=${release_candidate_version}
   ```

4. **Maven Build & Deploy**
   ```bash
   ./mvnw -B -DmultiArchBuild=true -DskipTests deploy --file pom.xml
   ```

   Environment:
   - `GITHUB_ACTOR`: Repository owner
   - `GITHUB_TOKEN`: GitHub token for GHCR push

5. **Cleanup Old Images**
   - Keeps minimum 5 versions
   - Deletes only pre-release versions
   - Targets:
     - `keycloak-custom/com.inventage.keycloak.custom.container`
     - `keycloak-custom/keycloak-custom-chart`

#### Artifacts Published

1. **Docker Image:** `ghcr.io/inventage/keycloak-custom/com.inventage.keycloak.custom.container`
2. **Helm Chart:** `ghcr.io/inventage/keycloak-custom/keycloak-custom-chart`

#### Permissions Required

```yaml
permissions:
  packages: write
```

---

## Deployment Procedures

### Local Development Setup

1. **Start PostgreSQL Database**
   ```bash
   cd /home/tuanna47/workspace/keycloak-custom/docker-compose/postgres
   docker-compose up -d
   ```

2. **Start Mailpit (Optional)**
   ```bash
   cd /home/tuanna47/workspace/keycloak-custom/docker-compose/mailpit
   docker-compose up -d
   ```

3. **Build Project**
   ```bash
   cd /home/tuanna47/workspace/keycloak-custom
   ./mvnw clean install
   ```

4. **Start Keycloak**
   ```bash
   cd /home/tuanna47/workspace/keycloak-custom/docker-compose/target/keycloak
   docker-compose up
   ```

5. **Access Keycloak**
   - HTTP: `http://localhost:8080`
   - HTTPS: `https://localhost:8443`
   - Admin Console: `http://localhost:8080/admin`
   - Credentials: `temp-admin` / `admin` (first run)

### Kubernetes/Minikube Deployment

1. **Start Minikube**
   ```bash
   minikube start
   ```

2. **Build & Load Image**
   ```bash
   cd /home/tuanna47/workspace/keycloak-custom
   ./mvnw clean install -DskipTests
   eval $(minikube docker-env)
   docker load -i container/target/docker/com.inventage.keycloak.custom.container/1.0.0-SNAPSHOT/tmp/docker-build.tar
   ```

3. **Create Secrets & ConfigMaps**
   ```bash
   kubectl apply -f helm/src/test/resources/local/keycloak-custom-config-vars.local.yaml
   kubectl apply -f helm/src/test/resources/local/keycloak-custom-secret-vars.local.yaml
   ```

4. **Install PostgreSQL**
   ```bash
   helm repo add bitnami https://charts.bitnami.com/bitnami
   helm install postgresql bitnami/postgresql -f helm/src/test/resources/postgresql/values.minikube.yaml
   ```

5. **Install Keycloak**
   ```bash
   cd helm/target/helm/keycloak-custom-chart
   helm install keycloak-custom . -f values.yaml
   ```

6. **Port Forward**
   ```bash
   kubectl port-forward svc/keycloak-custom-keycloakx-http 8080:80
   ```

7. **Access**
   - URL: `http://localhost:8080`

### Production Deployment Checklist

- [ ] **Change Default Passwords**
  - Update `KC_BOOTSTRAP_ADMIN_PASSWORD`
  - Update `KC_BOOTSTRAP_ADMIN_CLIENT_SECRET`
  - Update database passwords

- [ ] **Use External Secrets Management**
  - Integrate with HashiCorp Vault, AWS Secrets Manager, or Azure Key Vault
  - Remove hardcoded secrets from ConfigMaps/Secrets

- [ ] **Switch to Permanent Config Client**
  - Update `KEYCLOAK_CLIENTID` to `keycloak-config-cli`
  - Update `KEYCLOAK_CLIENTSECRET` to use permanent client secret

- [ ] **Enable HTTPS Only**
  - Set `KC_HTTP_ENABLED=false`
  - Configure valid TLS certificates
  - Set `KC_HOSTNAME_STRICT=true`

- [ ] **Configure Reverse Proxy**
  - Set appropriate `KC_PROXY_HEADERS` value
  - Configure ingress with TLS termination

- [ ] **Database Configuration**
  - Use managed database service (RDS, Cloud SQL, Azure Database)
  - Enable SSL/TLS for database connections
  - Configure connection pooling
  - Enable automated backups

- [ ] **Resource Limits**
  - Set CPU and memory requests/limits
  - Configure autoscaling policies

- [ ] **Monitoring & Logging**
  - Enable `/metrics` endpoint collection
  - Configure centralized logging (ELK, Loki, CloudWatch)
  - Set up alerting rules

- [ ] **High Availability**
  - Deploy multiple replicas (min 3 for production)
  - Configure load balancing
  - Enable cache clustering (Infinispan)

- [ ] **Backup & Disaster Recovery**
  - Automated database backups
  - Export realm configurations regularly
  - Document restore procedures

---

## Monitoring & Health Checks

### Health Check Endpoints

**Configuration:** `health-enabled=true` in `keycloak.conf`

**Endpoints:**
- Health: `http://localhost:8080/health`
- Readiness: `http://localhost:8080/health/ready`
- Liveness: `http://localhost:8080/health/live`

**Response Example:**
```json
{
  "status": "UP",
  "checks": [
    {
      "name": "Keycloak database connections health check",
      "status": "UP"
    }
  ]
}
```

### Metrics Endpoint

**Configuration:** `metrics-enabled=true` in `keycloak.conf`

**Endpoint:** `http://localhost:8080/metrics`

**Prometheus Format:**
```
# HELP jvm_memory_used_bytes The amount of used memory
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap",id="G1 Eden Space"} 1.23456789E8
```

**Key Metrics:**
- JVM memory usage
- Thread counts
- HTTP request rates
- Database connection pool stats
- Authentication/authorization counts

### Database Health Check

Helm chart includes `dbchecker` that ensures PostgreSQL is ready before Keycloak starts.

### Logging Configuration

**Log Levels:**
```properties
KC_LOG_LEVEL=info,org.hibernate.SQL:debug
```

**Event Logging:**
```bash
--spi-events-listener-jboss-logging-success-level=info
--spi-events-listener-jboss-logging-error-level=warn
```

**Log Output:**
- Container stdout/stderr
- Accessible via: `docker logs <container_name>` or `kubectl logs <pod_name>`

### Recommended Monitoring Setup

1. **Prometheus + Grafana**
   - Scrape `/metrics` endpoint
   - Use Keycloak Grafana dashboards
   - Alert on:
     - High memory usage (>80%)
     - Failed login attempts spike
     - Database connection failures

2. **Application Performance Monitoring (APM)**
   - Elastic APM
   - Datadog
   - New Relic
   - Configure Java agent in `JAVA_OPTS_APPEND`

3. **Log Aggregation**
   - ELK Stack (Elasticsearch, Logstash, Kibana)
   - Grafana Loki
   - AWS CloudWatch Logs
   - Azure Monitor Logs

---

## Troubleshooting

### Common Issues

#### 1. Container Fails to Start

**Symptom:** Keycloak container exits immediately

**Diagnostic:**
```bash
docker logs <container_name>
kubectl logs <pod_name>
```

**Common Causes:**
- Database not accessible
- Missing environment variables
- Invalid configuration

**Solutions:**
- Verify database is running: `docker ps | grep postgres`
- Check database connectivity: `psql -h localhost -p 15432 -U postgres`
- Validate environment files exist and are readable
- Check `keycloak.conf` syntax

#### 2. Database Connection Failed

**Symptom:** "Unable to connect to database"

**Diagnostic:**
```bash
# Test database connection
docker exec -it postgres-container psql -U postgres

# Check database logs
docker logs postgres-container
```

**Solutions:**
- Verify `KC_DB_URL` format: `jdbc:postgresql://hostname:port/database`
- Check database credentials
- Ensure database network is accessible
- Verify PostgreSQL is listening on correct port

#### 3. Authentication Failed During Setup

**Symptom:** keycloak-config-cli or kcadm authentication errors

**Diagnostic:**
Check bootstrap credentials:
```bash
echo $KC_BOOTSTRAP_ADMIN_USERNAME
echo $KC_BOOTSTRAP_ADMIN_CLIENT_ID
```

**Solutions:**
- Verify bootstrap credentials are set correctly
- Ensure bootstrap client has admin role
- Check if temporary admin has been deleted (use permanent client)
- Review `/setup` directory realm configurations

#### 4. Helm Deployment Fails

**Symptom:** Pod in CrashLoopBackOff or ImagePullBackOff

**Diagnostic:**
```bash
kubectl get pods
kubectl describe pod <pod_name>
kubectl logs <pod_name>
```

**Solutions:**
- **ImagePullBackOff:** Check image name and registry credentials
  ```bash
  kubectl create secret docker-registry ghcr-secret \
    --docker-server=ghcr.io \
    --docker-username=$GITHUB_USER \
    --docker-password=$GITHUB_TOKEN
  ```
- **CrashLoopBackOff:** Review pod logs for startup errors
- Check ConfigMap and Secret existence:
  ```bash
  kubectl get configmap keycloak-custom-config-vars
  kubectl get secret keycloak-custom-secret-vars
  ```

#### 5. HTTPS/TLS Certificate Issues

**Symptom:** "Certificate not found" or SSL errors

**Diagnostic:**
```bash
# Check certificate files in container
docker exec <container> ls -la /opt/keycloak/conf/localhost*.pem
```

**Solutions:**
- Verify certificate files exist at configured paths
- Check file permissions (must be readable by user 1000)
- Generate self-signed certificates for development:
  ```bash
  openssl req -newkey rsa:2048 -nodes -keyout localhost-key.pem \
    -x509 -days 365 -out localhost.pem
  ```
- For production, use Let's Encrypt or proper CA-signed certificates

#### 6. High Memory Usage / OOM Errors

**Symptom:** Container killed due to out-of-memory

**Diagnostic:**
```bash
docker stats
kubectl top pods
```

**Solutions:**
- Increase container memory limits
- Tune JVM settings:
  ```yaml
  JAVA_OPTS_APPEND: "-Xms512m -Xmx2048m"
  ```
- For Kubernetes, adjust heap percentage:
  ```yaml
  JAVA_OPTS_KC_HEAP: "-XX:MaxRAMPercentage=70 -XX:InitialRAMPercentage=50"
  ```

#### 7. Custom Providers Not Loading

**Symptom:** SPI provider not registered

**Diagnostic:**
```bash
# Check providers directory
docker exec <container> ls -la /opt/keycloak/providers/

# Check Keycloak startup logs for provider loading
docker logs <container> | grep -i "provider"
```

**Solutions:**
- Verify JAR files are in `/opt/keycloak/providers/`
- Ensure providers are compiled for correct Keycloak version (26.4.6)
- Check for META-INF/services provider registration
- Rebuild image after adding new providers

### Debug Mode

Enable debug logging:

**Docker Compose:**
```yaml
environment:
  - KC_LOG_LEVEL=debug
  - DEBUG=true
  - DEBUG_PORT=8787
```

**Kubernetes:**
```yaml
extraEnv: |
  - name: KC_LOG_LEVEL
    value: debug
```

**Command Line:**
```bash
/opt/keycloak/bin/kc.sh start --debug 8787
```

Connect debugger to port 8787.

### Useful Commands

**View Keycloak Configuration:**
```bash
docker exec <container> /opt/keycloak/bin/kc.sh show-config
```

**Export Realm:**
```bash
docker exec <container> /opt/keycloak/bin/kc.sh export \
  --file /tmp/realm-export.json \
  --realm master
```

**Check Database Connection:**
```bash
docker exec <container> /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://localhost:8080 \
  --realm master \
  --user temp-admin \
  --password admin
```

**Test Health Endpoint:**
```bash
curl -v http://localhost:8080/health
curl -v http://localhost:8080/health/ready
curl -v http://localhost:8080/health/live
```

**Test Metrics Endpoint:**
```bash
curl http://localhost:8080/metrics
```

---

## Additional Resources

- **Keycloak Documentation:** https://www.keycloak.org/documentation
- **Codecentric Helm Chart:** https://github.com/codecentric/helm-charts/tree/master/charts/keycloakx
- **Project README:** `/home/tuanna47/workspace/keycloak-custom/README.md`
- **Custom Template Guide:** https://keycloak.ch/keycloak-tutorials/tutorial-custom-keycloak/

---

## Deployment Summary

### Docker Services

| Service | Image | Ports | Purpose |
|---------|-------|-------|---------|
| Keycloak | `ghcr.io/inventage/keycloak-custom/com.inventage.keycloak.custom.container:1.0.0-SNAPSHOT` | 8080, 8443 | Main IAM server |
| PostgreSQL | `postgres:16-alpine` | 15432→5432 | Database backend |
| Mailpit | `axllent/mailpit:v1.18.6` | 1025, 8025 | Email testing |

### Kubernetes Configuration

- **Namespace:** `default`
- **Helm Chart:** keycloak-custom-chart (depends on codecentric/keycloakx:7.1.4)
- **Database:** PostgreSQL (external service or Bitnami chart)
- **ConfigMap:** keycloak-custom-config-vars
- **Secret:** keycloak-custom-secret-vars

### CI/CD Pipeline

- **Platform:** GitHub Actions
- **JDK Version:** 21 (Temurin)
- **Build Tool:** Maven 3.x
- **Artifacts:** Docker image + Helm chart → GitHub Container Registry (GHCR)
- **Retention:** 5 most recent versions

### Environment Requirements

- **Java:** 17+ (container runs JDK 17)
- **Database:** PostgreSQL 12+
- **Container Runtime:** Docker 20.10+ or containerd
- **Kubernetes:** 1.24+ (if using Helm)
- **Maven:** 3.6+

---

**Document Version:** 1.0
**Last Updated:** 2025-11-30
**Maintained By:** Backend Developer Agent
