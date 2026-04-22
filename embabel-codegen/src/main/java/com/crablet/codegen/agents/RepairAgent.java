package com.crablet.codegen.agents;

import com.crablet.codegen.pipeline.CompileError;
import com.crablet.codegen.tools.FileWriterTool;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RepairAgent {

    private final AnthropicService anthropic;

    public RepairAgent(AnthropicService anthropic) {
        this.anthropic = anthropic;
    }

    public void fix(List<CompileError> errors, Path outputDir) {
        System.out.println("[RepairAgent] Fixing " + errors.size() + " compile errors...");

        Map<Path, List<CompileError>> byFile = errors.stream()
                .collect(Collectors.groupingBy(CompileError::file));

        byFile.forEach((file, fileErrors) -> {
            String currentContent;
            try {
                currentContent = Files.readString(file);
            } catch (IOException e) {
                System.err.println("[RepairAgent] Cannot read " + file + ": " + e.getMessage());
                return;
            }

            String system = """
                    You are a Java expert fixing compilation errors in spring-crablet generated code.
                    Return the COMPLETE corrected file in this exact format — nothing else:
                    ===FILE: %s===
                    <corrected file content>
                    ===END FILE===
                    """.formatted(outputDir.relativize(file));

            String user = """
                    File: %s

                    Errors:
                    %s

                    Current content:
                    %s
                    """.formatted(
                    file.getFileName(),
                    fileErrors.stream()
                            .map(e -> "  Line " + e.line() + ": " + e.message())
                            .collect(Collectors.joining("\n")),
                    currentContent
            );

            String fixed = anthropic.complete(system, user);
            // Extract just the content between the FILE markers and write it
            int start = fixed.indexOf('\n');
            int end = fixed.lastIndexOf("===END FILE===");
            if (start >= 0 && end > start) {
                String fixedContent = fixed.substring(start + 1, end);
                try {
                    Files.writeString(file, fixedContent);
                    System.out.println("[RepairAgent] Repaired " + file.getFileName());
                } catch (IOException e) {
                    System.err.println("[RepairAgent] Cannot write " + file + ": " + e.getMessage());
                }
            } else {
                System.err.println("[RepairAgent] Could not parse fixed content for " + file.getFileName());
            }
        });
    }
}
