package com.crablet.codegen.agents;

import com.crablet.codegen.model.EventModel;
import com.crablet.codegen.model.ScenarioSpec;
import com.crablet.codegen.model.ScenarioStepSpec;
import com.crablet.codegen.tools.TemplateLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

@Component
public class ScenariosAgent {

    @SuppressWarnings("unused")
    private final TemplateLoader templates;

    public ScenariosAgent(TemplateLoader templates) {
        this.templates = templates;
    }

    public void generate(EventModel model, Path outputDir) {
        if (model.scenarios().isEmpty()) return;
        System.out.println("[ScenariosAgent] Generating scenario test scaffolding...");

        Path testOutputDir = deriveTestOutputDir(outputDir);
        String testPackage = model.basePackage() + ".test";

        for (ScenarioSpec scenario : model.scenarios()) {
            String base = toJavaIdentifier(scenario.name());
            String className = base + "ScenarioTest";
            String methodName = toLowerCamelCase(base);
            Path targetFile = packageToPath(testOutputDir, testPackage, className);
            String content = generateTestFile(testPackage, className, scenario.name(), methodName, scenario.steps());

            try {
                Files.createDirectories(targetFile.getParent());
                Files.writeString(targetFile, content, StandardOpenOption.CREATE_NEW);
                System.out.println("  wrote " + targetFile);
            } catch (FileAlreadyExistsException e) {
                System.out.println("[ScenariosAgent] Skipping " + className + " — already exists (user-owned)");
            } catch (IOException e) {
                throw new RuntimeException("Failed to write " + targetFile, e);
            }
        }
    }

    Path deriveTestOutputDir(Path outputDir) {
        Path abs = outputDir.toAbsolutePath().normalize();
        int count = abs.getNameCount();
        for (int i = 0; i <= count - 3; i++) {
            if ("src".equals(abs.getName(i).toString())
                    && "main".equals(abs.getName(i + 1).toString())
                    && "java".equals(abs.getName(i + 2).toString())) {
                Path base = abs.getRoot();
                for (int j = 0; j <= i; j++) {
                    Path segment = abs.getName(j);
                    base = (base != null) ? base.resolve(segment) : segment;
                }
                return base.resolve("test").resolve("java");
            }
        }
        throw new IllegalArgumentException(
                "Cannot derive test output path from: " + outputDir
                        + " — expected a path containing src/main/java");
    }

    private Path packageToPath(Path testOutputDir, String packageName, String className) {
        Path dir = testOutputDir;
        for (String segment : packageName.split("\\.")) {
            dir = dir.resolve(segment);
        }
        return dir.resolve(className + ".java");
    }

    private String generateTestFile(String testPackage, String className, String scenarioName,
                                     String methodName, List<ScenarioStepSpec> steps) {
        return """
                package %s;

                import org.junit.jupiter.api.DisplayName;
                import org.junit.jupiter.api.Test;

                class %s {

                    @Test
                    @DisplayName("%s")
                    void %s() {
                %s    }
                }
                """.formatted(testPackage, className, escapeDisplayName(scenarioName), methodName, renderSteps(steps));
    }

    private String renderSteps(List<ScenarioStepSpec> steps) {
        if (steps.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (ScenarioStepSpec step : steps) {
            String keyword = step.keyword();
            String text = sanitizeStepText(step.text());
            switch (keyword) {
                case "Given", "When", "Then" -> {
                    if (!first) sb.append("\n");
                    sb.append("        // ").append(keyword).append(": ").append(text).append("\n");
                }
                case "And" -> sb.append("        // And: ").append(text).append("\n");
                case "But" -> sb.append("        // But: ").append(text).append("\n");
                default -> sb.append("        // ").append(keyword).append(": ").append(text).append("\n");
            }
            first = false;
        }
        return sb.toString();
    }

    private String escapeDisplayName(String name) {
        return name.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String sanitizeStepText(String text) {
        return text.replace("\r\n", " ").replace("\r", " ").replace("\n", " ");
    }

    private String toLowerCamelCase(String pascalCase) {
        if (pascalCase.isEmpty()) return pascalCase;
        return Character.toLowerCase(pascalCase.charAt(0)) + pascalCase.substring(1);
    }

    private String toJavaIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return "Generated";
        }
        StringBuilder result = new StringBuilder();
        boolean upperNext = true;
        for (char c : value.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                result.append(upperNext ? Character.toUpperCase(c) : c);
                upperNext = false;
            } else {
                upperNext = true;
            }
        }
        if (result.isEmpty()) {
            return "Generated";
        }
        if (!Character.isJavaIdentifierStart(result.charAt(0))) {
            result.insert(0, "Generated");
        }
        return result.toString();
    }
}
