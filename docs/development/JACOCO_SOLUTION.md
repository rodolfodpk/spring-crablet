# Jacoco Cross-Module Coverage Solution

## Understanding the Problem

JaCoCo instruments classes **at runtime** during test execution. This means:
1. When `wallet-outbox-service` tests run, they load `crablet-outbox` classes as dependencies
2. JaCoCo instruments those `crablet-outbox` classes at runtime
3. Coverage data for `crablet-outbox` is stored in `wallet-outbox-service/target/jacoco.exec`
4. But `crablet-outbox/target/jacoco.exec` only contains coverage from `crablet-outbox`'s own tests

## The Solution

We need to merge execution data from wallet modules into the crablet modules' reports.

### Approach 1: Use report-aggregate in Parent POM (Recommended)

Configure the parent POM to generate an aggregated report that includes all modules.

### Approach 2: Post-Process Merge (Current Implementation)

Add a `merge` execution to each crablet module that combines:
- Coverage from crablet module's own tests
- Coverage from wallet modules

## Current Implementation

We've added merge executions to:
- `crablet-outbox/pom.xml` - merges with `wallet-outbox-service` coverage
- `crablet-eventstore/pom.xml` - merges with `wallet-eventstore-service` coverage

### How It Works

1. Each wallet module generates `target/jacoco.exec` with coverage data
2. During `verify` phase, crablet modules run the merge execution
3. The merge combines:
   - `crablet-X/target/jacoco.exec` (crablet's unit tests)
   - `wallet-X-service/target/jacoco.exec` (wallet's integration tests)
4. Generate report from merged execution data

### Executions Configured

```xml
<execution>
    <id>merge-with-wallet-coverage</id>
    <phase>verify</phase>
    <goals>
        <goal>merge</goal>
    </goals>
    <configuration>
        <fileSets>
            <fileSet>
                <directory>${project.basedir}</directory>
                <includes>
                    <include>target/jacoco.exec</include>
                </includes>
            </fileSet>
            <fileSet>
                <directory>${project.basedir}/../wallet-outbox-service</directory>
                <includes>
                    <include>target/jacoco.exec</include>
                </includes>
            </fileSet>
        </fileSets>
        <destFile>${project.build.directory}/jacoco-with-wallet-coverage.exec</destFile>
    </configuration>
</execution>
<execution>
    <id>report-with-wallet</id>
    <phase>verify</phase>
    <goals>
        <goal>report</goal>
    </goals>
    <configuration>
        <dataFile>${project.build.directory}/jacoco-with-wallet-coverage.exec</dataFile>
        <outputDirectory>${project.build.directory}/site/jacoco-complete</outputDirectory>
    </configuration>
</execution>
```

## Important Notes

- **Build Order Matters**: Wallet modules must build before crablet modules attempt to merge their coverage
- **Maven Reactor**: Use `-am` flag to build dependencies first: `mvn verify -am`
- **CI/CD**: Ensure full reactor build to get correct coverage: `mvn clean verify`

## Alternative: Parent-Level Aggregation

You could also use the parent POM's `report-aggregate` goal to generate a combined report that shows coverage across all modules simultaneously.

## Testing the Solution

Run:
```bash
mvn clean verify
```

Then check the reports:
- `crablet-outbox/target/site/jacoco-complete/index.html` - Should show 64%+ coverage
- `wallet-outbox-service/target/site/jacoco-aggregate-complete/index.html` - Aggregated view

## References

- [JaCoCo Maven Plugin Documentation](https://www.eclemma.org/jacoco/trunk/doc/maven.html)
- [JaCoCo Mission & Goals](https://www.jacoco.org/jacoco/trunk/doc/mission.html)
- [JaCoCo Maven Plugin Goals](https://www.jacoco.org/jacoco/trunk/doc/maven.html#usage)

