# Keycloak Custom - Deployment Guide

## Deployment Overview

Keycloak Custom supports three deployment models:

1. **Local Development** - Docker Compose with full stack
2. **Container** - Standalone Docker image
3. **Kubernetes/Helm** - Cloud-native production deployment

---

## 1. Local Development with Docker Compose

### Prerequisites

- Docker Engine 20.10+
- Docker Compose 2.0+
- 4GB+ available RAM
- 10GB+ available disk space

### Quick Start

```bash
# Clone repository
git clone https://github.com/inventage/keycloak-custom.git
cd keycloak-custom

# Build everything
mvn clean install

# Start services
docker-compose -f docker-compose/src/main/resources/docker-compose.yml up -d

# Verify services
docker-compose -f docker-compose/src/main/resources/docker-compose.yml ps
```

### Access Points

| Service | URL | Username | Password |
|---------|-----|----------|----------|
| Admin Console | http://localhost:8080/admin | admin | admin |
| Master Realm | http://localhost:8080/auth/realms/master | - | - |
| Metrics | http://localhost:8080/metrics | - | - |
| Health | http://localhost:8080/health | - | - |
| Mailpit Web | http://localhost:8025 | - | - |

### Verify Deployment

```bash
# Check containers running
docker ps

# Check logs
docker logs keycloak-custom_keycloak_1

# Verify health
curl http://localhost:8080/health/ready

# View metrics
curl http://localhost:8080/metrics | head -20

# Test OIDC discovery
curl http://localhost:8080/auth/realms/master/.well-known/openid-configuration
```

### Troubleshooting

#### PostgreSQL won't start
```bash
# Check if port 5432 is in use
lsof -i :5432

# Remove volume and restart
docker-compose down -v
docker-compose up -d postgres
docker-compose logs postgres
```

#### Keycloak won't connect to database
```bash
# Check database connectivity
docker exec keycloak-custom_keycloak_1 \
  psql -h postgres -U keycloak -d keycloak -c "SELECT 1"

# Check environment variables
docker exec keycloak-custom_keycloak_1 env | grep KC_DB
```

#### Port already in use
```bash
# Find and kill process
lsof -i :8080
kill -9 <PID>

# Or change port in docker-compose.yml
# ports:
#   - "8081:8080"
```

#### Realm import fails
```bash
# Check keycloak logs
docker logs keycloak-custom_keycloak_1 | grep -i error

# Validate JSON
cd config/src/main/resources
cat keycloak-config-cli/realms/master-realm.json | jq .
```

### Cleanup

```bash
# Stop services
docker-compose -f docker-compose/src/main/resources/docker-compose.yml down

# Remove volumes (WARNING: deletes data)
docker-compose -f docker-compose/src/main/resources/docker-compose.yml down -v

# Clean up images
docker rmi ghcr.io/inventage/keycloak-custom:latest
```

---

## 2. Standalone Docker Container

### Build Image

```bash
# Build container module
mvn clean install -pl :container

# Verify image
docker images | grep keycloak-custom
```

### Run Container

#### With External PostgreSQL

```bash
# Create network
docker network create keycloak-net

# Start PostgreSQL
docker run -d \
  --name postgres \
  --network keycloak-net \
  -e POSTGRES_DB=keycloak \
  -e POSTGRES_USER=keycloak \
  -e POSTGRES_PASSWORD=keycloak \
  -v postgres-data:/var/lib/postgresql/data \
  postgres:15-alpine

# Start Keycloak
docker run -d \
  --name keycloak \
  --network keycloak-net \
  -p 8080:8080 \
  -e KC_DB=postgres \
  -e KC_DB_URL=jdbc:postgresql://postgres:5432/keycloak \
  -e KC_DB_USERNAME=keycloak \
  -e KC_DB_PASSWORD=keycloak \
  -e KC_HOSTNAME=localhost \
  ghcr.io/inventage/keycloak-custom:26.4.6

# Access
curl http://localhost:8080/health/ready
```

#### With Environment File

```bash
# Create .env file
cat > keycloak.env << EOF
KC_DB=postgres
KC_DB_URL=jdbc:postgresql://postgres:5432/keycloak
KC_DB_USERNAME=keycloak
KC_DB_PASSWORD=keycloak
KC_HOSTNAME=keycloak.example.com
KC_HTTP_ENABLED=false
KC_LOG_LEVEL=INFO
EOF

# Run with env file
docker run -d \
  --name keycloak \
  -p 8080:8080 \
  -p 8443:8443 \
  --env-file keycloak.env \
  -v /etc/keycloak/certs:/etc/keycloak/certs:ro \
  ghcr.io/inventage/keycloak-custom:26.4.6
```

### View Logs

```bash
# Real-time logs
docker logs -f keycloak

# Last 100 lines
docker logs --tail 100 keycloak

# Search for errors
docker logs keycloak | grep -i error
```

---

## 3. Kubernetes/Helm Deployment

### Prerequisites

- Kubernetes 1.24+
- Helm 3.10+
- kubectl configured for cluster access
- External PostgreSQL database (production)
- TLS certificates (production)

### Installation

#### 1. Add Helm Repository

```bash
# Add GHCR Helm repository (if configured)
helm repo add keycloak-custom \
  oci://ghcr.io/inventage/keycloak-custom/helm

helm repo update
```

#### 2. Create Namespace

```bash
kubectl create namespace keycloak
```

#### 3. Create Secrets

```bash
# Database credentials
kubectl create secret generic keycloak-db-secret \
  --from-literal=db-password='your-secure-password' \
  -n keycloak

# TLS Certificate (production)
kubectl create secret tls keycloak-tls \
  --cert=/path/to/tls.crt \
  --key=/path/to/tls.key \
  -n keycloak

# Admin credentials
kubectl create secret generic keycloak-admin-secret \
  --from-literal=admin-password='your-admin-password' \
  -n keycloak
```

#### 4. Create Values File

```bash
cat > production-values.yaml << 'EOF'
# Image configuration
image:
  repository: ghcr.io/inventage/keycloak-custom
  tag: "26.4.6"
  pullPolicy: IfNotPresent

# Replica configuration
replicaCount: 3

# Resource limits
resources:
  requests:
    memory: "512Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "1000m"

# Database configuration
database:
  url: jdbc:postgresql://postgres.example.com:5432/keycloak
  username: keycloak
  # password from secret

# Hostname
hostname: keycloak.example.com
hostnameStrict: true
httpEnabled: false

# TLS
tls:
  enabled: true
  certificateSecret: keycloak-tls

# Service configuration
service:
  type: LoadBalancer
  port: 80
  targetPort: 8080

# Ingress configuration
ingress:
  enabled: true
  className: nginx
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
  hosts:
    - host: keycloak.example.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: keycloak-tls
      hosts:
        - keycloak.example.com

# Health checks
livenessProbe:
  httpGet:
    path: /health/live
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /health/ready
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 5

# Node affinity (optional)
affinity:
  podAntiAffinity:
    preferredDuringSchedulingIgnoredDuringExecution:
      - weight: 100
        podAffinityTerm:
          labelSelector:
            matchExpressions:
              - key: app
                operator: In
                values:
                  - keycloak
          topologyKey: kubernetes.io/hostname
EOF
```

#### 5. Install Helm Chart

```bash
# Install from local chart
helm install keycloak ./helm/src/main/resources \
  -f production-values.yaml \
  -n keycloak

# Or from registry
helm install keycloak \
  oci://ghcr.io/inventage/keycloak-custom/helm:26.4.6 \
  -f production-values.yaml \
  -n keycloak
```

### Verify Installation

```bash
# Check deployment
kubectl get deployment -n keycloak
kubectl get pods -n keycloak

# Check services
kubectl get svc -n keycloak

# Check logs
kubectl logs -f deployment/keycloak-keycloak -n keycloak

# Port forward for testing
kubectl port-forward -n keycloak svc/keycloak-keycloak 8080:80

# Test health
curl http://localhost:8080/health/ready
```

### Upgrade Deployment

```bash
# Update Helm values
helm upgrade keycloak ./helm/src/main/resources \
  -f production-values.yaml \
  -n keycloak

# Or specific values
helm upgrade keycloak \
  oci://ghcr.io/inventage/keycloak-custom/helm:26.4.7 \
  --set replicaCount=5 \
  --set image.tag=26.4.7 \
  -n keycloak

# Monitor rollout
kubectl rollout status deployment/keycloak-keycloak -n keycloak

# View revision history
helm history keycloak -n keycloak

# Rollback if needed
helm rollback keycloak 1 -n keycloak
```

### Scale Deployment

```bash
# Scale replicas
kubectl scale deployment keycloak-keycloak \
  --replicas=5 \
  -n keycloak

# Or via Helm values
helm upgrade keycloak ./helm/src/main/resources \
  --set replicaCount=5 \
  -n keycloak
```

### Access Application

```bash
# Via LoadBalancer (production)
kubectl get svc -n keycloak keycloak-keycloak
# Note external IP and access via FQDN

# Via port-forward (testing)
kubectl port-forward -n keycloak svc/keycloak-keycloak 8080:80

# Via Ingress (production)
# Access via keycloak.example.com (configured in Ingress)
```

### Troubleshooting

#### Pod not starting
```bash
# Check pod status
kubectl describe pod <pod-name> -n keycloak

# Check events
kubectl get events -n keycloak --sort-by='.lastTimestamp'

# Check logs
kubectl logs <pod-name> -n keycloak -c keycloak
```

#### Database connection failed
```bash
# Test database connectivity
kubectl run -it --rm debug \
  --image=postgres:15-alpine \
  --restart=Never \
  -n keycloak \
  -- psql -h postgres.example.com -U keycloak -d keycloak -c "SELECT 1"

# Check DNS resolution
kubectl run -it --rm debug \
  --image=alpine:latest \
  --restart=Never \
  -n keycloak \
  -- nslookup postgres.example.com
```

#### Metrics not working
```bash
# Verify metrics endpoint
kubectl exec -it <pod-name> -n keycloak \
  -- curl http://localhost:8080/metrics

# Check service monitor (if using Prometheus)
kubectl get servicemonitor -n keycloak
```

### Uninstall

```bash
# Remove Helm release
helm uninstall keycloak -n keycloak

# Remove namespace
kubectl delete namespace keycloak

# Remove PVCs (if using persistent storage)
kubectl delete pvc -n keycloak --all
```

---

## 4. CI/CD Deployment

### GitHub Actions Workflow

The project includes GitHub Actions workflow for automated deployment:

```yaml
# .github/workflows/build-pipeline.yml
name: Build Pipeline

on:
  push:
    branches:
      - main
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - name: Build with Maven
        run: mvn clean install

      - name: Build Docker image
        run: docker build -t ghcr.io/inventage/keycloak-custom:${{ github.sha }} .

      - name: Push to GHCR
        run: |
          echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u ${{ github.actor }} --password-stdin
          docker push ghcr.io/inventage/keycloak-custom:${{ github.sha }}
```

### Deployment Strategies

#### Blue-Green Deployment
```bash
# Deploy to green environment
helm install keycloak-green \
  oci://ghcr.io/inventage/keycloak-custom/helm:26.4.6 \
  -f green-values.yaml \
  -n keycloak

# Test green environment
kubectl port-forward -n keycloak svc/keycloak-green 8080:80

# Switch traffic (update ingress)
kubectl patch ingress keycloak-ingress -p \
  '{"spec":{"rules":[{"host":"keycloak.example.com","http":{"paths":[{"backend":{"service":{"name":"keycloak-green"}}}]}}]}}'

# Remove blue
helm uninstall keycloak-blue -n keycloak
```

#### Rolling Deployment
```bash
# Default Helm behavior
helm upgrade keycloak \
  oci://ghcr.io/inventage/keycloak-custom/helm:26.4.6 \
  --set image.tag=26.4.7 \
  -n keycloak

# Monitor
kubectl rollout status deployment/keycloak-keycloak -n keycloak
```

#### Canary Deployment
```bash
# Deploy canary version
helm install keycloak-canary \
  oci://ghcr.io/inventage/keycloak-custom/helm:26.4.6 \
  -f canary-values.yaml \
  -n keycloak

# Configure ingress with traffic split (using Istio or Flagger)
# 10% to canary, 90% to stable
```

---

## 5. Database Setup

### PostgreSQL Configuration

#### Local Development
```bash
# PostgreSQL container (included in docker-compose)
# Created automatically in docker-compose.yml
```

#### Production

```sql
-- Create database
CREATE DATABASE keycloak;

-- Create user
CREATE USER keycloak WITH PASSWORD 'secure_password';

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak;

-- Connect to keycloak database
\c keycloak

-- Grant schema privileges
GRANT ALL PRIVILEGES ON SCHEMA public TO keycloak;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO keycloak;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO keycloak;

-- Create extensions (if needed)
CREATE EXTENSION IF NOT EXISTS uuid-ossp;
```

#### Backup & Restore

```bash
# Backup database
pg_dump -h localhost -U keycloak -d keycloak -F c > keycloak_backup.dump

# Restore database
pg_restore -h localhost -U keycloak -d keycloak keycloak_backup.dump

# For Kubernetes
kubectl exec -it postgres-pod -n keycloak -- \
  pg_dump -U keycloak -d keycloak > keycloak_backup.sql

kubectl exec -it postgres-pod -n keycloak -- \
  psql -U keycloak -d keycloak < keycloak_backup.sql
```

---

## 6. Monitoring & Observability

### Prometheus Setup

```yaml
# prometheus-config.yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'keycloak'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/metrics'
```

### Grafana Dashboard

```bash
# Add Prometheus data source
# Create dashboard with metrics:
# - jvm_memory_used_bytes
# - http_server_requests_seconds
# - jvm_threads_count
# - gc_duration_seconds
```

### Health Checks

```bash
# Liveness probe
curl -f http://localhost:8080/health/live || exit 1

# Readiness probe
curl -f http://localhost:8080/health/ready || exit 1

# Combined check
curl -f http://localhost:8080/health || exit 1
```

---

## 7. Security Hardening

### Before Production Deployment

1. **Change Default Credentials**
   ```bash
   # Update admin password immediately
   kubectl exec -it <pod> -n keycloak \
     -- kcadm.sh config credentials --server http://localhost:8080 \
       --realm master --user admin --password admin --client admin-cli

   kubectl exec -it <pod> -n keycloak \
     -- kcadm.sh set-password --cclientid admin-cli --cclientsecret <secret> \
       -r master -u admin -p newSecurePassword123!
   ```

2. **Enable HTTPS Only**
   ```bash
   # Disable HTTP
   helm upgrade keycloak ./helm \
     --set httpEnabled=false \
     --set tls.enabled=true \
     -n keycloak
   ```

3. **Configure Hostname Strict Mode**
   ```bash
   helm upgrade keycloak ./helm \
     --set hostnameStrict=true \
     -n keycloak
   ```

4. **Set Up TLS Certificates**
   ```bash
   # Use Let's Encrypt with cert-manager
   kubectl apply -f - <<EOF
   apiVersion: cert-manager.io/v1
   kind: Issuer
   metadata:
     name: letsencrypt-prod
     namespace: keycloak
   spec:
     acme:
       server: https://acme-v02.api.letsencrypt.org/directory
       email: admin@example.com
       privateKeySecretRef:
         name: letsencrypt-prod
       solvers:
         - http01:
             ingress:
               class: nginx
   EOF
   ```

5. **Enable RBAC**
   ```bash
   # Create Kubernetes RBAC
   kubectl create role keycloak-read --verb=get,list --resource=pods -n keycloak
   kubectl create rolebinding keycloak-read \
     --role=keycloak-read \
     --user=monitoring-user \
     -n keycloak
   ```

---

## 8. Backup & Disaster Recovery

### Automated Backups

```bash
# Backup PostgreSQL daily
0 2 * * * pg_dump -h localhost -U keycloak keycloak | gzip > /backups/keycloak_$(date +\%Y\%m\%d).dump.gz

# Backup Kubernetes manifests
0 3 * * * kubectl get all -n keycloak -o yaml > /backups/keycloak_$(date +\%Y\%m\%d).yaml
```

### Disaster Recovery

```bash
# 1. Restore database
gunzip < /backups/keycloak_20231215.dump.gz | pg_restore -h localhost -U keycloak -d keycloak

# 2. Restore Kubernetes resources
kubectl apply -f /backups/keycloak_20231215.yaml

# 3. Verify system
kubectl get pods -n keycloak
curl http://keycloak.example.com/health/ready
```

---

## 9. Performance Tuning

### JVM Settings

```yaml
# Helm values
resources:
  requests:
    memory: "1Gi"
    cpu: "500m"
  limits:
    memory: "2Gi"
    cpu: "2000m"

# Environment variables
KC_HEAP_SIZE: "1024m"
KC_JVM_SETTINGS: >
  -XX:+UseG1GC
  -XX:MaxMetaspaceSize=512m
  -XX:InitiatingHeapOccupancyPercent=35
  -XX:G1HeapRegionSize=16m
```

### Database Optimization

```sql
-- Create indexes for common queries
CREATE INDEX idx_user_entity_username ON user_entity(username);
CREATE INDEX idx_user_entity_email ON user_entity(email);
CREATE INDEX idx_user_role_mapping_user_id ON user_role_mapping(user_id);
CREATE INDEX idx_realm_default_roles ON realm_default_roles(realm_id);

-- Analyze tables
ANALYZE user_entity;
ANALYZE user_role_mapping;
```

### Connection Pool Tuning

```properties
# keycloak.conf
db-pool-size=20
db-pool-max-lifetime=30m
db-pool-max-idle-time=10m
```

---

## References

- [Keycloak Server Installation Guide](https://www.keycloak.org/docs/latest/server_installation/)
- [Keycloak Kubernetes Guide](https://www.keycloak.org/docs/latest/server_installation/index.html#_kubernetes)
- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Helm Documentation](https://helm.sh/docs/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
