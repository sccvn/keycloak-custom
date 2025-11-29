# Protocol Mapper Extension - Deep Code Analysis

## Overview
The protocol mapper extension demonstrates how to customize OIDC token claims in Keycloak authentication responses.

## Extension: NoOperationProtocolMapper

### Location
`extensions/extension-no-op-protocol-mapper/src/main/java/com/inventage/keycloak/noopformauthenticator/infrastructure/protocolmapper/`

### Architecture Pattern: Token Claim Mapper

#### Core Class Analysis

**NoOperationProtocolMapper.java**

### Class Declaration
```java
public class NoOperationProtocolMapper extends AbstractOIDCProtocolMapper
    implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper
```

**Pattern Analysis:**
- **Inheritance**: Extends `AbstractOIDCProtocolMapper` base class
- **Multiple Interfaces**: Implements three token mapper interfaces
- **Purpose**: Single mapper can add claims to all OIDC token types

### Token Mapper Interfaces

**1. OIDCAccessTokenMapper**
- **Purpose**: Adds claims to OAuth 2.0 access tokens
- **Use Case**: API authorization, resource server validation
- **Lifetime**: Short-lived (5-30 minutes typical)

**2. OIDCIDTokenMapper**
- **Purpose**: Adds claims to OpenID Connect ID tokens
- **Use Case**: User authentication, client-side user info
- **Lifetime**: Short-lived (5 minutes typical)
- **Audience**: Client application

**3. UserInfoTokenMapper**
- **Purpose**: Adds claims to UserInfo endpoint response
- **Use Case**: Additional user profile information
- **Access**: Via `/userinfo` endpoint
- **Standard**: OpenID Connect UserInfo specification

### Configuration and Metadata

```java
public static final String PROVIDER_ID = "no-operation-protocol-mapper";

private static final List<ProviderConfigProperty> CONFIG_PROPERTIES = new ArrayList<>();

static {
    OIDCAttributeMapperHelper.addIncludeInTokensConfig(CONFIG_PROPERTIES,
        NoOperationProtocolMapper.class);
}
```

**Static Initialization Analysis:**
- **Pattern**: Static configuration block
- **Purpose**: Initialize configuration properties at class load
- **Helper Usage**: `OIDCAttributeMapperHelper` provides standard config options
- **Config Type**: "Include in tokens" configuration

**Include in Tokens Configuration:**
This adds checkboxes in Keycloak admin UI to control where claim appears:
- [ ] Access Token
- [ ] ID Token
- [ ] UserInfo

**Configuration Properties Provided:**
1. **includeInAccessToken**: Boolean, default true
2. **includeInIdToken**: Boolean, default true
3. **includeInUserInfo**: Boolean, default true

### Metadata Methods

```java
@Override
public String getDisplayCategory() {
    return TOKEN_MAPPER_CATEGORY;
}
```
- **Value**: `TOKEN_MAPPER_CATEGORY` = "Token mapper"
- **UI**: Appears in this category in Keycloak admin console
- **Grouping**: Groups with other token mappers

```java
@Override
public String getDisplayType() {
    return "No Operation Protocol Mapper";
}
```
- **Purpose**: User-friendly name in admin UI
- **Visibility**: Shown in mapper selection dropdown

```java
@Override
public String getHelpText() {
    return "No Operation Protocol Mapper";
}
```
- **Note**: Could be more descriptive
- **Better**: "Adds a static 'claimName' claim with value 'claimValue' to tokens"

```java
@Override
public String getId() {
    return PROVIDER_ID;
}
```
- **Purpose**: Unique identifier for this mapper type
- **Usage**: Internal Keycloak registry, configuration references

### Core Functionality

```java
@Override
protected void setClaim(IDToken token,
                       ProtocolMapperModel mappingModel,
                       UserSessionModel userSession,
                       KeycloakSession keycloakSession,
                       ClientSessionContext clientSessionCtx) {
    LOG.debugf("setClaim:");
    token.getOtherClaims().put("claimName", "claimValue");
}
```

**Method Analysis:**

**Parameters:**
1. **IDToken token**: The token being constructed (can be ID, Access, or UserInfo)
2. **ProtocolMapperModel mappingModel**: Configuration for this mapper instance
3. **UserSessionModel userSession**: Current user's session data
4. **KeycloakSession keycloakSession**: Global session with service access
5. **ClientSessionContext clientSessionCtx**: Client-specific session context

**Implementation:**
```java
token.getOtherClaims().put("claimName", "claimValue");
```
- **Method**: Adds claim to "other claims" section
- **Static Values**: Both key and value are hardcoded
- **Purpose**: Demonstration only - production would use dynamic values

**Token Structure Impact:**
```json
{
  "iss": "https://keycloak.example.com/realms/myrealm",
  "sub": "user-id-123",
  "aud": "my-client",
  "exp": 1234567890,
  "iat": 1234567800,
  "claimName": "claimValue"  // <-- Added by this mapper
}
```

### Design Patterns Identified

#### 1. Template Method Pattern
- **Base Class**: `AbstractOIDCProtocolMapper` defines workflow
- **Override**: `setClaim()` fills in custom claim logic
- **Benefit**: Framework handles token type routing, mapper focuses on claim logic

#### 2. Interface Segregation
- **Separate Interfaces**: Each token type has distinct interface
- **Flexibility**: Mapper can choose which tokens to support
- **Clarity**: Explicit declaration of capabilities

#### 3. Builder Pattern (Framework)
- **Token Construction**: Keycloak builds tokens incrementally
- **Mappers**: Each mapper adds its claims
- **Final Product**: Complete token with all configured claims

#### 4. Static Configuration
- **Pattern**: Configuration loaded once at startup
- **Performance**: No repeated initialization
- **Immutability**: Config properties don't change at runtime

### Framework Integration

#### Keycloak Protocol Mapper SPI

**Registration:**
```
META-INF/services/org.keycloak.protocol.ProtocolMapper
```
**Content:**
```
com.inventage.keycloak.noopformauthenticator.infrastructure.protocolmapper.NoOperationProtocolMapper
```

**Discovery Process:**
1. Keycloak scans classpath for service files
2. Loads mapper class
3. Instantiates mapper
4. Reads metadata (display name, category, etc.)
5. Adds to available protocol mappers registry

#### AbstractOIDCProtocolMapper Hierarchy

```
AbstractOIDCProtocolMapper (Keycloak framework)
└── NoOperationProtocolMapper (Custom implementation)
```

**Inherited Functionality:**
- Token type routing (ID, Access, UserInfo)
- Configuration handling
- Standard claim name validation
- Include in tokens logic

**Custom Implementation:**
- Claim value generation
- Claim key selection
- Custom business logic

### OAuth 2.0 / OIDC Context

#### Token Types Explained

**1. ID Token (OIDC)**
- **Purpose**: Proves user authentication
- **Format**: JWT (JSON Web Token)
- **Signature**: Always signed by Keycloak
- **Audience**: Client application
- **Claims**: User identity, authentication metadata

**2. Access Token (OAuth 2.0)**
- **Purpose**: Authorizes resource access
- **Format**: JWT or opaque token
- **Signature**: Signed for JWT format
- **Audience**: Resource servers (APIs)
- **Claims**: Authorization scopes, permissions

**3. UserInfo Response (OIDC)**
- **Purpose**: Additional user profile data
- **Format**: JSON object (not JWT)
- **Signature**: Not signed (HTTPS provides security)
- **Audience**: Client application
- **Claims**: Extended user attributes

### Practical Use Cases

While this example is minimal, production protocol mappers commonly:

**1. User Attribute Mapping**
```java
String department = userSession.getUser().getFirstAttribute("department");
token.getOtherClaims().put("department", department);
```

**2. Role Mapping**
```java
Set<String> roles = userSession.getRoles();
token.getOtherClaims().put("roles", new ArrayList<>(roles));
```

**3. Group Membership**
```java
Set<String> groups = userSession.getUser().getGroupsStream()
    .map(GroupModel::getName)
    .collect(Collectors.toSet());
token.getOtherClaims().put("groups", groups);
```

**4. Custom Business Logic**
```java
// Calculate permissions based on user attributes
List<String> permissions = calculatePermissions(userSession, keycloakSession);
token.getOtherClaims().put("permissions", permissions);
```

**5. External System Integration**
```java
// Fetch data from external system
String externalId = fetchExternalId(userSession.getUser(), keycloakSession);
token.getOtherClaims().put("external_system_id", externalId);
```

**6. Conditional Claims**
```java
// Add claim only for specific clients
String clientId = clientSessionCtx.getClientSession().getClient().getClientId();
if ("special-client".equals(clientId)) {
    token.getOtherClaims().put("premium_feature", true);
}
```

### Configuration in Keycloak Admin UI

When this mapper is configured, admins can:

**1. Assign to Client**
- Navigate to Client → Client Scopes → Dedicated scope
- Add Mapper → Select "No Operation Protocol Mapper"

**2. Configure Options**
- [x] Include in Access Token
- [x] Include in ID Token
- [x] Include in UserInfo

**3. Scope Assignment**
- Assign mapper to default scope (always included)
- Or assign to optional scope (requested by client)

### Maven Configuration

```xml
<dependencies>
    <dependency>
        <groupId>org.keycloak</groupId>
        <artifactId>keycloak-core</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>org.keycloak</groupId>
        <artifactId>keycloak-server-spi</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>org.keycloak</groupId>
        <artifactId>keycloak-server-spi-private</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>org.keycloak</groupId>
        <artifactId>keycloak-services</artifactId>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

**Analysis:**
- **Minimal Dependencies**: Only Keycloak framework dependencies
- **Provided Scope**: All dependencies provided by server
- **No AutoService**: Unlike authenticator, no annotation processing
- **Manual SPI**: Uses manual service file registration

**Comparison with Authenticator POM:**
- **Missing**: `auto-service-annotations` dependency
- **Simpler**: Fewer dependencies needed
- **Same Pattern**: Provided scope, antrun deployment plugin

### Deployment Process

**Build:**
```bash
mvn clean package
```

**Artifact:**
```
target/extension-no-op-protocol-mapper-1.0.0-SNAPSHOT.jar
```

**Deployment:**
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-antrun-plugin</artifactId>
    <execution>
        <target>
            <copy file="${file.to.deploy}"
                  todir="${keycloak.providers.dir}"/>
        </target>
    </execution>
</plugin>
```

**Target Location:**
```
../../server/target/keycloak/providers/extension-no-op-protocol-mapper-1.0.0-SNAPSHOT.jar
```

**Keycloak Discovery:**
1. Server starts
2. Scans `providers/` directory
3. Finds JAR with `META-INF/services/org.keycloak.protocol.ProtocolMapper`
4. Loads `NoOperationProtocolMapper` class
5. Registers in protocol mapper registry
6. Available in admin UI for configuration

### Code Quality Assessment

**Strengths:**
1. **Clear Purpose**: Simple, focused implementation
2. **Logging**: Debug logging for troubleshooting
3. **Standard Compliance**: Follows OIDC specifications
4. **Proper Interfaces**: Implements all necessary token mapper interfaces
5. **Configuration**: Uses standard Keycloak configuration helpers

**Potential Improvements:**
1. **Hardcoded Values**: Should use configuration properties
2. **No Validation**: No null checks or validation
3. **Error Handling**: No try-catch for exceptions
4. **Help Text**: Could be more descriptive
5. **Documentation**: Missing JavaDoc comments
6. **Dynamic Claims**: Should demonstrate configurable claim names/values
7. **Testing**: No unit tests present

**Enhanced Version Example:**
```java
@Override
protected void setClaim(IDToken token,
                       ProtocolMapperModel mappingModel,
                       UserSessionModel userSession,
                       KeycloakSession keycloakSession,
                       ClientSessionContext clientSessionCtx) {
    // Get configured claim name and value
    String claimName = mappingModel.getConfig().get("claim.name");
    String claimValue = mappingModel.getConfig().get("claim.value");

    if (claimName != null && !claimName.isEmpty()) {
        // Could fetch from user attributes, external system, etc.
        Object computedValue = computeClaimValue(claimValue, userSession);
        token.getOtherClaims().put(claimName, computedValue);
    }
}
```

### Security Considerations

**1. Claim Injection**
- **Risk**: Hardcoded values could be exploited if not validated
- **Mitigation**: Validate and sanitize all claim values
- **Best Practice**: Use configuration for claim keys/values

**2. Information Disclosure**
- **Risk**: Adding sensitive data to tokens
- **Mitigation**: Carefully consider what claims are necessary
- **Best Practice**: Minimize PII in tokens

**3. Token Size**
- **Risk**: Large claims increase token size
- **Impact**: Performance, URL length limits
- **Best Practice**: Keep claims minimal, use references

**4. Claim Name Conflicts**
- **Risk**: Overwriting standard claims
- **Mitigation**: Use namespaced claim names
- **Example**: `custom_claim_name` or `org.example.claim`

### Integration Points

**Keycloak Services:**
- `KeycloakSession`: Access to services, providers
- `UserSessionModel`: Current user session data
- `ClientSessionContext`: Client-specific context
- `ProtocolMapperModel`: This mapper's configuration

**Token Construction:**
- `IDToken.getOtherClaims()`: Custom claims map
- Framework handles serialization to JWT
- Signature and encryption handled by framework

**Standard Claims** (should not override):
- `iss` (issuer)
- `sub` (subject)
- `aud` (audience)
- `exp` (expiration)
- `iat` (issued at)
- `auth_time` (authentication time)
- `nonce` (OIDC nonce)

## Conclusion

The NoOperationProtocolMapper demonstrates:

**Core Concepts:**
- OIDC token claim customization
- Multi-token-type support (ID, Access, UserInfo)
- Keycloak protocol mapper SPI
- Configuration integration

**Learning Value:**
- Shows minimal protocol mapper structure
- Demonstrates token claim injection
- Provides template for custom mappers

**Production Readiness:**
- Currently a demonstration/learning tool
- Needs dynamic configuration
- Requires validation and error handling
- Should include comprehensive testing

**Extension Opportunities:**
- User attribute mapping
- Role/group transformation
- External system integration
- Complex claim calculations
- Conditional claim inclusion
