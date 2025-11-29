# Infrastructure Architecture and Deployment Topology - Keycloak Custom

## Executive Summary

The Keycloak Custom project supports multiple deployment topologies optimized for different environments: local development with Docker Compose, Kubernetes with Helm for production, and automated CI/CD through GitHub Actions. The architecture emphasizes flexibility, scalability, and automated configuration management.

## Deployment Environment Matrix

| Environment | Orchestration | Database | Configuration | Use Case |
|-------------|--------------|----------|---------------|----------|
| Development | Docker Compose | PostgreSQL 16 (local) | .env files | Local testing |
| Minikube | Helm + K8s | PostgreSQL (Bitnami chart) | ConfigMaps/Secrets | Local K8s testing |
| Production | Helm + K8s | PostgreSQL (external/managed) | ConfigMaps/Secrets | Production workloads |
| CI/CD | Docker Build | N/A (test containers) | GitHub Secrets | Automated builds |

## Architecture Patterns

### 1. Local Development Topology (Docker Compose)

```
┌─────────────────────────────────────────────────────────────────┐
│                     Developer Workstation                        │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                    Docker Engine                            │ │
│  │                                                             │ │
│  │  ┌──────────────────┐  ┌──────────────┐  ┌─────────────┐  │ │
│  │  │   Keycloak       │  │ PostgreSQL   │  │  Mailpit    │  │ │
│  │  │   Container      │  │   16-Alpine  │  │             │  │ │
│  │  │                  │  │              │  │  SMTP: 1025 │  │ │
│  │  │  HTTP: 8080      │◄─┤ Port: 15432  │  │  Web: 8025  │  │ │
│  │  │  HTTPS: 8443     │  │              │  │             │  │ │
│  │  │                  │  │ Volume:      │  │             │  │ │
│  │  │  Env:            │  │ ./volume/16/ │  │             │  │ │
│  │  │  - common.env    │  │   pgdata/    │  │             │  │ │
│  │  │  - specific.env  │  │              │  │             │  │ │
│  │  │  - secrets.env   │  │              │  │             │  │ │
│  │  └──────────────────┘  └──────────────┘  └─────────────┘  │ │
│  │         │                      │                 │         │ │
│  │         └──────────────────────┴─────────────────┘         │ │
│  │                  Network: keycloak                         │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  Access:                                                         │
│  - http://localhost:8080 (Keycloak HTTP)                        │
│  - https://localhost:8443 (Keycloak HTTPS)                      │
│  - http://localhost:8025 (Mailpit Web UI)                       │
└─────────────────────────────────────────────────────────────────┘
```

**Key Characteristics:**
- **Isolation:** Each service runs in isolated container
- **Networking:** Bridge network for inter-container communication
- **Persistence:** Local volume mount for PostgreSQL data
- **Configuration:** Environment file-based configuration
- **Startup:** Manual orchestration (start services individually)

**Advantages:**
- Fast iteration and debugging
- No Kubernetes overhead
- Direct file system access
- Simple dependency management

**Limitations:**
- Single host (no HA)
- Manual service management
- No automatic scaling
- Limited to development use

### 2. Kubernetes Production Topology (Helm)

```
┌──────────────────────────────────────────────────────────────────────────┐
│                       Kubernetes Cluster                                  │
│                                                                           │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │                    Namespace: default                               │  │
│  │                                                                     │  │
│  │  ┌───────────────────────────────────────────────────────────────┐ │  │
│  │  │                    Ingress Controller                          │ │  │
│  │  │              (TLS Termination / Load Balancing)               │ │  │
│  │  └────────────────────────┬──────────────────────────────────────┘ │  │
│  │                           │                                         │  │
│  │  ┌────────────────────────▼──────────────────────────────────────┐ │  │
│  │  │              Service: keycloak-custom-http                     │ │  │
│  │  │              Type: ClusterIP, Port: 80 → 8080                  │ │  │
│  │  └────────────────────────┬──────────────────────────────────────┘ │  │
│  │                           │                                         │  │
│  │  ┌────────────────────────▼──────────────────────────────────────┐ │  │
│  │  │              StatefulSet: keycloak-custom                      │ │  │
│  │  │              Replicas: 2+ (for HA)                             │ │  │
│  │  │                                                                 │ │  │
│  │  │  ┌─────────────────┐         ┌─────────────────┐              │ │  │
│  │  │  │   Pod 0         │         │   Pod 1         │              │ │  │
│  │  │  │                 │         │                 │              │ │  │
│  │  │  │  Container:     │◄───────►│  Container:     │              │ │  │
│  │  │  │  keycloak       │ JGroups │  keycloak       │              │ │  │
│  │  │  │                 │ Cluster │                 │              │ │  │
│  │  │  │  Port: 8080     │         │  Port: 8080     │              │ │  │
│  │  │  │                 │         │                 │              │ │  │
│  │  │  │  Resources:     │         │  Resources:     │              │ │  │
│  │  │  │  CPU: 500m      │         │  CPU: 500m      │              │ │  │
│  │  │  │  MEM: 1Gi       │         │  MEM: 1Gi       │              │ │  │
│  │  │  └────────┬────────┘         └────────┬────────┘              │ │  │
│  │  │           │                           │                        │ │  │
│  │  │           └───────────────┬───────────┘                        │ │  │
│  │  │                           │                                     │ │  │
│  │  └───────────────────────────┼─────────────────────────────────── │ │  │
│  │                              │                                     │  │
│  │  ┌───────────────────────────▼─────────────────────────────────┐  │  │
│  │  │           Service: postgresql (Headless)                     │  │  │
│  │  │           Port: 5432                                         │  │  │
│  │  └───────────────────────────┬─────────────────────────────────┘  │  │
│  │                              │                                     │  │
│  │  ┌───────────────────────────▼─────────────────────────────────┐  │  │
│  │  │           StatefulSet: postgresql                            │  │  │
│  │  │                                                               │  │  │
│  │  │  ┌──────────────────────────────────────────────────┐        │  │  │
│  │  │  │  Pod: postgresql-0                               │        │  │  │
│  │  │  │                                                   │        │  │  │
│  │  │  │  Container: postgresql                           │        │  │  │
│  │  │  │  Port: 5432                                      │        │  │  │
│  │  │  │                                                   │        │  │  │
│  │  │  │  PersistentVolumeClaim: data                     │        │  │  │
│  │  │  │  Storage: 10Gi                                   │        │  │  │
│  │  │  └──────────────────────────────────────────────────┘        │  │  │
│  │  └───────────────────────────────────────────────────────────────┘  │  │
│  │                                                                     │  │
│  │  ┌───────────────────────────────────────────────────────────────┐ │  │
│  │  │                   ConfigMap Resources                          │ │  │
│  │  │                                                                │ │  │
│  │  │  - keycloak-custom-config-vars                                │ │  │
│  │  │    (Non-sensitive configuration)                              │ │  │
│  │  └───────────────────────────────────────────────────────────────┘ │  │
│  │                                                                     │  │
│  │  ┌───────────────────────────────────────────────────────────────┐ │  │
│  │  │                    Secret Resources                            │ │  │
│  │  │                                                                │ │  │
│  │  │  - keycloak-custom-secret-vars                                │ │  │
│  │  │    (KC_BOOTSTRAP_ADMIN_PASSWORD,                              │ │  │
│  │  │     KC_BOOTSTRAP_ADMIN_CLIENT_SECRET)                         │ │  │
│  │  └───────────────────────────────────────────────────────────────┘ │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
```

**Key Characteristics:**
- **High Availability:** Multiple Keycloak replicas (StatefulSet)
- **Session Clustering:** JGroups DNS-based discovery
- **Load Balancing:** Kubernetes Service distributes traffic
- **Persistence:** PersistentVolumeClaim for PostgreSQL
- **Configuration:** ConfigMaps and Secrets
- **Auto-Healing:** Kubernetes automatically restarts failed pods

**Components:**

1. **Ingress Controller:**
   - TLS termination
   - HTTP to HTTPS redirect
   - Load balancing across pods

2. **Keycloak StatefulSet:**
   - Ordered pod deployment
   - Stable network identities
   - JGroups clustering enabled

3. **PostgreSQL StatefulSet:**
   - Single instance (can be clustered separately)
   - Persistent storage for data
   - Headless service for direct pod access

4. **Services:**
   - `keycloak-custom-http` (ClusterIP) - Internal access
   - `keycloak-custom-headless` (Headless) - JGroups discovery
   - `postgresql` (ClusterIP) - Database access

**Advantages:**
- High availability (multi-replica)
- Automatic failover
- Horizontal scaling
- Rolling updates
- Resource management
- Production-ready

**Configuration Management:**
- ConfigMaps for environment variables
- Secrets for credentials
- Helm values for customization

### 3. CI/CD Topology (GitHub Actions)

```
┌────────────────────────────────────────────────────────────────────────┐
│                         GitHub Actions Runner                           │
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │                     Build Pipeline Workflow                        │ │
│  │                                                                    │ │
│  │  Step 1: Checkout Repository                                      │ │
│  │  ┌─────────────────────────────────────────────────────────────┐  │ │
│  │  │  actions/checkout@v3                                         │  │ │
│  │  │  - Clones repository                                         │  │ │
│  │  │  - Checks out branch (main or PR)                            │  │ │
│  │  └─────────────────────────────────────────────────────────────┘  │ │
│  │                              │                                     │ │
│  │  Step 2: Setup Java Environment                                   │ │
│  │  ┌─────────────────────────────────────────────────────────────┐  │ │
│  │  │  actions/setup-java@v3                                       │  │ │
│  │  │  - JDK 21 (Temurin distribution)                             │  │ │
│  │  │  - Maven cache enabled                                       │  │ │
│  │  └─────────────────────────────────────────────────────────────┘  │ │
│  │                              │                                     │ │
│  │  Step 3: Version Management                                        │ │
│  │  ┌─────────────────────────────────────────────────────────────┐  │ │
│  │  │  Replace SNAPSHOT with timestamped version                   │  │ │
│  │  │  Format: {version}-{YYYYMMDDHHM M}-{run_number}-{commit_sha}│  │ │
│  │  │  Example: 1.0.0-202511290430-123-a1b2c3d4                    │  │ │
│  │  └─────────────────────────────────────────────────────────────┘  │ │
│  │                              │                                     │ │
│  │  Step 4: Maven Build                                               │ │
│  │  ┌─────────────────────────────────────────────────────────────┐  │ │
│  │  │  mvn -B -DmultiArchBuild=true -DskipTests deploy              │  │ │
│  │  │                                                               │  │ │
│  │  │  Modules Built:                                               │  │ │
│  │  │  1. server - Download and prepare Keycloak                   │  │ │
│  │  │  2. config - Copy configuration files                        │  │ │
│  │  │  3. extensions - Compile custom providers                    │  │ │
│  │  │  4. themes - Package custom themes                           │  │ │
│  │  │  5. container - Build Docker image (multi-arch)              │  │ │
│  │  │  6. docker-compose - Generate compose files                  │  │ │
│  │  │  7. helm - Package Helm chart                                │  │ │
│  │  └─────────────────────────────────────────────────────────────┘  │ │
│  │                              │                                     │ │
│  │                              ├──────────────────┐                  │ │
│  │                              │                  │                  │ │
│  │  Step 5a: Push Docker Image  │  Step 5b: Push Helm Chart          │ │
│  │  ┌────────────────────────┐  │  ┌───────────────────────────────┐ │ │
│  │  │ ghcr.io/inventage/     │  │  │ ghcr.io/inventage/            │ │ │
│  │  │   keycloak-custom/     │  │  │   keycloak-custom/            │ │ │
│  │  │   container:version    │  │  │   keycloak-custom-chart:ver   │ │ │
│  │  │                        │  │  │                               │ │ │
│  │  │ Platforms:             │  │  │ Format: OCI                   │ │ │
│  │  │ - linux/amd64          │  │  │ Compressed: .tgz              │ │ │
│  │  │ - linux/arm64          │  │  │                               │ │ │
│  │  └────────────────────────┘  │  └───────────────────────────────┘ │ │
│  │                              │                                     │ │
│  │  Step 6: Cleanup Old Versions                                      │ │
│  │  ┌─────────────────────────────────────────────────────────────┐  │ │
│  │  │  actions/delete-package-versions@v4                          │  │ │
│  │  │  - Keep minimum 5 versions                                   │  │ │
│  │  │  - Delete only pre-release versions                          │  │ │
│  │  │  - Prevents registry bloat                                   │  │ │
│  │  └─────────────────────────────────────────────────────────────┘  │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│  Triggers:                                                              │
│  - Push to main branch                                                  │
│  - Pull request to main branch                                          │
│                                                                         │
│  Permissions:                                                           │
│  - packages: write (push to GHCR)                                       │
│                                                                         │
│  Secrets Used:                                                          │
│  - GITHUB_TOKEN (automatic)                                             │
│  - GITHUB_ACTOR (automatic)                                             │
└─────────────────────────────────────────────────────────────────────────┘
```

**Build Artifacts:**

1. **Docker Image:**
   - Registry: ghcr.io/inventage/keycloak-custom/com.inventage.keycloak.custom.container
   - Tags: version-specific and `latest`
   - Multi-architecture: amd64, arm64

2. **Helm Chart:**
   - Registry: ghcr.io/inventage/keycloak-custom/keycloak-custom-chart
   - Format: OCI (not traditional HTTP repository)
   - Versioned: Same as Docker image

**Version Strategy:**

For SNAPSHOT versions, CI/CD generates unique version:
```
Base: 1.0.0-SNAPSHOT
Generated: 1.0.0-202511290430-123-a1b2c3d4
           ^^^^^^^^ ^^^^^^^^^^^^^ ^^^ ^^^^^^^^
           Version  Timestamp    Run  Commit
```

**Advantages:**
- Automated builds on every commit
- Multi-architecture support
- Version traceability
- Automated registry management
- Pull request validation

## Network Architecture

### Development Network (Docker Compose)

```
Docker Host
│
├── Network: keycloak (bridge)
│   ├── keycloak:8080 (HTTP)
│   ├── keycloak:8443 (HTTPS)
│   └── Can communicate internally
│
├── Network: postgres (bridge)
│   └── postgres:5432 (internal)
│
└── Host Ports:
    ├── 8080 → keycloak:8080
    ├── 8443 → keycloak:8443
    ├── 15432 → postgres:5432
    ├── 1025 → mailpit:1025 (SMTP)
    └── 8025 → mailpit:8025 (Web)
```

**Isolation:**
- Separate networks for different service groups
- Port mapping for external access
- Internal DNS resolution by container name

### Kubernetes Network (Production)

```
Kubernetes CNI (e.g., Calico, Flannel)
│
├── Cluster IP Range: 10.96.0.0/12 (services)
├── Pod IP Range: 10.244.0.0/16 (pods)
│
├── Service: keycloak-custom-http (ClusterIP)
│   ├── IP: 10.96.x.x
│   └── Port: 80 → targetPort: 8080
│
├── Service: keycloak-custom-headless (Headless)
│   ├── No ClusterIP (DNS only)
│   └── DNS Records:
│       ├── keycloak-custom-0.keycloak-custom-headless.default.svc.cluster.local
│       └── keycloak-custom-1.keycloak-custom-headless.default.svc.cluster.local
│
├── Service: postgresql (ClusterIP)
│   ├── IP: 10.96.y.y
│   └── Port: 5432
│
└── Ingress (External)
    ├── Public IP / Load Balancer
    ├── TLS: *.example.com
    └── Routes:
        └── /auth → keycloak-custom-http:80
```

**Key Features:**
- ClusterIP services for internal communication
- Headless service for JGroups clustering
- Ingress for external HTTPS access
- DNS-based service discovery
- NetworkPolicies for security (optional)

## Storage Architecture

### Development Storage (Docker Volumes)

```
Host Filesystem
│
├── docker-compose/postgres/volume/16/pgdata/
│   └── PostgreSQL data files (bind mount)
│
└── Container ephemeral storage:
    └── Keycloak cache, logs, temp files
```

**Characteristics:**
- Bind mounts for database persistence
- Local filesystem (not cloud storage)
- Simple backup (copy directory)

### Kubernetes Storage (PersistentVolumes)

```
Storage Backend (e.g., AWS EBS, NFS, Ceph)
│
├── StorageClass: gp2 (example)
│   ├── Provisioner: kubernetes.io/aws-ebs
│   └── Parameters: type=gp2, fsType=ext4
│
├── PersistentVolumeClaim: postgresql-data
│   ├── Size: 10Gi
│   ├── AccessMode: ReadWriteOnce
│   └── StorageClass: gp2
│
└── PersistentVolume (auto-provisioned)
    ├── Backed by cloud storage
    └── Mounted to PostgreSQL pod: /var/lib/postgresql/data
```

**Advantages:**
- Dynamic provisioning
- Cloud-native storage integration
- Automatic volume creation
- Backup and snapshot support
- Independent lifecycle from pods

## High Availability Architecture

### Keycloak Clustering (Kubernetes)

```
┌─────────────────────────────────────────────────────────────┐
│              Kubernetes Load Balancer                        │
│           (Ingress or Service LoadBalancer)                  │
└────────────────────────┬────────────────────────────────────┘
                         │
          ┌──────────────┴──────────────┐
          │                             │
┌─────────▼──────────┐       ┌──────────▼─────────┐
│   Keycloak Pod 0   │       │  Keycloak Pod 1    │
│                    │       │                    │
│  JGroups Member    │◄─────►│  JGroups Member    │
│  Cache: Infinispan │  UDP  │  Cache: Infinispan │
│  Sessions: Shared  │       │  Sessions: Shared  │
└────────────────────┘       └────────────────────┘
          │                             │
          └──────────────┬──────────────┘
                         │
                         ▼
              ┌──────────────────┐
              │   PostgreSQL     │
              │   (Single/HA)    │
              └──────────────────┘
```

**Clustering Features:**

1. **JGroups Configuration:**
   ```yaml
   -Djgroups.dns.query=keycloak-custom-headless.default.svc.cluster.local
   ```
   - DNS-based member discovery
   - No static IP configuration
   - Automatic cluster formation

2. **Infinispan Caching:**
   - Distributed cache across pods
   - Session replication
   - Invalidation messages

3. **Session Stickiness:**
   - Optional sticky sessions at load balancer
   - Or full session replication

4. **Database Consistency:**
   - Single source of truth
   - Optimistic locking
   - Transaction coordination

**Failure Scenarios:**

| Scenario | Behavior | Recovery |
|----------|----------|----------|
| Pod crash | K8s restarts pod, sessions lost or replicated | < 30 seconds |
| Database down | All pods fail health checks, K8s stops routing | Manual DB recovery |
| Network partition | Split-brain protection via database | Automatic reconciliation |
| Rolling update | Zero downtime with readiness probes | Gradual pod replacement |

## Configuration Management Strategy

### Environment-Specific Configuration

```
Configuration Hierarchy
│
├── Build-Time Configuration (baked into image)
│   ├── keycloak.conf (database vendor, features)
│   ├── Custom providers (JAR files)
│   ├── Custom themes
│   └── Setup scripts
│
├── Deployment-Time Configuration (Helm values)
│   ├── Replica count
│   ├── Resource limits
│   ├── Ingress rules
│   └── Service type
│
└── Runtime Configuration (ConfigMaps/Secrets)
    ├── Database connection string
    ├── Admin credentials
    ├── Feature flags
    └── Custom environment variables
```

### Configuration Precedence (Kubernetes)

1. **Helm Chart Values** (highest priority)
   - values.yaml overrides
   - `--set` flags during install

2. **Kubernetes ConfigMaps**
   - `keycloak-custom-config-vars`
   - Non-sensitive configuration

3. **Kubernetes Secrets**
   - `keycloak-custom-secret-vars`
   - Credentials and keys

4. **Container Environment**
   - Baked into Dockerfile
   - Build-time defaults

### Secrets Management

**Development (Docker Compose):**
- `.env` files (NOT committed to git)
- Plain text storage
- Manual management

**Production (Kubernetes):**
- Kubernetes Secrets (base64 encoded)
- External secret management (optional):
  - AWS Secrets Manager
  - HashiCorp Vault
  - Sealed Secrets

**Best Practices:**
- Rotate credentials regularly
- Use separate secrets per environment
- Limit RBAC access to secrets
- Enable encryption at rest

## Scaling Strategies

### Horizontal Scaling (Kubernetes)

**Manual Scaling:**
```bash
kubectl scale statefulset keycloak-custom --replicas=5
```

**Helm Configuration:**
```yaml
replicas: 3  # In values.yaml
```

**Horizontal Pod Autoscaler (HPA):**
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: keycloak-custom-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: StatefulSet
    name: keycloak-custom
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

**Scaling Considerations:**
- JGroups cluster reforming during scaling
- Database connection pool sizing
- Session replication overhead
- License considerations (if applicable)

### Vertical Scaling

**Resource Adjustment:**
```yaml
resources:
  requests:
    memory: "2Gi"
    cpu: "1000m"
  limits:
    memory: "4Gi"
    cpu: "2000m"
```

**JVM Heap Sizing:**
```yaml
extraEnv:
  - name: JAVA_OPTS_APPEND
    value: "-Xms2g -Xmx4g"
```

## Monitoring and Observability

### Metrics Endpoints

**Keycloak Metrics:**
- Endpoint: `/metrics`
- Format: Prometheus
- Enabled: `metrics-enabled=true`

**Metrics Categories:**
- JVM metrics (heap, GC, threads)
- HTTP requests (count, duration)
- Database connections
- Cache hit/miss ratios
- Session counts

### Health Checks

**Kubernetes Probes:**
```yaml
livenessProbe:
  httpGet:
    path: /health/live
    port: 8080
  initialDelaySeconds: 300
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /health/ready
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 5
```

**Health Endpoint:**
- URL: `/health`
- Components checked:
  - Database connectivity
  - Infinispan cache
  - JGroups cluster

### Logging Architecture

**Log Sources:**
1. Keycloak application logs
2. PostgreSQL logs
3. Kubernetes events
4. Ingress access logs

**Log Aggregation (Recommended):**
```
Pods → Fluent Bit → Elasticsearch → Kibana
  or
Pods → Promtail → Loki → Grafana
```

**Log Levels:**
```bash
KC_LOG_LEVEL=info,org.hibernate.SQL:debug
```

## Disaster Recovery and Backup

### Backup Strategy

**Database Backup:**
```bash
# PostgreSQL dump
kubectl exec postgresql-0 -- pg_dump -U keycloak keycloak > backup.sql

# Restore
kubectl exec -i postgresql-0 -- psql -U keycloak keycloak < backup.sql
```

**Configuration Backup:**
- Helm values files (version controlled)
- ConfigMaps and Secrets (exported YAML)
- Custom providers and themes (git repository)

**Automated Backup (Kubernetes):**
- Velero for cluster-level backup
- PV snapshots (cloud provider)
- Scheduled CronJobs for database dumps

### Recovery Procedures

**Pod Failure:** Automatic (Kubernetes restarts)
**Database Failure:** Restore from backup + PV snapshot
**Cluster Failure:** Rebuild cluster + Restore from Velero backup
**Data Corruption:** Point-in-time recovery from database backup

**RTO/RPO Targets:**
- RTO (Recovery Time Objective): < 1 hour
- RPO (Recovery Point Objective): < 15 minutes (with frequent backups)

## Security Architecture

### Network Security

**Kubernetes NetworkPolicies (Example):**
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: keycloak-netpol
spec:
  podSelector:
    matchLabels:
      app: keycloak-custom
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: ingress-nginx
    ports:
    - protocol: TCP
      port: 8080
  egress:
  - to:
    - podSelector:
        matchLabels:
          app: postgresql
    ports:
    - protocol: TCP
      port: 5432
```

**TLS Configuration:**
- Ingress TLS termination
- Internal HTTP (encrypted by CNI)
- Certificate management (cert-manager)

### Pod Security

**Security Context:**
```yaml
securityContext:
  runAsNonRoot: true
  runAsUser: 1000
  fsGroup: 1000
  allowPrivilegeEscalation: false
  capabilities:
    drop: ["ALL"]
  seccompProfile:
    type: RuntimeDefault
```

**PodSecurityPolicy/PodSecurityStandards:**
- Restricted profile enforcement
- No privileged containers
- Non-root user requirement

### Access Control

**RBAC for Kubernetes:**
```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: keycloak-custom

---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: keycloak-custom-role
rules:
- apiGroups: [""]
  resources: ["configmaps", "secrets"]
  verbs: ["get", "list"]
```

## Summary

The infrastructure architecture provides:

1. **Flexibility:** Multiple deployment topologies for different environments
2. **Scalability:** Horizontal scaling with Kubernetes StatefulSets
3. **High Availability:** Multi-replica deployments with JGroups clustering
4. **Automation:** CI/CD pipeline with GitHub Actions
5. **Security:** Network policies, pod security contexts, secrets management
6. **Observability:** Metrics, logging, and health checks
7. **Disaster Recovery:** Backup strategies and recovery procedures
8. **Configuration Management:** Environment-specific configuration with Helm

The architecture balances developer productivity (Docker Compose) with production readiness (Kubernetes) while maintaining consistent configuration and deployment practices across all environments.
