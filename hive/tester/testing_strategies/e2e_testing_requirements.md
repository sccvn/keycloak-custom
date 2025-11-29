# End-to-End Testing Requirements - Keycloak Custom

## Overview
End-to-end testing strategy for validating complete authentication and authorization flows in Keycloak Custom, including browser-based UI testing and API flow validation.

## E2E Testing Scope

### What Should Be Tested E2E

1. **Complete Authentication Flows**
   - User login with username/password
   - Custom authenticator flows (NoOp authenticator example)
   - Multi-factor authentication sequences
   - Social login integrations
   - Password reset flows

2. **Authorization Flows**
   - OAuth2 authorization code flow
   - OpenID Connect authentication
   - Token exchange and refresh
   - Client credentials grant
   - SAML authentication flows

3. **User Management Workflows**
   - User registration
   - Profile updates
   - Email verification
   - Account linking

4. **Admin Console Operations**
   - Realm creation and configuration
   - Client registration
   - Role and permission assignment
   - Identity provider configuration

5. **Custom Extension Validation**
   - Custom theme rendering
   - Protocol mapper token transformations
   - Authenticator integration in flows
   - Event listener callbacks

## Recommended E2E Testing Stack

### Browser Automation
```xml
<!-- Playwright for modern browser automation -->
<dependency>
    <groupId>com.microsoft.playwright</groupId>
    <artifactId>playwright</artifactId>
    <version>1.43.0</version>
    <scope>test</scope>
</dependency>

<!-- Alternative: Selenium WebDriver -->
<dependency>
    <groupId>org.seleniumhq.selenium</groupId>
    <artifactId>selenium-java</artifactId>
    <version>4.18.1</version>
    <scope>test</scope>
</dependency>
```

### API Testing
```xml
<!-- REST Assured for API testing -->
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>rest-assured</artifactId>
    <version>5.4.0</version>
    <scope>test</scope>
</dependency>

<!-- For OAuth2/OIDC flows -->
<dependency>
    <groupId>com.nimbusds</groupId>
    <artifactId>oauth2-oidc-sdk</artifactId>
    <version>11.9.1</version>
    <scope>test</scope>
</dependency>
```

## E2E Test Architecture

### Test Organization Structure
```
src/test/java/e2e/
├── flows/
│   ├── AuthenticationFlowsE2ETest.java
│   ├── OAuth2FlowsE2ETest.java
│   └── PasswordResetFlowE2ETest.java
├── pages/
│   ├── LoginPage.java
│   ├── RegisterPage.java
│   ├── AdminConsolePage.java
│   └── UserAccountPage.java
├── api/
│   ├── TokenEndpointTest.java
│   └── UserInfoEndpointTest.java
└── scenarios/
    ├── CustomAuthenticatorScenarioTest.java
    └── ThemeCustomizationScenarioTest.java
```

## E2E Test Patterns

### 1. Page Object Model (POM)

```java
/**
 * Page Object for Keycloak Login Page
 */
public class LoginPage {
    private final Page page;
    private final String baseUrl;

    public LoginPage(Page page, String keycloakBaseUrl) {
        this.page = page;
        this.baseUrl = keycloakBaseUrl;
    }

    public void navigate(String realm, String clientId, String redirectUri) {
        String authUrl = String.format(
            "%s/realms/%s/protocol/openid-connect/auth?client_id=%s&redirect_uri=%s&response_type=code&scope=openid",
            baseUrl, realm, clientId, redirectUri
        );
        page.navigate(authUrl);
    }

    public void enterUsername(String username) {
        page.fill("#username", username);
    }

    public void enterPassword(String password) {
        page.fill("#password", password);
    }

    public void clickLogin() {
        page.click("#kc-login");
    }

    public boolean isErrorDisplayed() {
        return page.isVisible("#input-error");
    }

    public String getErrorMessage() {
        return page.textContent("#input-error");
    }

    public void login(String username, String password) {
        enterUsername(username);
        enterPassword(password);
        clickLogin();
    }
}
```

### 2. Complete Authentication Flow Test

```java
@Tag("e2e")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthenticationFlowsE2ETest {

    private SystemUnderTest sut;
    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;

    @BeforeAll
    void setUpAll() {
        sut = SystemUnderTest.start();
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
            .setHeadless(true));
    }

    @BeforeEach
    void setUp() {
        context = browser.newContext();
        page = context.newPage();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @AfterAll
    void tearDownAll() {
        browser.close();
        playwright.close();
        sut.stop();
    }

    @Test
    @DisplayName("User should successfully login with valid credentials")
    void testSuccessfulLogin() {
        // Arrange
        String testUser = createTestUser("testuser", "password123");
        LoginPage loginPage = new LoginPage(page, sut.getBaseUrl());

        // Act
        loginPage.navigate("example1", "test-client", "http://localhost:8080/callback");
        loginPage.login("testuser", "password123");

        // Wait for redirect to callback
        page.waitForURL("**/callback**");

        // Assert
        String currentUrl = page.url();
        assertTrue(currentUrl.contains("code="),
            "Should receive authorization code in callback URL");
        assertFalse(currentUrl.contains("error="),
            "Should not have error in callback URL");
    }

    @Test
    @DisplayName("User should see error message with invalid credentials")
    void testInvalidCredentials() {
        // Arrange
        LoginPage loginPage = new LoginPage(page, sut.getBaseUrl());

        // Act
        loginPage.navigate("example1", "test-client", "http://localhost:8080/callback");
        loginPage.login("invaliduser", "wrongpassword");

        // Assert
        assertTrue(loginPage.isErrorDisplayed(),
            "Error message should be displayed");
        assertTrue(loginPage.getErrorMessage().contains("Invalid username or password"),
            "Error message should indicate invalid credentials");
    }
}
```

### 3. OAuth2 Authorization Code Flow Test

```java
@Test
@DisplayName("Complete OAuth2 authorization code flow should issue valid tokens")
void testOAuth2AuthorizationCodeFlow() {
    // Arrange
    String realm = "example1";
    String clientId = "test-client";
    String clientSecret = "test-secret";
    String redirectUri = "http://localhost:8080/callback";

    LoginPage loginPage = new LoginPage(page, sut.getBaseUrl());

    // Act - Step 1: Get authorization code
    loginPage.navigate(realm, clientId, redirectUri);
    loginPage.login("testuser", "password123");
    page.waitForURL("**/callback**");

    String callbackUrl = page.url();
    String authCode = extractCodeFromUrl(callbackUrl);

    // Act - Step 2: Exchange code for tokens
    given()
        .baseUri(sut.getBaseUrl())
        .basePath("/realms/" + realm + "/protocol/openid-connect/token")
        .formParam("grant_type", "authorization_code")
        .formParam("code", authCode)
        .formParam("client_id", clientId)
        .formParam("client_secret", clientSecret)
        .formParam("redirect_uri", redirectUri)
    .when()
        .post()
    .then()
        .statusCode(200)
        .body("access_token", notNullValue())
        .body("refresh_token", notNullValue())
        .body("token_type", equalTo("Bearer"))
        .body("expires_in", greaterThan(0));
}
```

### 4. Custom Authenticator E2E Test

```java
@Test
@DisplayName("Custom NoOp authenticator should allow authentication without user interaction")
void testNoOpAuthenticatorFlow() {
    // Arrange
    String realm = "example1";
    String flowAlias = "custom-noop-flow";

    // Configure authentication flow with NoOp authenticator via Admin API
    configureNoOpAuthenticationFlow(realm, flowAlias);

    // Create client using custom flow
    String clientId = createClientWithAuthFlow(realm, flowAlias);

    // Act - Attempt authentication with NoOp flow
    LoginPage loginPage = new LoginPage(page, sut.getBaseUrl());
    loginPage.navigate(realm, clientId, "http://localhost:8080/callback");

    // NoOp authenticator should bypass normal login
    page.waitForURL("**/callback**", new Page.WaitForURLOptions().setTimeout(5000));

    // Assert
    String currentUrl = page.url();
    assertTrue(currentUrl.contains("code="),
        "NoOp authenticator should issue authorization code without user interaction");
}
```

### 5. Token Validation E2E Test

```java
@Test
@DisplayName("Access token should contain custom protocol mapper claims")
void testCustomProtocolMapperInToken() {
    // Arrange
    String accessToken = authenticateAndGetAccessToken("testuser", "password123");

    // Act - Decode JWT token
    SignedJWT signedJWT = SignedJWT.parse(accessToken);
    JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

    // Assert
    assertAll("Token should contain expected claims",
        () -> assertNotNull(claims.getSubject(), "Token should have subject"),
        () -> assertEquals("testuser", claims.getClaim("preferred_username"),
            "Token should contain username"),
        () -> assertNotNull(claims.getClaim("custom_claim"),
            "Custom protocol mapper should add custom claim"),
        () -> assertTrue(claims.getExpirationTime().after(new Date()),
            "Token should not be expired")
    );
}
```

### 6. Theme Customization E2E Test

```java
@Test
@DisplayName("Custom login theme should be rendered correctly")
void testCustomThemeRendering() {
    // Arrange
    LoginPage loginPage = new LoginPage(page, sut.getBaseUrl());

    // Act
    loginPage.navigate("example1", "test-client", "http://localhost:8080/callback");

    // Assert - Check for custom theme elements
    assertTrue(page.isVisible(".inventage-logo"),
        "Custom theme logo should be visible");

    String cssClass = page.getAttribute("#kc-page-title", "class");
    assertTrue(cssClass.contains("inventage-title"),
        "Custom theme CSS should be applied");

    // Verify custom theme assets are loaded
    Response logoResponse = page.request().get(
        sut.getBaseUrl() + "/realms/example1/theme/inventage/login/resources/img/logo.png"
    );
    assertEquals(200, logoResponse.status(),
        "Custom theme logo should be accessible");
}
```

### 7. Multi-Step Authentication Flow

```java
@Test
@DisplayName("Multi-step authentication with OTP should complete successfully")
void testMultiFactorAuthenticationFlow() {
    // Arrange
    String testUser = createTestUserWithOTP("mfauser", "password123");
    LoginPage loginPage = new LoginPage(page, sut.getBaseUrl());
    OTPPage otpPage = new OTPPage(page);

    // Act - Step 1: Username/Password
    loginPage.navigate("example1", "test-client", "http://localhost:8080/callback");
    loginPage.login("mfauser", "password123");

    // Step 2: OTP Challenge
    page.waitForSelector("#otp");
    String totpCode = generateTOTPCode(testUser);
    otpPage.enterOTP(totpCode);
    otpPage.submitOTP();

    // Assert
    page.waitForURL("**/callback**");
    assertTrue(page.url().contains("code="),
        "MFA flow should successfully issue authorization code");
}
```

## API-Level E2E Tests

### OIDC Discovery Endpoint Test

```java
@Test
@DisplayName("OIDC discovery endpoint should return valid configuration")
void testOIDCDiscoveryEndpoint() {
    given()
        .baseUri(sut.getBaseUrl())
        .basePath("/realms/example1/.well-known/openid-configuration")
    .when()
        .get()
    .then()
        .statusCode(200)
        .body("issuer", equalTo(sut.getBaseUrl() + "/realms/example1"))
        .body("authorization_endpoint", notNullValue())
        .body("token_endpoint", notNullValue())
        .body("userinfo_endpoint", notNullValue())
        .body("jwks_uri", notNullValue())
        .body("grant_types_supported", hasItems("authorization_code", "client_credentials"))
        .body("response_types_supported", hasItems("code", "token"))
        .body("scopes_supported", hasItems("openid", "profile", "email"));
}
```

### Token Introspection Test

```java
@Test
@DisplayName("Token introspection should validate active tokens")
void testTokenIntrospection() {
    // Arrange
    String accessToken = authenticateAndGetAccessToken("testuser", "password123");

    // Act & Assert
    given()
        .baseUri(sut.getBaseUrl())
        .basePath("/realms/example1/protocol/openid-connect/token/introspect")
        .auth().preemptive().basic("test-client", "test-secret")
        .formParam("token", accessToken)
    .when()
        .post()
    .then()
        .statusCode(200)
        .body("active", equalTo(true))
        .body("username", equalTo("testuser"))
        .body("client_id", equalTo("test-client"))
        .body("token_type", equalTo("Bearer"));
}
```

## Test Data Management

### Test User Factory

```java
public class TestUserFactory {
    private final Keycloak adminClient;
    private final String realm;

    public String createUser(String username, String password) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(username + "@test.com");
        user.setEnabled(true);

        Response response = adminClient.realm(realm)
            .users()
            .create(user);

        String userId = extractUserId(response);

        // Set password
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);

        adminClient.realm(realm)
            .users()
            .get(userId)
            .resetPassword(credential);

        return userId;
    }

    public void deleteUser(String userId) {
        adminClient.realm(realm)
            .users()
            .get(userId)
            .remove();
    }
}
```

## Performance and Load Testing

### Performance Test with Gatling

```scala
class KeycloakLoadTest extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .acceptHeader("application/json")

  val scn = scenario("OAuth2 Login Flow")
    .exec(http("Get Auth Code")
      .get("/realms/example1/protocol/openid-connect/auth")
      .queryParam("client_id", "test-client")
      .queryParam("redirect_uri", "http://localhost/callback")
      .queryParam("response_type", "code")
      .check(status.is(200))
      .check(regex("""code=([^&]+)""").saveAs("authCode")))
    .exec(http("Exchange Code for Token")
      .post("/realms/example1/protocol/openid-connect/token")
      .formParam("grant_type", "authorization_code")
      .formParam("code", "${authCode}")
      .formParam("client_id", "test-client")
      .formParam("client_secret", "test-secret")
      .check(status.is(200))
      .check(jsonPath("$.access_token").saveAs("accessToken")))

  setUp(
    scn.inject(
      rampUsers(100) during (60 seconds)
    )
  ).protocols(httpProtocol)
   .assertions(
     global.responseTime.max.lt(2000),
     global.successfulRequests.percent.gt(95)
   )
}
```

## Best Practices

1. **Browser Management**: Use headless mode for CI, headed for debugging
2. **Wait Strategies**: Use explicit waits, avoid Thread.sleep()
3. **Test Isolation**: Each test should clean up its data
4. **Screenshot on Failure**: Capture screenshots for failed tests
5. **Video Recording**: Record test execution for complex flows
6. **Parallel Execution**: Use separate browser contexts, not tabs
7. **Network Interception**: Mock external services when needed
8. **Accessibility Testing**: Include a11y checks in E2E tests
9. **Mobile Testing**: Test responsive behavior
10. **Cross-Browser**: Test on Chrome, Firefox, Safari

## CI/CD Integration

### Maven Execution
```bash
# Run E2E tests
mvn verify -Pe2e

# Run with specific browser
mvn verify -Pe2e -Dbrowser=firefox

# Run headless
mvn verify -Pe2e -Dheadless=true
```

### GitHub Actions
```yaml
- name: Run E2E Tests
  run: mvn verify -Pe2e -Dheadless=true
  env:
    PLAYWRIGHT_BROWSERS_PATH: 0

- name: Upload Test Videos
  if: failure()
  uses: actions/upload-artifact@v3
  with:
    name: test-videos
    path: target/playwright-videos/
```

## Recommended Tools

1. **Playwright**: Modern browser automation (recommended)
2. **Cypress**: JavaScript E2E testing framework
3. **REST Assured**: API testing library
4. **Gatling**: Load and performance testing
5. **K6**: Modern load testing tool
6. **Postman/Newman**: API collection testing

## Next Steps

1. Implement Playwright-based E2E test suite
2. Add cross-browser testing configuration
3. Create reusable page objects for all Keycloak pages
4. Implement visual regression testing
5. Add performance testing with Gatling
6. Create E2E test data management utilities
7. Set up E2E test reporting dashboard
8. Implement accessibility testing
