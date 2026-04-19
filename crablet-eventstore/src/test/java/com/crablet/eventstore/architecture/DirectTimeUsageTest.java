package com.crablet.eventstore.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

class DirectTimeUsageTest {

    private static final Pattern DIRECT_TIME_USAGE = Pattern.compile(
            "Instant\\.now\\(|LocalDate\\.now\\(|LocalDateTime\\.now\\(|OffsetDateTime\\.now\\("
                    + "|ZonedDateTime\\.now\\(|System\\.currentTimeMillis\\("
    );

    private static final Set<String> FRAMEWORK_MODULES = Set.of(
            "crablet-eventstore",
            "crablet-commands",
            "crablet-commands-web",
            "crablet-outbox",
            "crablet-event-poller",
            "crablet-views",
            "crablet-automations",
            "crablet-metrics-micrometer",
            "crablet-test-support"
    );

    private static final Map<String, Set<String>> ALLOWED_DIRECT_TIME_USAGE = Map.of(
            "crablet-eventstore/src/main/java/com/crablet/eventstore/internal/ClockProviderImpl.java",
            Set.of("return Instant.now(clock);"),
            "crablet-event-poller/src/main/java/com/crablet/eventpoller/internal/EventProcessorImpl.java",
            Set.of("Instant.now().plusMillis(sanitizedDelayMs)"),
            "crablet-event-poller/src/main/java/com/crablet/eventpoller/internal/sharedfetch/SharedFetchModuleProcessor.java",
            Set.of("taskScheduler.schedule(this::runSharedCycle, Instant.now())")
    );

    @Test
    void frameworkBusinessTimeGoesThroughClockProvider() throws IOException {
        Path repositoryRoot = findRepositoryRoot();

        List<String> violations;
        try (Stream<Path> files = Files.list(repositoryRoot)) {
            violations = files
                    .filter(Files::isDirectory)
                    .filter(path -> FRAMEWORK_MODULES.contains(path.getFileName().toString()))
                    .flatMap(DirectTimeUsageTest::mainJavaFiles)
                    .flatMap(path -> directTimeViolations(repositoryRoot, path).stream())
                    .toList();
        }

        if (!violations.isEmpty()) {
            fail("Direct wall-clock usage found in framework src/main. "
                    + "Business timestamps must use ClockProvider; deadline/scheduler usage must be allowlisted.\n"
                    + String.join("\n", violations));
        }
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("codecov.yml"))
                    && Files.isDirectory(current.resolve("crablet-eventstore"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root");
    }

    private static Stream<Path> mainJavaFiles(Path moduleRoot) {
        Path mainJava = moduleRoot.resolve("src/main/java");
        if (!Files.exists(mainJava)) {
            return Stream.empty();
        }
        try {
            return Files.walk(mainJava)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan " + mainJava, e);
        }
    }

    private static List<String> directTimeViolations(Path repositoryRoot, Path sourceFile) {
        String relativePath = repositoryRoot.relativize(sourceFile).toString();
        Set<String> allowedSnippets = ALLOWED_DIRECT_TIME_USAGE.getOrDefault(relativePath, Set.of());

        try {
            List<String> lines = Files.readAllLines(sourceFile);
            return Stream.iterate(0, index -> index + 1)
                    .limit(lines.size())
                    .map(index -> violation(relativePath, index + 1, lines.get(index), allowedSnippets))
                    .filter(violation -> !violation.isBlank())
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + sourceFile, e);
        }
    }

    private static String violation(String relativePath, int lineNumber, String line, Set<String> allowedSnippets) {
        if (!DIRECT_TIME_USAGE.matcher(line).find()) {
            return "";
        }
        boolean allowed = allowedSnippets.stream().anyMatch(line::contains);
        if (allowed) {
            return "";
        }
        return relativePath + ":" + lineNumber + ": " + line.trim();
    }
}
