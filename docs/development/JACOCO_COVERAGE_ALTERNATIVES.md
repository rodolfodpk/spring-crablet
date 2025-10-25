# Jacoco Code Coverage Alternatives

## Problem Statement

We have a multi-module Maven project where:
- **Library modules**: `crablet-eventstore`, `crablet-outbox` 
- **Application modules**: `wallet-eventstore-service`, `wallet-outbox-service`

The issue is that:
1. Tests for library functionality are in the application modules
2. Jacoco runs per-module and doesn't track cross-module coverage by default
3. Coverage from `wallet-outbox-service` tests against `crablet-outbox` code isn't counted toward `crablet-outbox` coverage metrics

## Alternatives

### Option 1: Use report-aggregate Goal (✅ Recommended)

**Approach**: Use Jacoco's `report-aggregate` goal which is designed for this exact scenario.

**Pros**:
- Designed for multi-module projects
- Automatically includes coverage from dependent modules
- Handles source directories automatically

**Cons**:
- Requires proper configuration of aggregate report
- May need module ordering to be correct

**Implementation**:
```xml
<!-- In root pom.xml -->
<execution>
    <id>report-aggregate</id>
    <phase>verify</phase>
    <goals>
        <goal>report-aggregate</goal>
    </goals>
    <configuration>
        <outputDirectory>${project.build.directory}/site/jacoco-aggregate</outputDirectory>
    </configuration>
</execution>
```

### Option 2: Move Integration Tests to Crablet Modules

**Approach**: Create integration tests directly in `crablet-eventstore/src/test` and `crablet-outbox/src/test`.

**Pros**:
- Tests live with the code they test
- Jacoco automatically includes coverage
- Clear separation of concerns

**Cons**:
- Less realistic (tests won't use the library as an actual application would)
- Need to duplicate application setup in each library module
- Mixes unit and integration test concerns within libraries

**Example Structure**:
```
crablet-outbox/
  src/
    test/
      java/
        crablet/
          integration/  # Integration tests here
            OutboxProcessorIntegrationTest.java
```

### Option 3: Create Separate Test Module

**Approach**: Create `crablet-integration-tests` module that depends on both libraries and contains all integration tests.

**Pros**:
- Clean separation of unit tests vs integration tests
- All integration tests in one place
- Can test multiple libraries together

**Cons**:
- Adds another module to maintain
- May not reflect real-world usage as well as application modules do

**Example Structure**:
```
crablet/
  modules/
    crablet-eventstore/
    crablet-outbox/
    integration-tests/  # New module
      pom.xml
      src/test/java/
        crablet/
          IntegrationTest.java
```

### Option 4: Use Source Directories in Report Generation

**Approach**: Configure the report goal to include source directories from multiple modules.

**Pros**:
- Keeps current test locations
- Explicit control over what's included

**Cons**:
- Less flexible
- Requires manual configuration for each module addition
- Path management gets complex

**Implementation**:
```xml
<execution>
    <id>report</id>
    <phase>verify</phase>
    <goals>
        <goal>report</goal>
    </goals>
    <configuration>
        <dataFile>${project.build.directory}/jacoco-merged.exec</dataFile>
        <outputDirectory>${project.build.directory}/site/jacoco-aggregate</outputDirectory>
        <sourceDirectories>
            <sourceDirectory>${project.basedir}/crablet-eventstore/src/main/java</sourceDirectory>
            <sourceDirectory>${project.basedir}/crablet-outbox/src/main/java</sourceDirectory>
            <sourceDirectory>${project.basedir}/wallet-outbox-service/src/main/java</sourceDirectory>
        </sourceDirectories>
    </configuration>
</execution>
```

### Option 5: Accept Separate Coverage Metrics

**Approach**: Have separate coverage metrics for library unit tests vs integration test coverage from applications.

**Pros**:
- Simple, no configuration changes needed
- Clear distinction between unit test coverage and integration test coverage
- Realistic test scenarios

**Cons**:
- Doesn't meet goal of "previous coverage"
- Coverage numbers may appear lower
- Need to explain metrics in documentation

**Documentation Example**:
```
Coverage Metrics:
- crablet-eventstore: 75% (unit tests only)
- crablet-outbox: 70% (unit tests only)
- Integration Coverage: 90% (from wallet-* service tests)
```

### Option 6: Use SonarQube for Cross-Module Coverage

**Approach**: Use SonarQube which better handles multi-module projects and cross-module coverage.

**Pros**:
- Industry standard tool
- Designed for this scenario
- Better visualization and tracking

**Cons**:
- Requires additional infrastructure
- Different tool to learn
- May not fit current CI/CD setup

**Implementation**:
```xml
<plugin>
    <groupId>org.sonarsource.scanner.maven</groupId>
    <artifactId>sonar-maven-plugin</artifactId>
    <version>3.9.1.2184</version>
</plugin>
```

## Recommended Solution

Based on the current architecture, I recommend **Option 1** (report-aggregate) as the primary solution with a fallback to **Option 5** (accept separate metrics).

### Why Option 1?
1. Maintains realistic test scenarios (tests in application modules)
2. Uses built-in Jacoco functionality designed for this
3. Minimal changes required
4. Already partially implemented in the codebase

### Implementation Steps

1. ✅ Ensure all modules have jacoco-plugin configured (already done)
2. ✅ Add report-aggregate execution to root pom.xml (just added)
3. Test that merged coverage includes all modules
4. Update CI/CD to use the aggregated report
5. Document the coverage metrics

### Fallback Strategy

If Option 1 doesn't meet coverage requirements, we can:
1. Move some key integration tests to the crablet modules (Option 2)
2. Document that we track both unit coverage and integration coverage separately (Option 5)
3. Consider moving some tests if coverage targets aren't met

## Current Status

- ✅ Jacoco merge is configured in root pom.xml
- ✅ report-aggregate execution added
- ⏳ Need to verify coverage actually includes cross-module data
- ⏳ Need to update coverage reports in CI/CD

## Testing the Solution

Run the following to test:

```bash
# Clean and test
mvn clean verify

# Check if aggregated report was generated
ls -la target/site/jacoco-aggregate-complete/

# Open the report
open target/site/jacoco-aggregate-complete/index.html
```

## References

- [Jacoco Maven Plugin Documentation](https://www.eclemma.org/jacoco/trunk/doc/maven.html)
- [Multi-Module Coverage Best Practices](https://www.jacoco.org/jacoco/trunk/doc/maven.html#report-aggregate-goal)

