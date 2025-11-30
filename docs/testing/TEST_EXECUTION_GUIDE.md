# Test Execution Guide

## Quick Reference

### Run All Tests
```bash
cd /home/tuanna47/workspace/keycloak-custom
./mvnw test -pl extensions/extension-webauthn-realm
```

### Run Specific Test Classes

```bash
# Credential Manager Tests
./mvnw test -pl extensions/extension-webauthn-realm \
  -Dtest=WebAuthnCredentialManagerTest

# Authentication Service Tests
./mvnw test -pl extensions/extension-webauthn-realm \
  -Dtest=WebAuthnAuthenticationServiceTest

# Registration Service Tests
./mvnw test -pl extensions/extension-webauthn-realm \
  -Dtest=WebAuthnRegistrationServiceTest

# Utility Tests
./mvnw test -pl extensions/extension-webauthn-realm \
  -Dtest=Base64UtilTest,ChallengeGeneratorTest
```

### Run Specific Test Methods

```bash
# Single test method
./mvnw test -pl extensions/extension-webauthn-realm \
  -Dtest=WebAuthnCredentialManagerTest#testCreateCredential_Success

# Multiple test methods (pattern matching)
./mvnw test -pl extensions/extension-webauthn-realm \
  -Dtest=WebAuthnCredentialManagerTest#test*Credential*
```

### Run Integration Tests

```bash
# All integration tests
./mvnw verify -pl extensions/extension-webauthn-realm

# Specific integration test
./mvnw verify -pl extensions/extension-webauthn-realm \
  -Dit.test=WebAuthnIntegrationTest
```

---

## Test Categories

### Unit Tests (Fast)
**Execution Time**: 2-5 seconds
**Dependencies**: None (mocked)
**When to run**: On every code change

```bash
./mvnw test -pl extensions/extension-webauthn-realm
```

**Tests Included**:
- WebAuthnCredentialManagerTest (15 tests)
- WebAuthnAuthenticationServiceTest (8 tests)
- WebAuthnRegistrationServiceTest (12 tests)
- Base64UtilTest (16 tests)
- ChallengeGeneratorTest (14 tests)

### Integration Tests (Slow)
**Execution Time**: 30-60 seconds
**Dependencies**: Docker (Testcontainers)
**When to run**: Before commit/merge

```bash
./mvnw verify -pl extensions/extension-webauthn-realm
```

**Tests Included**:
- WebAuthnIntegrationTest (10 tests)

**Requirements**:
- Docker daemon running
- Sufficient memory (2GB+ recommended)
- Network access for container images

---

## Test Execution Options

### With Coverage Report

```bash
./mvnw clean test jacoco:report -pl extensions/extension-webauthn-realm

# View report
open extensions/extension-webauthn-realm/target/site/jacoco/index.html
# or
firefox extensions/extension-webauthn-realm/target/site/jacoco/index.html
```

### With Debug Output

```bash
# Maven debug mode
./mvnw test -X -pl extensions/extension-webauthn-realm

# Test debug mode (attach debugger)
./mvnw test -Dmaven.surefire.debug -pl extensions/extension-webauthn-realm
# Then attach debugger to port 5005
```

### With Specific JVM Options

```bash
# Increase memory
./mvnw test -pl extensions/extension-webauthn-realm \
  -DargLine="-Xmx1024m -XX:MaxPermSize=512m"

# Enable assertions
./mvnw test -pl extensions/extension-webauthn-realm \
  -DargLine="-ea"
```

### Skip Tests

```bash
# Skip all tests
./mvnw clean package -DskipTests

# Skip only unit tests
./mvnw clean package -Dmaven.test.skip=true

# Skip only integration tests
./mvnw clean package -DskipITs
```

---

## Troubleshooting

### Problem: Tests fail to find classes

**Solution**: Clean and rebuild
```bash
./mvnw clean test -pl extensions/extension-webauthn-realm
```

### Problem: Testcontainers timeout

**Symptoms**: Integration tests fail with timeout errors

**Solutions**:
```bash
# Check Docker is running
docker ps

# Increase Docker memory (Docker Desktop Settings)
# Recommended: 4GB+

# Pull images manually
docker pull quay.io/keycloak/keycloak:26.4.6
docker pull postgres:17-alpine

# Clean up old containers
docker container prune -f
docker volume prune -f
```

### Problem: Tests pass locally but fail in CI

**Common causes**:
- Different Java version
- Missing dependencies
- Docker not available
- Memory constraints

**Solution**: Match CI environment
```bash
# Check Java version
java -version

# Run in CI-like environment
docker run --rm -v "$PWD":/app -w /app maven:3.9-eclipse-temurin-21 \
  mvn test -pl extensions/extension-webauthn-realm
```

### Problem: Flaky tests

**Symptoms**: Tests pass/fail intermittently

**Common causes**:
- Race conditions (concurrent tests)
- Time-dependent logic
- External dependencies
- Insufficient waits

**Solution**: Add debugging
```bash
# Run test multiple times
for i in {1..10}; do
  ./mvnw test -pl extensions/extension-webauthn-realm \
    -Dtest=FlakyTest || break
done

# Enable test retry
./mvnw test -pl extensions/extension-webauthn-realm \
  -Dsurefire.rerunFailingTestsCount=2
```

---

## Test Data

### Test Realm Configuration

Location: `src/test/resources/test-realm.json`

**Contents**:
- Realm name: `test-realm`
- Test users
- Client configurations
- WebAuthn policies

### Modifying Test Data

```bash
# Edit test realm
vim extensions/extension-webauthn-realm/src/test/resources/test-realm.json

# Recreate from running Keycloak
# 1. Start Keycloak with test configuration
# 2. Export realm: Admin Console > Realm Settings > Export
# 3. Save to src/test/resources/test-realm.json
```

### Test Fixtures

All test data generated through:
```java
import static com.inventage.keycloak.webauthn.fixtures.WebAuthnTestFixtures.*;

String challenge = generateChallenge();
CredentialModel credential = createMockCredential();
```

---

## Performance Testing

### Benchmark Tests

```bash
# Run with JMH (if configured)
./mvnw test -pl extensions/extension-webauthn-realm \
  -Dtest=*BenchmarkTest

# Profile memory usage
./mvnw test -pl extensions/extension-webauthn-realm \
  -DargLine="-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp"
```

### Load Testing

```bash
# Use JMeter or Gatling for load tests
# Example: 100 concurrent users, 1000 requests
gatling:test -Dusers=100 -Drequests=1000
```

---

## Continuous Integration

### GitHub Actions Example

```yaml
name: Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Cache Maven packages
        uses: actions/cache@v3
        with:
          path: ~/.m2
          key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}

      - name: Run tests
        run: ./mvnw test -pl extensions/extension-webauthn-realm

      - name: Run integration tests
        run: ./mvnw verify -pl extensions/extension-webauthn-realm

      - name: Generate coverage
        run: ./mvnw jacoco:report -pl extensions/extension-webauthn-realm

      - name: Upload coverage
        uses: codecov/codecov-action@v3
```

---

## Test Metrics

### Tracking Coverage Over Time

```bash
# Generate coverage report
./mvnw clean test jacoco:report -pl extensions/extension-webauthn-realm

# Extract coverage percentage
grep -oP 'Total.*?([0-9]{1,3})%' \
  extensions/extension-webauthn-realm/target/site/jacoco/index.html
```

### Test Execution Trends

```bash
# Log test execution times
./mvnw test -pl extensions/extension-webauthn-realm | tee test-log.txt

# Extract execution time
grep "Total time:" test-log.txt
```

---

## Best Practices

### Before Committing

1. **Run all tests**
   ```bash
   ./mvnw clean test -pl extensions/extension-webauthn-realm
   ```

2. **Check coverage** (should be >80%)
   ```bash
   ./mvnw jacoco:report -pl extensions/extension-webauthn-realm
   ```

3. **Run integration tests** (if Docker available)
   ```bash
   ./mvnw verify -pl extensions/extension-webauthn-realm
   ```

4. **Fix any warnings**
   ```bash
   ./mvnw test -pl extensions/extension-webauthn-realm 2>&1 | grep -i warning
   ```

### Before Merging

1. **Full clean build**
   ```bash
   ./mvnw clean verify -pl extensions/extension-webauthn-realm
   ```

2. **Check for flaky tests**
   ```bash
   for i in {1..5}; do
     ./mvnw test -pl extensions/extension-webauthn-realm || exit 1
   done
   ```

3. **Verify documentation updated**
   ```bash
   git diff --name-only | grep -E '\.md$'
   ```

---

## Quick Debugging

### Test Output Too Verbose

```bash
# Reduce output
./mvnw test -pl extensions/extension-webauthn-realm --quiet

# Or redirect
./mvnw test -pl extensions/extension-webauthn-realm > test-output.log 2>&1
```

### Find Slow Tests

```bash
# Enable test timing
./mvnw test -pl extensions/extension-webauthn-realm \
  -Dsurefire.printSummary=true

# Sort by execution time
./mvnw test -pl extensions/extension-webauthn-realm | \
  grep "Time elapsed" | sort -t: -k2 -n
```

### Re-run Only Failed Tests

```bash
# First run
./mvnw test -pl extensions/extension-webauthn-realm

# Re-run failed
./mvnw test -pl extensions/extension-webauthn-realm \
  -Dsurefire.rerunFailingTestsCount=2
```

---

## Summary Commands

```bash
# Quick test run (unit tests only)
./mvnw test -pl extensions/extension-webauthn-realm

# Full test run (unit + integration)
./mvnw verify -pl extensions/extension-webauthn-realm

# With coverage
./mvnw clean test jacoco:report -pl extensions/extension-webauthn-realm

# Debug mode
./mvnw test -X -Dmaven.surefire.debug -pl extensions/extension-webauthn-realm

# Clean build
./mvnw clean verify -pl extensions/extension-webauthn-realm
```

---

**Document Version**: 1.0
**Last Updated**: 2025-11-30
**Author**: Testing Analysis Agent
