package com.crablet.codegen.tools;

import com.crablet.codegen.pipeline.CompileError;
import com.crablet.codegen.pipeline.CompileResult;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MavenTool {

    // [ERROR] /abs/path/File.java:[42,10] some error message
    private static final Pattern ERROR_PATTERN = Pattern.compile(
            "\\[ERROR\\] ([^:]+\\.java):\\[(\\d+),\\d+\\] (.+)"
    );

    public CompileResult compile(Path outputDir) {
        Path projectRoot = findProjectRoot(outputDir);
        if (projectRoot == null) {
            return new CompileResult(false, List.of(
                    new CompileError(outputDir, 0, "No pom.xml found — cannot compile")));
        }
        try {
            String mvnCmd = resolveWrapper(projectRoot);
            ProcessBuilder pb = new ProcessBuilder(mvnCmd, "compile", "-q")
                    .directory(projectRoot.toFile())
                    .redirectErrorStream(true);
            Process process = pb.start();
            List<String> outputLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputLines.add(line);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode == 0) return CompileResult.ok();
            List<CompileError> errors = parseErrors(outputLines);
            if (errors.isEmpty()) {
                errors = List.of(new CompileError(projectRoot, 0,
                        "Compilation failed (exit=" + exitCode + "). Output: "
                                + String.join("\n", outputLines)));
            }
            return new CompileResult(false, errors);
        } catch (Exception e) {
            return new CompileResult(false, List.of(
                    new CompileError(projectRoot, 0, "Failed to run Maven: " + e.getMessage())));
        }
    }

    private List<CompileError> parseErrors(List<String> lines) {
        List<CompileError> errors = new ArrayList<>();
        for (String line : lines) {
            Matcher m = ERROR_PATTERN.matcher(line);
            if (m.find()) {
                errors.add(new CompileError(
                        Path.of(m.group(1)),
                        Integer.parseInt(m.group(2)),
                        m.group(3).trim()
                ));
            }
        }
        return errors;
    }

    private Path findProjectRoot(Path dir) {
        Path candidate = dir;
        while (candidate != null) {
            if (Files.exists(candidate.resolve("pom.xml"))) return candidate;
            candidate = candidate.getParent();
        }
        return null;
    }

    private String resolveWrapper(Path projectRoot) {
        if (Files.exists(projectRoot.resolve("mvnw"))) return "./mvnw";
        return "mvn";
    }
}
