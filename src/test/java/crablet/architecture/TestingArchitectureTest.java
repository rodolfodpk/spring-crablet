package crablet.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Architecture tests for test organization and patterns.
 * <p>
 * These tests ensure that test classes follow proper organization patterns
 * and maintain consistency with the main codebase structure.
 */
class TestingArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
                .importPackages("crablet", "wallets");
    }

    @Test
    void all_tests_must_be_in_crablet_or_wallets_package() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Test")
                .or().haveSimpleNameEndingWith("IT")
                .should().resideInAnyPackage("crablet..", "wallets..");

        rule.check(classes);
    }


    @Test
    void crablet_test_classes_should_only_test_crablet_code() {
        ArchRule rule = classes()
                .that().resideInAPackage("crablet..")
                .and().haveSimpleNameEndingWith("Test")
                .or().resideInAPackage("crablet..")
                .and().haveSimpleNameEndingWith("IT")
                .should().onlyAccessClassesThat()
                .resideInAnyPackage(
                        "crablet..", 
                        "com.crablet..", 
                        "com.wallets..",  // Allow wallet classes for testing purposes
                        "java..", 
                        "org.springframework..",
                        "org.springframework.boot.test..",  // Spring Boot test classes
                        "org.springframework.web.client..",  // RestTemplate
                        "org.springframework.test.web.client..",  // TestRestTemplate
                        "org.springframework.jdbc.core..",  // JdbcTemplate
                        "org.junit..",
                        "org.testcontainers..",
                        "org.assertj..",
                        "org.mockito..",
                        "com.fasterxml.jackson..",
                        "org.slf4j..",
                        "jakarta.annotation..",
                        "io.micrometer.core..",
                        "io.github.resilience4j..",
                        "com.tngtech.archunit.."
                );

        rule.check(classes);
    }

    @Test
    void wallet_tests_can_use_crablet_framework() {
        ArchRule rule = classes()
                .that().resideInAPackage("wallets..")
                .should().onlyAccessClassesThat()
                .resideInAnyPackage(
                        "wallets..",
                        "crablet..",
                        "com.wallets..",
                        "com.crablet..",
                        "java..",
                        "org.springframework..",
                        "org.junit..",
                        "org.testcontainers..",
                        "org.assertj..",
                        "org.mockito..",
                        "com.fasterxml.jackson..",
                        "org.slf4j..",
                        "jakarta.annotation..",
                        "io.micrometer.core..",
                        "io.github.resilience4j..",
                        "com.tngtech.archunit.."
                );

        rule.check(classes);
    }
}