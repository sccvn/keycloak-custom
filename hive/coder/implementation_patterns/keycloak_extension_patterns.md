# Keycloak Extension Implementation Patterns

## Pattern Catalog

This document catalogs all design patterns, architectural decisions, and implementation strategies found in the Keycloak custom extensions.

## 1. Service Provider Interface (SPI) Pattern

### Context
Keycloak uses Java SPI mechanism for plugin discovery and registration.

### Implementation Variants

#### Variant A: Annotation-Based Registration
```java
@AutoService(org.keycloak.authentication.AuthenticatorFactory.class)
public class NoOperationAuthenticatorFactory implements AuthenticatorFactory {
    // Implementation
}
```

**Mechanism:**
- Google AutoService annotation processor
- Auto-generates `META-INF/services/` files at compile time
- Requires `auto-service-annotations` dependency

**Maven Configuration:**
```xml
<dependency>
    <groupId>com.google.auto.service</groupId>
    <artifactId>auto-service-annotations</artifactId>
</dependency>

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>com.google.auto.service</groupId>
                <artifactId>auto-service</artifactId>
                <version>${auto-service.version}</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

**Pros:**
- Less manual work
- Prevents typos in service files
- Type-safe registration
- Automatic maintenance

**Cons:**
- Additional dependency
- Build-time processing required
- Less visible to developers

#### Variant B: Manual Service File Registration
```
File: META-INF/services/org.keycloak.protocol.ProtocolMapper
Content: com.inventage.keycloak.noopformauthenticator.infrastructure.protocolmapper.NoOperationProtocolMapper
```

**Pros:**
- No additional dependencies
- Explicit and visible
- Simple to understand
- No annotation processing

**Cons:**
- Manual maintenance
- Prone to typos
- Must keep file in sync with code
- Refactoring requires file updates

### Usage in Project
- **Authenticator**: Uses `@AutoService` annotation
- **Protocol Mapper**: Uses manual service file
- **Theme Provider**: Uses manual service file

**Pattern Recommendation:** Use `@AutoService` for consistency and maintainability.

## 2. Factory Pattern

### Context
Keycloak uses factory pattern to create provider instances with controlled lifecycle.

### Implementation

```java
public class NoOperationAuthenticatorFactory implements AuthenticatorFactory {

    private static final String PROVIDER_ID = "no-operation-authenticator";

    @Override
    public Authenticator create(KeycloakSession keycloakSession) {
        return new NoOperationAuthenticator();
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public void init(Config.Scope scope) {
        // Initialization logic
    }

    @Override
    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {
        // Post-initialization logic
    }

    @Override
    public void close() {
        // Cleanup logic
    }
}
```

### Lifecycle Phases

**1. init(Config.Scope scope)**
- **Phase**: Server startup, before realm loading
- **Purpose**: Initialize configuration
- **Usage**: Read config files, set up static resources
- **Scope**: Contains configuration from standalone.xml or environment

**2. postInit(KeycloakSessionFactory sessionFactory)**
- **Phase**: After all providers initialized
- **Purpose**: Cross-provider initialization
- **Usage**: Register event listeners, establish provider dependencies
- **Factory Access**: Can access other providers via factory

**3. create(KeycloakSession session)**
- **Phase**: Per request
- **Purpose**: Create provider instance for request
- **Usage**: Create stateless or request-scoped instances
- **Session Access**: Can access session-scoped services

**4. close()**
- **Phase**: Server shutdown
- **Purpose**: Cleanup resources
- **Usage**: Close connections, flush caches, release resources

### Metadata Methods

```java
@Override
public String getDisplayType() {
    return "No Operation Authenticator";
}

@Override
public String getHelpText() {
    return "This Authenticator does nothing";
}

@Override
public List<ProviderConfigProperty> getConfigProperties() {
    return Collections.emptyList();
}
```

**Purpose:** Admin UI integration
**Usage:** Dropdown labels, help tooltips, configuration forms

### Instance Creation Strategy

**Pattern A: Stateless Instance (Used in Extensions)**
```java
@Override
public Authenticator create(KeycloakSession keycloakSession) {
    return new NoOperationAuthenticator();
}
```
- Creates new instance per request
- No state sharing between requests
- Thread-safe by design
- Simple lifecycle

**Pattern B: Singleton Instance**
```java
private static final Authenticator INSTANCE = new NoOperationAuthenticator();

@Override
public Authenticator create(KeycloakSession keycloakSession) {
    return INSTANCE;
}
```
- Reuses single instance
- Must be stateless and thread-safe
- Better performance (no allocation)
- Use for truly stateless providers

**Pattern C: Pooled Instances**
```java
private final ObjectPool<Authenticator> pool = new ObjectPool<>();

@Override
public Authenticator create(KeycloakSession keycloakSession) {
    return pool.borrow();
}
```
- Complex lifecycle management
- Use only when necessary (expensive initialization)
- Must handle return to pool

**Project Usage:** All extensions use Pattern A (new instance per request)

## 3. Null Object Pattern

### Context
Provide "do nothing" implementation that satisfies interface contract without null checks.

### Implementation: NoOperationAuthenticator

```java
public class NoOperationAuthenticator implements Authenticator {

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        context.success();  // Always succeeds
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        context.success();  // Always succeeds
    }

    @Override
    public boolean requiresUser() {
        return false;  // No user required
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return false;  // Never configured
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // NOP - No required actions
    }

    @Override
    public void close() {
        // NOP - Nothing to close
    }
}
```

### Benefits
1. **No Null Checks:** Consumers never need to check for null
2. **Safe Default:** Provides safe behavior (pass-through)
3. **Interface Compliance:** Fully implements contract
4. **Testing:** Useful in test scenarios
5. **Development:** Placeholder during development

### Use Cases
- Testing authentication flows
- Development placeholder
- Default/fallback authenticator
- Skip steps conditionally
- Debugging authentication issues

### Anti-Pattern Warning
**Don't use in production** for security-critical flows. This pattern is safe only when:
- Used as placeholder during development
- Testing/debugging purposes
- Explicitly required to skip a step
- Documented as intentional pass-through

## 4. Model-View-Controller (MVC) Pattern

### Context
Separate form presentation from business logic and data.

### Implementation: NoOperationFormAuthenticator

**Model:**
```java
public class NoOperationFormModel {
    private final List<String> options;

    public NoOperationFormModel() {
        options = new ArrayList<>();
        options.add("Option1");
        options.add("Option2");
    }

    public List<String> getOptions() {
        return options;
    }
}
```

**View (Freemarker Template):**
```freemarker
<#import "template.ftl" as layout>
<@layout.registrationLayout; section>
    <#if section = "form">
        <#if formModel.options?has_content>
            <ul>
                <#list formModel.options as option>
                    <li>${option}</li>
                </#list>
            </ul>
        </#if>
        <form action="${url.loginAction}" method="post">
            <input type="text" name="livingPlace" />
            <input type="submit" value="Submit" />
        </form>
    </#if>
</@layout.registrationLayout>
```

**Controller (Authenticator):**
```java
public class NoOperationFormAuthenticator implements Authenticator {

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        // Prepare model
        NoOperationFormModel model = new NoOperationFormModel();

        // Inject model into view
        LoginFormsProvider forms = context.form();
        forms.setAttribute("formModel", model);

        // Render view
        context.challenge(forms.createForm("living-place.ftl"));
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        // Process form submission
        MultivaluedMap<String, String> params =
            context.getHttpRequest().getDecodedFormParameters();

        String livingPlace = params.getFirst("livingPlace");

        // Business logic
        if (isValid(livingPlace)) {
            context.success();
        } else {
            context.failure(AuthenticationFlowError.INVALID_CREDENTIALS);
        }
    }
}
```

### Component Responsibilities

**Model:**
- Data structure for view
- Business domain objects
- No view knowledge
- No controller knowledge

**View:**
- Presentation only
- No business logic
- Accesses model via getters
- Uses Keycloak's template system

**Controller:**
- Orchestrates flow
- Prepares model
- Selects view
- Processes user input
- Applies business rules

### Benefits
1. **Separation of Concerns:** Each component has single responsibility
2. **Testability:** Can test each component independently
3. **Reusability:** Models and views can be reused
4. **Maintainability:** Changes isolated to relevant component
5. **Designer-Developer Split:** Designers work on templates, developers on logic

## 5. Template Method Pattern

### Context
Framework defines algorithm structure, subclasses implement specific steps.

### Keycloak's Authenticator Contract

```java
public interface Authenticator extends Provider {

    // Step 1: Present challenge or auto-authenticate
    void authenticate(AuthenticationFlowContext context);

    // Step 2: Process response to challenge
    void action(AuthenticationFlowContext context);

    // Query: Does this authenticator require a user?
    boolean requiresUser();

    // Query: Is this authenticator configured for the user?
    boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user);

    // Command: Set required actions on user
    void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user);

    // Lifecycle: Cleanup
    void close();
}
```

### Framework Algorithm (Implicit)

```java
// Keycloak's authentication flow engine (simplified)
public void executeAuthenticator(Authenticator authenticator, AuthenticationFlowContext context) {

    // Check if user is required
    if (authenticator.requiresUser() && context.getUser() == null) {
        context.failure(AuthenticationFlowError.UNKNOWN_USER);
        return;
    }

    // Check if authenticator is configured for user
    if (context.getUser() != null) {
        if (!authenticator.configuredFor(session, realm, context.getUser())) {
            // Trigger configuration if not set up
            authenticator.setRequiredActions(session, realm, context.getUser());
            context.failure(AuthenticationFlowError.CLIENT_CREDENTIALS_SETUP_REQUIRED);
            return;
        }
    }

    // Execute authentication
    if (context.isProcessingAction()) {
        authenticator.action(context);  // Process form submission
    } else {
        authenticator.authenticate(context);  // Present challenge
    }

    // Cleanup
    authenticator.close();
}
```

### Custom Implementation Points

**1. authenticate()** - Presentation Logic
```java
// Auto-authenticate
context.success();

// Challenge with form
context.challenge(forms.createForm("template.ftl"));

// Redirect to external system
context.forceChallenge(forms.createLoginPage());

// Failure
context.failure(AuthenticationFlowError.INVALID_CREDENTIALS);
```

**2. action()** - Processing Logic
```java
// Extract form parameters
MultivaluedMap<String, String> params = context.getHttpRequest().getDecodedFormParameters();

// Validate
if (isValid(params)) {
    context.success();
} else {
    context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
        forms.setError("invalid_input").createForm("template.ftl"));
}
```

**3. requiresUser()** - Precondition Query
```java
// User must exist before this authenticator
return true;

// Can execute without user context
return false;
```

**4. configuredFor()** - Configuration Check
```java
// Check if user has required credentials
return user.credentialManager()
    .getStoredCredentialsByTypeStream(CredentialType.OTP)
    .findAny()
    .isPresent();
```

**5. setRequiredActions()** - Configuration Setup
```java
// Add required action for user to configure
user.addRequiredAction(UserModel.RequiredAction.CONFIGURE_TOTP);
```

### Benefits
1. **Consistent Flow:** Framework ensures proper execution order
2. **Extensibility:** Implementations focus on specific logic
3. **Reusability:** Framework code reused across all authenticators
4. **Validation:** Framework handles common validation
5. **Lifecycle:** Framework manages provider lifecycle

## 6. Chain of Responsibility Pattern

### Context
Pass request through chain of handlers until one handles it.

### Implementation: Theme Resource Provider

```java
public class InvitationClasspathThemeProviderFactory extends ClasspathThemeResourceProviderFactory {

    private ThemeResourceProvider resourceProviderFactory;

    @Override
    public URL getTemplate(String name) throws IOException {
        // Try primary handler
        final URL template = super.getTemplate(name);
        if (template != null) {
            return template;  // Handled by primary
        }

        // Fallback to secondary handler
        LOGGER.debugf("getTemplate: for name '%s'", name);
        return resourceProviderFactory.getTemplate(name);  // Handled by fallback
    }

    @Override
    public InputStream getResourceAsStream(String path) throws IOException {
        final InputStream resourceAsStream = super.getResourceAsStream(path);
        if (resourceAsStream != null) {
            return resourceAsStream;
        }

        LOGGER.debugf("getResourceAsStream: for path '%s'", path);
        return resourceProviderFactory.getResourceAsStream(path);
    }
}
```

### Handler Chain Structure

```
Request: Get template "login.ftl"
    ↓
Primary Handler (Custom themes)
    → Found? Return it
    → Not found? Pass to next
    ↓
Secondary Handler (Base themes)
    → Found? Return it
    → Not found? Return null
```

### Pattern Variations

**Hard Chain (Used Here):**
```java
// Fixed chain: Primary → Secondary
URL result = primary.get();
if (result == null) {
    result = secondary.get();
}
return result;
```

**Soft Chain:**
```java
// Flexible chain with explicit handler registration
for (Handler handler : handlers) {
    URL result = handler.get();
    if (result != null) {
        return result;
    }
}
return null;
```

### Benefits
1. **Flexibility:** Can add/remove handlers
2. **Decoupling:** Handlers don't know about chain
3. **Priority:** Order determines precedence
4. **Fallback:** Graceful degradation
5. **Logging:** Can log at each step

### Use Cases in Keycloak
- **Theme Resolution:** Custom → Base → Default
- **Resource Loading:** Extension → Core → Fallback
- **Configuration:** Specific → General → Default
- **Event Handlers:** Multiple listeners processing same event

## 7. Dependency Injection Pattern

### Context
Framework provides dependencies instead of components creating them.

### Method Parameter Injection

```java
@Override
public Authenticator create(KeycloakSession keycloakSession) {
    // KeycloakSession injected by framework
    return new MyAuthenticator(keycloakSession);
}

@Override
public void authenticate(AuthenticationFlowContext context) {
    // AuthenticationFlowContext injected by framework
    KeycloakSession session = context.getSession();
    RealmModel realm = context.getRealm();
    UserModel user = context.getUser();
    // Use injected dependencies
}
```

### Service Locator via Session

```java
// Access services through session
public void someMethod(KeycloakSession session) {
    // Get user storage
    UserProvider users = session.users();

    // Get client storage
    ClientProvider clients = session.clients();

    // Get realm storage
    RealmProvider realms = session.realms();

    // Get event store
    EventStoreProvider events = session.getProvider(EventStoreProvider.class);

    // Get custom provider
    MyCustomProvider custom = session.getProvider(MyCustomProvider.class);
}
```

### Benefits
1. **Testability:** Can inject mocks for testing
2. **Loose Coupling:** Depend on interfaces, not implementations
3. **Flexibility:** Implementation can change without code changes
4. **Lifecycle Management:** Framework controls component lifecycle
5. **Configuration:** Dependencies configured externally

### Anti-Pattern Warning

**Don't do this:**
```java
// ❌ Creating dependencies yourself
public class BadAuthenticator implements Authenticator {
    private MyService service = new MyServiceImpl();  // Hard dependency

    public void authenticate(AuthenticationFlowContext context) {
        service.doSomething();  // Can't be tested with mock
    }
}
```

**Do this instead:**
```java
// ✅ Get dependencies from framework
public class GoodAuthenticator implements Authenticator {
    public void authenticate(AuthenticationFlowContext context) {
        MyService service = context.getSession().getProvider(MyService.class);
        service.doSomething();  // Can be mocked in tests
    }
}
```

## 8. Builder Pattern (Framework Provided)

### Context
Keycloak provides builders for complex object construction.

### LoginFormsProvider as Builder

```java
@Override
public void authenticate(AuthenticationFlowContext context) {
    LoginFormsProvider forms = context.form()
        // Set attributes
        .setAttribute("formModel", new MyFormModel())
        .setAttribute("user", context.getUser())

        // Set messages
        .setInfo("Please complete the form")
        .setWarning("This is your last attempt")

        // Set errors
        .addError(new FormMessage("field1", "Invalid value"))

        // Build and return form
        .createForm("my-template.ftl");

    context.challenge(forms);
}
```

### Error Handling Pattern

```java
@Override
public void action(AuthenticationFlowContext context) {
    MultivaluedMap<String, String> params = context.getHttpRequest().getDecodedFormParameters();

    // Validate
    List<FormMessage> errors = new ArrayList<>();
    if (params.getFirst("username") == null) {
        errors.add(new FormMessage("username", "Username is required"));
    }
    if (params.getFirst("password") == null) {
        errors.add(new FormMessage("password", "Password is required"));
    }

    if (!errors.isEmpty()) {
        // Build form with errors
        Response response = context.form()
            .setErrors(errors)
            .createForm("login.ftl");
        context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, response);
        return;
    }

    context.success();
}
```

## 9. Static Configuration Pattern

### Context
Configuration loaded once at startup for performance.

### Implementation

```java
public class MyProtocolMapper extends AbstractOIDCProtocolMapper {

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES = new ArrayList<>();

    static {
        // Loaded once when class is loaded
        ProviderConfigProperty property = new ProviderConfigProperty();
        property.setName("claim.name");
        property.setLabel("Claim Name");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText("Name of the claim to add to token");
        CONFIG_PROPERTIES.add(property);

        OIDCAttributeMapperHelper.addIncludeInTokensConfig(CONFIG_PROPERTIES, MyProtocolMapper.class);
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;  // Reuse static configuration
    }
}
```

### Benefits
1. **Performance:** Configuration created once
2. **Memory:** Single copy shared across instances
3. **Immutability:** Configuration can't change at runtime
4. **Thread Safety:** Read-only access is thread-safe

### Configuration Property Types

```java
// String input
property.setType(ProviderConfigProperty.STRING_TYPE);

// Boolean checkbox
property.setType(ProviderConfigProperty.BOOLEAN_TYPE);

// List/select dropdown
property.setType(ProviderConfigProperty.LIST_TYPE);
property.setOptions(Arrays.asList("Option1", "Option2"));

// Multi-select
property.setType(ProviderConfigProperty.MULTIVALUED_STRING_TYPE);

// Text area
property.setType(ProviderConfigProperty.TEXT_TYPE);

// Script editor
property.setType(ProviderConfigProperty.SCRIPT_TYPE);

// Client selection
property.setType(ProviderConfigProperty.CLIENT_LIST_TYPE);

// Role selection
property.setType(ProviderConfigProperty.ROLE_TYPE);
```

## Pattern Summary Table

| Pattern | Usage | Benefits | Complexity |
|---------|-------|----------|------------|
| Service Provider Interface | Extension registration | Plugin architecture | Low |
| Factory Pattern | Provider creation | Lifecycle control | Low |
| Null Object Pattern | Safe defaults | No null checks | Low |
| Model-View-Controller | Form handling | Separation of concerns | Medium |
| Template Method | Authentication flow | Consistent structure | Medium |
| Chain of Responsibility | Resource resolution | Flexible fallback | Medium |
| Dependency Injection | Service access | Testability | Low |
| Builder Pattern | Complex objects | Fluent API | Medium |
| Static Configuration | UI metadata | Performance | Low |

## Best Practices

### 1. Prefer Stateless Providers
```java
// ✅ Good: Stateless
public class StatelessAuthenticator implements Authenticator {
    public void authenticate(AuthenticationFlowContext context) {
        // All state from context
        String username = context.getHttpRequest().getParameter("username");
        context.success();
    }
}

// ❌ Bad: Stateful
public class StatefulAuthenticator implements Authenticator {
    private String username;  // State stored in instance

    public void authenticate(AuthenticationFlowContext context) {
        this.username = context.getHttpRequest().getParameter("username");
    }
}
```

### 2. Use Constants for Configuration Keys
```java
// ✅ Good
public class MyAuthenticator implements Authenticator {
    public static final String PARAM_USERNAME = "username";
    public static final String PARAM_PASSWORD = "password";

    public void action(AuthenticationFlowContext context) {
        String username = context.getHttpRequest().getParameter(PARAM_USERNAME);
    }
}

// ❌ Bad
public void action(AuthenticationFlowContext context) {
    String username = context.getHttpRequest().getParameter("username");  // Typo risk
}
```

### 3. Provide Meaningful Metadata
```java
// ✅ Good
@Override
public String getHelpText() {
    return "Validates user's email address through verification link sent to registered email";
}

// ❌ Bad
@Override
public String getHelpText() {
    return "Email Authenticator";  // Not helpful
}
```

### 4. Log at Appropriate Levels
```java
// Debug: Detailed flow information
LOG.debugf("Authenticating user: %s", username);

// Info: Important events
LOG.infof("User %s successfully authenticated", username);

// Warn: Recoverable issues
LOG.warnf("Failed authentication attempt for user %s", username);

// Error: Unexpected errors
LOG.errorf(exception, "Unexpected error during authentication");
```

### 5. Handle Errors Gracefully
```java
@Override
public void authenticate(AuthenticationFlowContext context) {
    try {
        // Authentication logic
        externalService.validate(user);
        context.success();
    } catch (ServiceUnavailableException e) {
        LOG.warn("External service unavailable", e);
        context.attempted();  // Mark as attempted, allow flow to continue
    } catch (Exception e) {
        LOG.error("Unexpected error", e);
        context.failure(AuthenticationFlowError.INTERNAL_ERROR);
    }
}
```

## Conclusion

The Keycloak custom extensions demonstrate professional implementation of:
- Industry-standard design patterns
- Keycloak-specific patterns and practices
- Clean code principles
- Framework integration best practices

These patterns provide a solid foundation for building production-ready Keycloak extensions.
