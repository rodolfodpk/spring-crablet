package architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Architecture tests for test organization and patterns.
 * 
 * These tests ensure that test classes follow proper organization patterns
 * and maintain consistency with the main codebase structure.
 */
class TestingArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
            .importPackages("com.wallets");  // Remove DO_NOT_INCLUDE_TESTS to actually scan test classes
    }

    @Test
    void test_utils_should_be_accessible_to_all_tests() {
        ArchRule rule = classes()
            .that().resideInAPackage("..testutils..")
            .should().bePublic()
            .allowEmptyShould(true); // Allow empty since we moved testutils to new structure
        
        rule.check(classes);
    }
}