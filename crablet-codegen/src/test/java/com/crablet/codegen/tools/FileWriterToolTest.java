package com.crablet.codegen.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileWriterToolTest {

    private final FileWriterTool tool = new FileWriterTool();

    @Test
    void writesNormalNestedRelativePath(@TempDir Path out) throws Exception {
        String llm = "===FILE: com/example/Foo.java===\npublic class Foo {}\n===END FILE===";

        List<Path> written = tool.writeGeneratedFiles(llm, out);

        assertThat(written).hasSize(1);
        assertThat(written.get(0)).exists();
        assertThat(Files.readString(written.get(0))).contains("class Foo");
    }

    @Test
    void rejectsPathTraversalWithDotDot(@TempDir Path out) {
        String llm = "===FILE: ../../escape.java===\npwned\n===END FILE===";

        assertThatThrownBy(() -> tool.writeGeneratedFiles(llm, out))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Path traversal rejected");
    }

    @Test
    void rejectsAbsolutePath(@TempDir Path out) {
        String absolutePath = out.toAbsolutePath().getParent().resolve("sibling.java").toString();
        String llm = "===FILE: " + absolutePath + "===\npwned\n===END FILE===";

        assertThatThrownBy(() -> tool.writeGeneratedFiles(llm, out))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Path traversal rejected");
    }

    @Test
    void overwriteIsIdempotent(@TempDir Path out) throws Exception {
        String block = "===FILE: Foo.java===\nfirst\n===END FILE===";
        tool.writeGeneratedFiles(block, out);

        String block2 = "===FILE: Foo.java===\nsecond\n===END FILE===";
        tool.writeGeneratedFiles(block2, out);

        assertThat(Files.readString(out.resolve("Foo.java"))).isEqualTo("second\n");
    }

    @Test
    void stripsWholeContentMarkdownFence(@TempDir Path out) throws Exception {
        String llm = """
                ===FILE: com/example/Foo.java===
                ```java
                package com.example;

                public class Foo {}
                ```
                ===END FILE===
                """;

        tool.writeGeneratedFiles(llm, out);

        assertThat(Files.readString(out.resolve("com/example/Foo.java")))
                .isEqualTo("""
                        package com.example;

                        public class Foo {}
                        """);
    }

    @Test
    void returnsEmptyListAndWarnsWhenNoBlocksFound(@TempDir Path out) {
        List<Path> written = tool.writeGeneratedFiles("no blocks here", out);
        assertThat(written).isEmpty();
    }
}
