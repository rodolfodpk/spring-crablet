package com.crablet.eventstore.architecture;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import static org.junit.jupiter.api.Assertions.fail;

class UserFacingModuleBoundaryTest {

    private static final Set<String> USER_FACING_MODULES = Set.of(
            "crablet-eventstore",
            "crablet-commands",
            "crablet-commands-web",
            "crablet-outbox",
            "crablet-event-poller",
            "crablet-views",
            "crablet-automations",
            "crablet-metrics-micrometer"
    );

    private static final Set<String> FORBIDDEN_PACKAGE_REFERENCES = Set.of(
            "com.crablet.examples",
            "com.crablet.wallet"
    );

    private static final Set<String> EXAMPLE_ARTIFACTS = Set.of(
            "shared-examples-domain",
            "wallet-example-app",
            "docs-samples"
    );

    @Test
    void userFacingMainSourcesDoNotReferenceExampleApplicationPackages() throws IOException {
        Path repositoryRoot = findRepositoryRoot();

        List<String> violations;
        try (Stream<Path> files = Files.list(repositoryRoot)) {
            violations = files
                    .filter(Files::isDirectory)
                    .filter(path -> USER_FACING_MODULES.contains(path.getFileName().toString()))
                    .flatMap(UserFacingModuleBoundaryTest::mainJavaFiles)
                    .flatMap(path -> forbiddenPackageReferenceViolations(repositoryRoot, path).stream())
                    .toList();
        }

        if (!violations.isEmpty()) {
            fail("User-facing Crablet modules must not reference example application packages from src/main.\n"
                    + String.join("\n", violations));
        }
    }

    @Test
    void userFacingModulesDoNotDependOnExampleArtifactsAtRuntime()
            throws IOException, ParserConfigurationException, SAXException {
        Path repositoryRoot = findRepositoryRoot();

        List<String> violations;
        try (Stream<Path> files = Files.list(repositoryRoot)) {
            violations = files
                    .filter(Files::isDirectory)
                    .filter(path -> USER_FACING_MODULES.contains(path.getFileName().toString()))
                    .flatMap(path -> runtimeExampleDependencyViolations(repositoryRoot, path).stream())
                    .toList();
        }

        if (!violations.isEmpty()) {
            fail("User-facing Crablet modules must not depend on example artifacts at runtime.\n"
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

    private static List<String> forbiddenPackageReferenceViolations(Path repositoryRoot, Path sourceFile) {
        String relativePath = repositoryRoot.relativize(sourceFile).toString();

        try {
            List<String> lines = Files.readAllLines(sourceFile);
            return Stream.iterate(0, index -> index + 1)
                    .limit(lines.size())
                    .flatMap(index -> forbiddenPackageReferences(relativePath, index + 1, lines.get(index)).stream())
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + sourceFile, e);
        }
    }

    private static List<String> forbiddenPackageReferences(String relativePath, int lineNumber, String line) {
        return FORBIDDEN_PACKAGE_REFERENCES.stream()
                .filter(line::contains)
                .map(reference -> relativePath + ":" + lineNumber + ": " + reference)
                .toList();
    }

    private static List<String> runtimeExampleDependencyViolations(Path repositoryRoot, Path moduleRoot) {
        Path pom = moduleRoot.resolve("pom.xml");
        if (!Files.exists(pom)) {
            return List.of();
        }

        try {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom.toFile());
            NodeList dependencies = document.getElementsByTagName("dependency");
            return Stream.iterate(0, index -> index + 1)
                    .limit(dependencies.getLength())
                    .map(dependencies::item)
                    .map(UserFacingModuleBoundaryTest::dependencyViolation)
                    .filter(violation -> !violation.isBlank())
                    .map(violation -> repositoryRoot.relativize(pom) + ": " + violation)
                    .toList();
        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new RuntimeException("Failed to read " + pom, e);
        }
    }

    private static String dependencyViolation(org.w3c.dom.Node dependency) {
        String artifactId = childText(dependency, "artifactId");
        if (!EXAMPLE_ARTIFACTS.contains(artifactId)) {
            return "";
        }

        String scope = childText(dependency, "scope");
        if ("test".equals(scope)) {
            return "";
        }

        return artifactId + " dependency has runtime scope"
                + (scope.isBlank() ? "" : " (" + scope + ")");
    }

    private static String childText(org.w3c.dom.Node node, String childName) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (childName.equals(child.getNodeName())) {
                return child.getTextContent().trim();
            }
        }
        return "";
    }
}
