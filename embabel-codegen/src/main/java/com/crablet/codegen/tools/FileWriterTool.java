package com.crablet.codegen.tools;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FileWriterTool {

    private static final Pattern FILE_BLOCK = Pattern.compile(
            "===FILE: ([^=]+)===\\s*\n(.*?)===END FILE===",
            Pattern.DOTALL
    );

    /**
     * Parses LLM output for ===FILE: path=== ... ===END FILE=== blocks
     * and writes each to outputDir, creating parent directories as needed.
     *
     * @return list of written file paths (relative to outputDir)
     */
    public List<Path> writeGeneratedFiles(String llmOutput, Path outputDir) {
        List<Path> written = new ArrayList<>();
        Matcher m = FILE_BLOCK.matcher(llmOutput);
        while (m.find()) {
            String relativePath = m.group(1).trim();
            String fileContent = m.group(2);
            Path target = outputDir.resolve(relativePath).normalize();
            try {
                Files.createDirectories(target.getParent());
                Files.writeString(target, fileContent);
                written.add(target);
                System.out.println("  wrote " + target);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write " + target, e);
            }
        }
        if (written.isEmpty()) {
            System.err.println("[WARN] No ===FILE: ...=== blocks found in LLM output. Raw response follows:\n"
                    + llmOutput.substring(0, Math.min(500, llmOutput.length())));
        }
        return written;
    }
}
