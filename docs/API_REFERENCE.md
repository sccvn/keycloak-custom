# Keycloak Custom - API & Configuration Reference

## API Endpoints

### Authentication & Authorization

#### OIDC Discovery
```
GET /auth/realms/{realm}/.well-known/openid-configuration
```

**Response**: OpenID Connect Discovery metadata

```json
{
  "issuer": "http://localhost:8080/auth/realms/master",
  "authorization_endpoint": "http://localhost:8080/auth/realms/master/protocol/openid-connect/auth",
  "token_endpoint": "http://localhost:8080/auth/realms/master/protocol/openid-connect/token",
  "userinfo_endpoint": "http://localhost:8080/auth/realms/master/protocol/openid-connect/userinfo",
  "jwks_uri": "http://localhost:8080/auth/realms/master/protocol/openid-connect/certs",
  "token_endpoint_auth_methods_supported": ["private_key_jwt", "client_secret_basic", "client_secret_post"],
  "response_types_supported": ["code", "id_token", "token id_token"],
  "grant_types_supported": ["authorization_code", "implicit", "refresh_token", "password", "client_credentials"]
}
```

---

#### Authorization Code Flow
```
GET /auth/realms/{realm}/protocol/openid-connect/auth
  ?client_id={clientId}
  &response_type=code
  &redirect_uri={redirectUri}
  &scope={scopes}
  &state={state}
  &nonce={nonce}
```

**Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `client_id` | String | Yes | OIDC client identifier |
| `response_type` | String | Yes | Must be `code` |
| `redirect_uri` | URI | Yes | Client callback URI |
| `scope` | String | Yes | Space-separated scopes (openid, profile, email) |
| `state` | String | Yes | CSRF token (client-generated) |
| `nonce` | String | Optional | Prevent token replay |

**Response**:
```
302 Found
Location: {redirectUri}?code={authCode}&state={state}
```

---

#### Token Exchange
```
POST /auth/realms/{realm}/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code={authCode}
&client_id={clientId}
&client_secret={clientSecret}
&redirect_uri={redirectUri}
```

**Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `grant_type` | String | Yes | Must be `authorization_code` |
| `code` | String | Yes | Authorization code from auth endpoint |
| `client_id` | String | Yes | OIDC client ID |
| `client_secret` | String | Yes | Client secret (public clients omit) |
| `redirect_uri` | URI | Yes | Must match original request |

**Response** (200 OK):
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI...",
  "expires_in": 300,
  "refresh_expires_in": 1800,
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI...",
  "token_type": "Bearer",
  "id_token": "eyJhbGciOiJSUzI1NiIsInR5cCI...",
  "not-before-policy": 0,
  "session_state": "f2d4a3e1-c29e-4c7d-a8f5-2b1e9c3d6a4f",
  "scope": "openid profile email"
}
```

---

#### Refresh Token
```
POST /auth/realms/{realm}/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token
&refresh_token={refreshToken}
&client_id={clientId}
&client_secret={clientSecret}
```

**Response**: Same as Token Exchange response

---

#### Token Introspection
```
POST /auth/realms/{realm}/protocol/openid-connect/token/introspect
Content-Type: application/x-www-form-urlencoded
Authorization: Basic {clientId}:{clientSecret}

token={accessToken}
```

**Response** (200 OK):
```json
{
  "active": true,
  "exp": 1640995234,
  "iat": 1640994934,
  "auth_time": 1640994934,
  "jti": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "iss": "http://localhost:8080/auth/realms/master",
  "sub": "f2d4a3e1-c29e-4c7d-a8f5-2b1e9c3d6a4f",
  "typ": "Bearer",
  "azp": "my-client",
  "session_state": "f2d4a3e1-c29e-4c7d-a8f5-2b1e9c3d6a4f",
  "acr": "1",
  "realm_access": {
    "roles": ["user", "admin"]
  },
  "resource_access": {
    "my-client": {
      "roles": ["manage-account", "manage-account-links"]
    }
  }
}
```

---

#### UserInfo Endpoint
```
GET /auth/realms/{realm}/protocol/openid-connect/userinfo
Authorization: Bearer {accessToken}
```

**Response** (200 OK):
```json
{
  "sub": "f2d4a3e1-c29e-4c7d-a8f5-2b1e9c3d6a4f",
  "email_verified": true,
  "name": "John Doe",
  "preferred_username": "john.doe",
  "given_name": "John",
  "family_name": "Doe",
  "email": "john.doe@example.com",
  "realm_access": {
    "roles": ["user", "admin"]
  }
}
```

---

#### Token Revocation
```
POST /auth/realms/{realm}/protocol/openid-connect/revoke
Content-Type: application/x-www-form-urlencoded
Authorization: Basic {clientId}:{clientSecret}

token={refreshToken}
&token_type_hint=refresh_token
```

**Response**: 204 No Content

---

### Admin APIs

#### Get Realm
```
GET /admin/realms/{realm}
Authorization: Bearer {adminToken}
```

**Response** (200 OK):
```json
{
  "id": "f2d4a3e1-c29e-4c7d-a8f5-2b1e9c3d6a4f",
  "realm": "master",
  "notBefore": 0,
  "revokeRefreshToken": false,
  "refreshTokenMaxReuse": 0,
  "accessTokenLifespan": 300,
  "accessTokenLifespanForImplicitFlow": 900,
  "ssoSessionIdleTimeout": 1800,
  "enabled": true,
  "sslRequired": "external",
  "passwordPolicy": "length(8) and forceExpiredPasswordChange(365)"
}
```

---

#### List Clients
```
GET /admin/realms/{realm}/clients
Authorization: Bearer {adminToken}
```

**Response** (200 OK):
```json
[
  {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "clientId": "my-client",
    "name": "My Client Application",
    "enabled": true,
    "publicClient": false,
    "redirectUris": ["http://localhost:3000/callback"],
    "webOrigins": ["http://localhost:3000"]
  }
]
```

---

#### Create Client
```
POST /admin/realms/{realm}/clients
Authorization: Bearer {adminToken}
Content-Type: application/json

{
  "clientId": "my-new-client",
  "name": "My New Client",
  "description": "A new OIDC client",
  "enabled": true,
  "publicClient": false,
  "redirectUris": [
    "http://localhost:3000/callback",
    "http://localhost:3000/logout"
  ],
  "webOrigins": ["http://localhost:3000"],
  "standardFlowEnabled": true,
  "implicitFlowEnabled": false,
  "directAccessGrantsEnabled": false,
  "serviceAccountsEnabled": false,
  "authorizationServicesEnabled": false
}
```

**Response**: 201 Created
```
Location: /admin/realms/master/clients/{clientId}
```

---

#### List Users
```
GET /admin/realms/{realm}/users
Authorization: Bearer {adminToken}
?username={username}
&email={email}
&first={first}
&max={max}
```

**Response** (200 OK):
```json
[
  {
    "id": "f2d4a3e1-c29e-4c7d-a8f5-2b1e9c3d6a4f",
    "username": "john.doe",
    "email": "john.doe@example.com",
    "emailVerified": true,
    "firstName": "John",
    "lastName": "Doe",
    "enabled": true,
    "createdTimestamp": 1640994934000,
    "attributes": {}
  }
]
```

---

#### Create User
```
POST /admin/realms/{realm}/users
Authorization: Bearer {adminToken}
Content-Type: application/json

{
  "username": "jane.doe",
  "email": "jane.doe@example.com",
  "emailVerified": true,
  "firstName": "Jane",
  "lastName": "Doe",
  "enabled": true,
  "credentials": [
    {
      "type": "password",
      "value": "TemporaryPassword123!",
      "temporary": true
    }
  ],
  "requiredActions": ["UPDATE_PASSWORD"]
}
```

**Response**: 201 Created
```
Location: /admin/realms/master/users/{userId}
```

---

#### Update User
```
PUT /admin/realms/{realm}/users/{userId}
Authorization: Bearer {adminToken}
Content-Type: application/json

{
  "email": "jane.newemail@example.com",
  "emailVerified": true,
  "firstName": "Jane",
  "lastName": "Smith"
}
```

**Response**: 204 No Content

---

#### Set User Password
```
PUT /admin/realms/{realm}/users/{userId}/reset-password
Authorization: Bearer {adminToken}
Content-Type: application/json

{
  "type": "password",
  "value": "NewPassword123!",
  "temporary": false
}
```

**Response**: 204 No Content

---

#### List User Roles
```
GET /admin/realms/{realm}/users/{userId}/role-mappings
Authorization: Bearer {adminToken}
```

**Response** (200 OK):
```json
{
  "realmMappings": [
    {
      "id": "role-id-1",
      "name": "admin",
      "composite": false
    },
    {
      "id": "role-id-2",
      "name": "user",
      "composite": false
    }
  ],
  "clientMappings": {
    "my-client": {
      "mappings": [
        {
          "id": "client-role-1",
          "name": "manage-account",
          "composite": false
        }
      ]
    }
  }
}
```

---

#### Assign User Role
```
POST /admin/realms/{realm}/users/{userId}/role-mappings/realm
Authorization: Bearer {adminToken}
Content-Type: application/json

[
  {
    "id": "role-id-1",
    "name": "admin",
    "composite": false
  }
]
```

**Response**: 204 No Content

---

### Metrics & Health

#### Prometheus Metrics
```
GET /metrics
```

**Response**: Prometheus format metrics

```
# HELP jvm_memory_used_bytes The amount of used memory
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap",id="G1 Old Gen",} 1.073741824E9

# HELP jvm_threads_count The current number of threads
# TYPE jvm_threads_count gauge
jvm_threads_count 42

# HELP http_server_requests_seconds_sum
# TYPE http_server_requests_seconds_sum counter
http_server_requests_seconds_sum{method="GET",status="200",uri="/auth/realms/{realm}/.well-known/openid-configuration"} 0.123
```

---

#### Health - Liveness Probe
```
GET /health/live
```

**Response** (200 OK):
```json
{
  "status": "UP"
}
```

---

#### Health - Readiness Probe
```
GET /health/ready
```

**Response** (200 OK):
```json
{
  "status": "UP",
  "checks": [
    {
      "name": "Database connection",
      "status": "UP"
    }
  ]
}
```

---

#### Health - Startup Probe
```
GET /health/startup
```

**Response** (200 OK):
```json
{
  "status": "UP"
}
```

---

## Configuration Reference

### Environment Variables

#### Database Configuration
| Variable | Default | Description |
|----------|---------|-------------|
| `KC_DB` | `postgres` | Database type |
| `KC_DB_URL` | `jdbc:postgresql://localhost:5432/keycloak` | JDBC connection URL |
| `KC_DB_USERNAME` | `keycloak` | Database user |
| `KC_DB_PASSWORD` | `keycloak` | Database password |

#### HTTP/HTTPS Configuration
| Variable | Default | Description |
|----------|---------|-------------|
| `KC_HTTP_ENABLED` | `false` | Enable HTTP (dev only) |
| `KC_HTTPS_CERTIFICATE_FILE` | - | TLS certificate path |
| `KC_HTTPS_CERTIFICATE_KEY_FILE` | - | TLS private key path |
| `KC_HTTPS_PORT` | `8443` | HTTPS port |

#### Hostname Configuration
| Variable | Default | Description |
|----------|---------|-------------|
| `KC_HOSTNAME` | `localhost` | External hostname |
| `KC_HOSTNAME_STRICT` | `true` | Require hostname match |
| `KC_HOSTNAME_STRICT_HTTPS` | `true` | Require HTTPS for hostname |

#### Cache Configuration
| Variable | Default | Description |
|----------|---------|-------------|
| `KC_CACHE` | `ispn` | Cache type (ispn/local) |
| `KC_CACHE_CONFIG_FILE` | - | Custom cache config |

#### Theme Configuration
| Variable | Default | Description |
|----------|---------|-------------|
| `KC_THEME_CACHE_THEMES` | `false` | Cache theme templates |
| `KC_THEME_CACHE_TEMPLATES` | `false` | Cache FreeMarker templates |
| `KC_THEME_DEFAULT` | `keycloak` | Default theme name |

#### Logging Configuration
| Variable | Default | Description |
|----------|---------|-------------|
| `KC_LOG` | `default` | Log format (default/json) |
| `KC_LOG_LEVEL` | `INFO` | Logging level |
| `KC_LOG_CONSOLE_OUTPUT` | `default` | Console output format |

#### Additional Configuration
| Variable | Default | Description |
|----------|---------|-------------|
| `KC_ADMIN_USERNAME` | `admin` | Admin username (first run) |
| `KC_ADMIN_PASSWORD` | `admin` | Admin password (first run) |
| `KC_HEALTH_ENABLED` | `true` | Enable health endpoints |
| `KC_METRICS_ENABLED` | `true` | Enable metrics endpoint |

---

### Configuration File (keycloak.conf)

```properties
# Database
db=postgres
db-url=jdbc:postgresql://postgres:5432/keycloak
db-username=keycloak
db-password=keycloak

# HTTP/HTTPS
http-enabled=false
https-port=8443
https-certificate-file=/etc/keycloak/certs/tls.crt
https-certificate-key-file=/etc/keycloak/certs/tls.key

# Hostname
hostname=keycloak.example.com
hostname-strict=true
hostname-strict-https=true

# Cache
cache=ispn

# Themes
theme-cache-themes=false
theme-cache-templates=false

# Logging
log=default
log-level=INFO

# Health & Metrics
health-enabled=true
metrics-enabled=true

# Admin
admin-username=admin
admin-password=${KC_ADMIN_PASSWORD}
```

---

### Realm Configuration (realm.json)

```json
{
  "realm": "master",
  "enabled": true,
  "displayName": "Master Realm",
  "users": [
    {
      "username": "user",
      "email": "user@example.com",
      "emailVerified": true,
      "enabled": true,
      "firstName": "User",
      "lastName": "Account",
      "credentials": [
        {
          "type": "password",
          "value": "password123",
          "temporary": false
        }
      ]
    }
  ],
  "roles": {
    "realm": [
      {
        "name": "admin",
        "description": "Administrator role"
      },
      {
        "name": "user",
        "description": "User role"
      }
    ]
  },
  "clients": [
    {
      "clientId": "my-app",
      "name": "My Application",
      "enabled": true,
      "publicClient": false,
      "redirectUris": ["http://localhost:3000/callback"],
      "webOrigins": ["http://localhost:3000"],
      "standardFlowEnabled": true,
      "implicitFlowEnabled": false,
      "directAccessGrantsEnabled": false,
      "clientAuthenticationType": "client-secret-basic",
      "protocolMappers": [
        {
          "name": "email",
          "protocol": "openid-connect",
          "protocolMapper": "oidc-usermodel-property-mapper",
          "config": {
            "claim.name": "email",
            "user.attribute": "email"
          }
        }
      ]
    }
  ]
}
```

---

## Error Responses

### Invalid Request
```
400 Bad Request

{
  "error": "invalid_request",
  "error_description": "Missing required parameter: client_id"
}
```

---

### Invalid Client
```
401 Unauthorized

{
  "error": "invalid_client",
  "error_description": "Client authentication failed"
}
```

---

### Invalid Grant
```
400 Bad Request

{
  "error": "invalid_grant",
  "error_description": "Authorization code is invalid or expired"
}
```

---

### Server Error
```
500 Internal Server Error

{
  "error": "server_error",
  "error_description": "An unexpected error occurred"
}
```

---

## Rate Limiting

**Status**: Not implemented (configure at load balancer)

**Recommended Limits**:
- Token endpoint: 100 requests/min per IP
- Auth endpoint: 50 requests/min per IP
- Login attempts: 5 failures before temporary lockout

---

## Security Headers

Keycloak automatically sets:
```
Strict-Transport-Security: max-age=31536000; includeSubDomains
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Content-Security-Policy: default-src 'self'
```

---

## Protocol Mapper Configuration (NoOperationProtocolMapper)

### Configuration Example
```json
{
  "id": "protocol-mapper-1",
  "name": "custom-claim",
  "protocol": "openid-connect",
  "protocolMapper": "oidc-custom-claim-mapper",
  "consentRequired": false,
  "config": {
    "claim.name": "custom_claim",
    "claim.value": "custom_value",
    "token.claim": true,
    "id.token.claim": true,
    "access.token.claim": false,
    "userinfo.token.claim": false
  }
}
```

**Options**:
- `claim.name`: Name of the claim in the token
- `claim.value`: Value to inject (supports user attributes)
- `token.claim`: Include in tokens
- `id.token.claim`: Include in ID token
- `access.token.claim`: Include in access token
- `userinfo.token.claim`: Include in userinfo response

---

## Custom Authenticator Configuration

### NoOperationAuthenticator
**Status**: Pass-through (no configuration required)

### NoOperationFormAuthenticator
**Status**: Form-based auth with custom template

**Template**: `login.ftl`
**Location**: `inventage/login/login.ftl`

---

## Troubleshooting API Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| `invalid_client` | Wrong client secret | Verify client credentials |
| `invalid_grant` | Expired code | Request new authorization code |
| `redirect_uri_mismatch` | URI not registered | Add URI to client redirectUris |
| `unauthorized_client` | Grant type not enabled | Enable grant type in client |
| `token_expired` | Access token expired | Use refresh token |
| `invalid_scope` | Scope not available | Request available scopes |

---

## References

- [Keycloak Admin REST API](https://www.keycloak.org/docs/latest/server_development/index.html#_admin_rest_api)
- [OpenID Connect Specification](https://openid.net/specs/openid-connect-core-1_0.html)
- [OAuth 2.0 RFC 6749](https://tools.ietf.org/html/rfc6749)
