# Jacoco Cross-Module Coverage - Solution Implemented

## Problem Solved ✅

**Issue**: Tests for `crablet-outbox` and `crablet-eventstore` are located in `wallet-outbox-service` and `wallet-eventstore-service` respectively. Jacoco couldn't count this coverage for the library modules.

**Result**: Coverage now properly includes integration tests from wallet modules.

## Coverage Results

### Before
- `crablet-outbox`: **11%** coverage (unit tests only)
- Coverage from wallet integration tests was **not counted** for library modules

### After  
- `crablet-outbox`: **64%** coverage (unit tests + wallet integration tests)
- **480% increase** in code coverage

### Coverage Breakdown
- Instruction Coverage: **64%** (2,546 covered out of 3,962)
- Branch Coverage: **48%** (115 covered out of 236)
- Line Coverage: **74%** (907 covered out of 1,227)
- Method Coverage: **76%** (200 covered out of 263)

## Implementation Details

### Modified Files

1. **crablet-outbox/pom.xml**
   - Added `merge-with-wallet-coverage` execution
   - Merges coverage data from `wallet-outbox-service`
   - Generates report at `target/site/jacoco-complete/index.html`

2. **crablet-eventstore/pom.xml**
   - Added `merge-with-wallet-coverage` execution
   - Merges coverage data from `wallet-eventstore-service`
   - Generates report at `target/site/jacoco-complete/index.html`

3. **pom.xml (root)**
   - Added `report-aggregate` execution
   - Generates aggregate report at `target/site/jacoco-aggregate-complete/index.html`

### How It Works

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

## Running the Build

### Full Build (All Modules)
```bash
mvn clean verify
```

### Specific Module
```bash
mvn clean verify -pl crablet-outbox,wallet-outbox-service -am
```

## Generated Reports

1. **crablet-outbox/target/site/jacoco-complete/index.html**
   - Shows 64% coverage (merged with wallet tests)

2. **crablet-eventstore/target/site/jacoco-complete/index.html**
   - Shows merged coverage including wallet tests

3. **wallet-outbox-service/target/site/jacoco-aggregate-complete/index.html**
   - Shows aggregated view of all dependencies

4. **target/site/jacoco-aggregate-complete/index.html** (root)
   - Shows overall project coverage

## Key Insights from Jacoco Documentation

1. **Runtime Instrumentation**: JaCoCo instruments classes at runtime when they are loaded by the JVM during test execution.

2. **Execution Data Location**: Coverage data is stored in the module where tests execute. When wallet-outbox-service tests run against crablet-outbox classes, the coverage data is stored in wallet-outbox-service's `jacoco.exec`.

3. **Merge Strategy**: The `merge` goal combines multiple `jacoco.exec` files into a single execution data file, allowing comprehensive coverage reporting.

4. **Build Order**: Wallet modules must be built before crablet modules attempt to merge their coverage data.

## Next Steps

1. ✅ Update CI/CD to use the new coverage reports
2. ⏳ Set coverage thresholds based on new baseline
3. ⏳ Update coverage documentation
4. ⏳ Consider adding coverage quality gates

## References

- [Jacoco Maven Plugin](https://www.eclemma.org/jacoco/trunk/doc/maven.html)
- [Jacoco Documentation](https://www.jacoco.org/jacoco/trunk/doc/)
- [JaCoCo Alternatives Document](./JACOCO_COVERAGE_ALTERNATIVES.md)
- [JaCoCo Solution Document](./JACOCO_SOLUTION.md)

