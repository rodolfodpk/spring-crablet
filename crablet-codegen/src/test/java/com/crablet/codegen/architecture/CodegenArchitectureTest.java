package com.crablet.codegen.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.crablet.codegen")
class CodegenArchitectureTest {

    @ArchTest
    static final ArchRule aiAgentsDoNotDependOnProviderSdks = noClasses()
            .that().resideInAPackage("..agents..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.anthropic..",
                    "org.springframework.ai.anthropic..",
                    "org.springframework.ai.openai.."
            );
}
