package crablet.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture tests for Crablet package constraints.
 * <p>
 * These tests ensure proper separation between core interfaces and implementation details:
 * - crablet.core: Pure interfaces and contracts, no Spring dependencies
 * - crablet.impl: Spring-specific implementations, can use Spring classes
 */
class CrabletArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.crablet.core", "com.crablet.core.impl");
    }

    @Test
    void crablet_core_should_not_depend_on_spring() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.crablet.core..")
                .and().resideOutsideOfPackage("com.crablet.core.impl..")
                .should().dependOnClassesThat()
                .resideInAPackage("org.springframework..");

        rule.check(classes);
    }

    @Test
    void crablet_core_should_not_have_spring_annotations() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.crablet.core..")
                .and().resideOutsideOfPackage("com.crablet.core.impl..")
                .should().beAnnotatedWith("org.springframework.stereotype.Component")
                .orShould().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Service")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Repository")
                .orShould().beAnnotatedWith("org.springframework.context.annotation.Configuration")
                .orShould().beAnnotatedWith("org.springframework.boot.context.properties.ConfigurationProperties");

        rule.check(classes);
    }

    @Test
    void crablet_core_should_not_depend_on_implementations() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.crablet.core..")
                .and().resideOutsideOfPackage("com.crablet.core.impl..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.crablet.core.impl..");

        rule.check(classes);
    }

    @Test
    void crablet_impl_can_use_spring_classes() {
        ArchRule rule = classes()
                .that().resideInAPackage("com.crablet.core.impl..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "com.crablet.core..",           // Core interfaces
                        "com.crablet.core.impl..",      // Other implementations
                        "org.springframework..",      // Spring framework
                        "java..",                      // Java standard library
                        "javax.sql..",                 // JDBC
                        "com.fasterxml.jackson..",    // JSON serialization
                        "org.slf4j..",                 // Logging
                        "com.zaxxer.hikari..",         // Connection pooling
                        "org.postgresql..",             // PostgreSQL driver
                        "io.github.resilience4j.."     // Resilience4j annotations
                );

        rule.check(classes);
    }

    @Test
    void crablet_impl_should_have_spring_annotations() {
        ArchRule rule = classes()
                .that().resideInAPackage("com.crablet.core.impl..")
                .and().haveSimpleNameEndingWith("EventStore")
                .and().areNotInnerClasses()
                .or().haveSimpleNameEndingWith("CommandExecutor")
                .and().resideInAPackage("com.crablet.core.impl..")
                .or().haveSimpleNameEndingWith("Config")
                .should().beAnnotatedWith("org.springframework.stereotype.Component");

        rule.check(classes);
    }

    @Test
    void crablet_implementations_should_implement_core_interfaces() {
        ArchRule rule = classes()
                .that().resideInAPackage("com.crablet.core.impl..")
                .and().haveSimpleNameEndingWith("EventStore")
                .should().implement("com.crablet.core.EventStore");

        rule.check(classes);
    }

    @Test
    void crablet_command_executor_should_implement_core_interface() {
        ArchRule rule = classes()
                .that().resideInAPackage("com.crablet.core.impl..")
                .and().haveSimpleNameEndingWith("CommandExecutor")
                .should().implement("com.crablet.core.CommandExecutor");

        rule.check(classes);
    }

    @Test
    void crablet_core_should_only_contain_interfaces_and_contracts() {
        ArchRule rule = classes()
                .that().resideInAPackage("com.crablet.core..")
                .and().resideOutsideOfPackage("com.crablet.core.impl..")
                .should().beInterfaces()
                .orShould().beAssignableTo(Exception.class)
                .orShould().beAssignableTo(Enum.class)
                .orShould().haveSimpleNameEndingWith("Result")
                .orShould().haveSimpleNameEndingWith("Condition")
                .orShould().haveSimpleNameEndingWith("Event")
                .orShould().haveSimpleNameEndingWith("Query")
                .orShould().haveSimpleNameEndingWith("Tag")
                .orShould().haveSimpleNameEndingWith("Cursor")
                .orShould().haveSimpleNameEndingWith("SequenceNumber")
                .orShould().haveSimpleNameEndingWith("Builder")
                .orShould().haveSimpleNameEndingWith("Violation")
                .orShould().haveSimpleNameEndingWith("Item")
                .orShould().haveSimpleNameEndingWith("Context");

        rule.check(classes);
    }

    @Test
    void crablet_impl_should_not_depend_on_wallet_packages() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.crablet.core.impl..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.wallets..");

        rule.check(classes);
    }
}
