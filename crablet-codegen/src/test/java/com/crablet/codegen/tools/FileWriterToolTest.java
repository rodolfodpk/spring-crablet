package com.crablet.codegen.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileWriterToolTest {

    private final FileWriterTool tool = new FileWriterTool();

    @Test
    void writesNormalNestedRelativePath(@TempDir Path out) throws Exception {
        tool.writeGeneratedFile("com/example/Foo.java", "public class Foo {}\n", out);

        Path written = out.resolve("com/example/Foo.java");
        assertThat(written).exists();
        assertThat(Files.readString(written)).contains("class Foo");
    }

    @Test
    void rejectsPathTraversalWithDotDot(@TempDir Path out) {
        assertThatThrownBy(() -> tool.writeGeneratedFile("../../escape.java", "pwned\n", out))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Path traversal rejected");
    }

    @Test
    void rejectsAbsolutePath(@TempDir Path out) {
        String absolutePath = out.toAbsolutePath().getParent().resolve("sibling.java").toString();
        assertThatThrownBy(() -> tool.writeGeneratedFile(absolutePath, "pwned\n", out))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Path traversal rejected");
    }

    @Test
    void overwriteIsIdempotent(@TempDir Path out) throws Exception {
        tool.writeGeneratedFile("Foo.java", "first\n", out);
        tool.writeGeneratedFile("Foo.java", "second\n", out);

        assertThat(Files.readString(out.resolve("Foo.java"))).isEqualTo("second\n");
    }
}
