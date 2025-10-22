package architecture;

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
                .importPackages("crablet", "unit", "integration", "testutils", "architecture");
    }

    @Test
    void test_utils_should_be_accessible_to_all_tests() {
        ArchRule rule = classes()
                .that().resideInAPackage("..testutils..")
                .should().bePublic()
                .allowEmptyShould(true); // Allow empty since we moved testutils to new structure

        rule.check(classes);
    }

    @Test
    void crablet_tests_should_be_in_crablet_package() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Test")
                .and().resideInAPackage("..crablet..")
                .should().resideInAnyPackage("crablet..");

        rule.check(classes);
    }

    @Test
    void unit_tests_should_be_in_unit_package() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Test")
                .and().resideInAPackage("..unit..")
                .and().resideOutsideOfPackage("crablet.unit..")
                .should().resideInAnyPackage("unit..");

        rule.check(classes);
    }

    @Test
    void integration_tests_should_be_in_integration_package() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("IT")
                .and().resideInAPackage("..integration..")
                .should().resideInAnyPackage("integration..", "crablet.integration..");

        rule.check(classes);
    }

    @Test
    void architecture_tests_should_be_in_architecture_package() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("ArchitectureTest")
                .should().resideInAPackage("architecture");

        rule.check(classes);
    }
}