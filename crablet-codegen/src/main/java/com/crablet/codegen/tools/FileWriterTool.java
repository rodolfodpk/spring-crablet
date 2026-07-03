package com.crablet.codegen.tools;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Component
public class FileWriterTool {

    public Path writeGeneratedFile(String relativePath, String content, Path outputDir) {
        Path target = safeResolve(relativePath, outputDir);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("  wrote " + target);
            return target;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + target, e);
        }
    }

    static Path safeResolve(String relativePath, Path outputDir) {
        Path safeRoot = outputDir.toAbsolutePath().normalize();
        Path target = safeRoot.resolve(relativePath).normalize();
        if (!target.startsWith(safeRoot)) {
            throw new IllegalArgumentException(
                    "Path traversal rejected: '" + relativePath + "' escapes output directory");
        }
        return target;
    }
}
