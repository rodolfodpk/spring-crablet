package com.crablet.codegen;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = CodegenApp.class, args = "help")
class CodegenAppContextTest {

    @Test
    void contextLoadsWithoutLlmCredentials() {
    }
}
