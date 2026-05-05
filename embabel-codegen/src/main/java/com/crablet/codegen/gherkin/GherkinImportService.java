package com.crablet.codegen.gherkin;

import com.crablet.codegen.model.EventModel;
import com.crablet.codegen.model.ScenarioSpec;
import com.crablet.codegen.model.ScenarioStepSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class GherkinImportService {

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    public EventModel importFeature(Path featureFile, String domainOverride, String basePackageOverride) {
        try {
            ParsedFeature parsed = parse(featureFile);
            String domain = notBlank(domainOverride) ? domainOverride : inferDomain(parsed.featureName(), featureFile);
            String basePackage = notBlank(basePackageOverride)
                    ? basePackageOverride
                    : inferBasePackage(domain, featureFile);

            return new EventModel(
                    domain,
                    basePackage,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    parsed.scenarios(),
                    null,
                    null
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to read Gherkin feature: " + featureFile, e);
        }
    }

    public String importToYaml(Path featureFile, String domainOverride, String basePackageOverride) {
        try {
            EventModel model = importFeature(featureFile, domainOverride, basePackageOverride);
            return yaml.writeValueAsString(model);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize imported feature model: " + featureFile, e);
        }
    }

    public void writeImportedModel(Path featureFile, Path outputFile, String domainOverride, String basePackageOverride) {
        try {
            String yamlText = importToYaml(featureFile, domainOverride, basePackageOverride);
            Path target = outputFile.toAbsolutePath().normalize();
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, yamlText, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write imported feature model to " + outputFile, e);
        }
    }

    private ParsedFeature parse(Path featureFile) throws IOException {
        List<String> lines = Files.readAllLines(featureFile, StandardCharsets.UTF_8);
        String featureName = null;
        List<ScenarioSpec> scenarios = new ArrayList<>();
        ScenarioBuilder current = null;
        List<String> pendingTags = new ArrayList<>();
        boolean inExamples = false;

        for (String raw : lines) {
            String line = raw.stripTrailing();
            String trimmed = line.trim();

            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (trimmed.startsWith("@")) {
                pendingTags.addAll(parseTags(trimmed));
                continue;
            }
            if (trimmed.startsWith("Feature:")) {
                featureName = trimmed.substring("Feature:".length()).trim();
                continue;
            }
            if (trimmed.startsWith("Scenario Outline:") || trimmed.startsWith("Scenario:")) {
                if (current != null) {
                    scenarios.add(current.build());
                }
                current = new ScenarioBuilder(stripPrefix(trimmed, "Scenario Outline:", "Scenario:"),
                        new ArrayList<>(pendingTags));
                pendingTags.clear();
                inExamples = false;
                continue;
            }
            if (trimmed.startsWith("Examples:")) {
                inExamples = true;
                continue;
            }
            if (inExamples) {
                continue;
            }
            if (current == null) {
                continue;
            }

            StepPrefix prefix = stepPrefix(trimmed);
            if (prefix != null) {
                current.steps.add(new ScenarioStepSpec(prefix.keyword(), prefix.text()));
            }
        }

        if (current != null) {
            scenarios.add(current.build());
        }
        if (scenarios.isEmpty()) {
            throw new IllegalArgumentException("No scenarios found in " + featureFile);
        }
        if (featureName == null || featureName.isBlank()) {
            featureName = featureSlug(featureFile);
        }
        return new ParsedFeature(featureName, scenarios);
    }

    private List<String> parseTags(String line) {
        String[] parts = line.trim().split("\\s+");
        List<String> tags = new ArrayList<>();
        for (String part : parts) {
            if (part.startsWith("@") && part.length() > 1) {
                tags.add(part.substring(1));
            }
        }
        return tags;
    }

    private StepPrefix stepPrefix(String line) {
        for (String keyword : new String[] {"Given", "When", "Then", "And", "But"}) {
            if (line.startsWith(keyword + " ")) {
                return new StepPrefix(keyword, line.substring(keyword.length()).trim());
            }
        }
        return null;
    }

    private String inferDomain(String featureName, Path featureFile) {
        String source = notBlank(featureName) ? featureName : featureSlug(featureFile);
        StringBuilder out = new StringBuilder();
        for (String token : source.split("[^A-Za-z0-9]+")) {
            if (token.isBlank()) continue;
            out.append(Character.toUpperCase(token.charAt(0)));
            if (token.length() > 1) out.append(token.substring(1).toLowerCase());
        }
        return out.isEmpty() ? "ImportedFeature" : out.toString();
    }

    private String inferBasePackage(String domain, Path featureFile) {
        String slug = featureSlug(featureFile).replaceAll("[^a-z0-9]+", "");
        if (slug.isBlank()) {
            slug = domain.replaceAll("[^A-Za-z0-9]+", "").toLowerCase();
        }
        return "com.example." + slug.toLowerCase();
    }

    private String featureSlug(Path featureFile) {
        String name = featureFile.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name;
    }

    private String stripPrefix(String value, String... prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return value.substring(prefix.length()).trim();
            }
        }
        return value;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private record ParsedFeature(String featureName, List<ScenarioSpec> scenarios) {}

    private static final class ScenarioBuilder {
        private final String name;
        private final List<String> tags;
        private final List<ScenarioStepSpec> steps = new ArrayList<>();

        private ScenarioBuilder(String name, List<String> tags) {
            this.name = name;
            this.tags = tags;
        }

        private ScenarioSpec build() {
            return new ScenarioSpec(name, tags, new ArrayList<>(steps));
        }
    }

    private record StepPrefix(String keyword, String text) {}
}
