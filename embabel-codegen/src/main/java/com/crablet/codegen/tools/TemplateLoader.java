package com.crablet.codegen.tools;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class TemplateLoader {

    private final Path claudeMdPath;

    public TemplateLoader(@Value("${codegen.claude-md-path:CLAUDE.md}") String claudeMdPath) {
        this.claudeMdPath = Path.of(claudeMdPath).toAbsolutePath();
    }

    /**
     * Loads the template section whose H3 header contains templateName.
     * E.g. load("View Projector") extracts "### View Projector Template" section.
     */
    public String load(String templateName) {
        String content;
        try {
            content = Files.readString(claudeMdPath);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read CLAUDE.md at " + claudeMdPath, e);
        }
        String[] lines = content.split("\n");
        int start = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("### ") && lines[i].contains(templateName)) {
                start = i;
                break;
            }
        }
        if (start == -1) {
            return "";
        }
        int end = lines.length;
        for (int i = start + 1; i < lines.length; i++) {
            if (lines[i].startsWith("### ")) {
                end = i;
                break;
            }
        }
        return Arrays.stream(lines, start, end).collect(Collectors.joining("\n"));
    }
}
