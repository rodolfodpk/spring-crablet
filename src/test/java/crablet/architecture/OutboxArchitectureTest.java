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
 * Architecture tests for Crablet Outbox package constraints.
 * <p>
 * These tests ensure proper separation between outbox interfaces and implementation details:
 * - crablet.outbox: Pure interfaces and contracts, no Spring dependencies
 * - crablet.outbox.impl: Spring-specific implementations, can use Spring classes
 */
class OutboxArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.crablet.outbox", "com.crablet.outbox.impl");
    }

    @Test
    void outbox_interfaces_should_not_depend_on_spring() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.crablet.outbox..")
                .and().resideOutsideOfPackage("com.crablet.outbox.impl..")
                .should().dependOnClassesThat()
                .resideInAPackage("org.springframework..");

        rule.check(classes);
    }

    @Test
    void outbox_interfaces_should_not_have_spring_annotations() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.crablet.outbox..")
                .and().resideOutsideOfPackage("com.crablet.outbox.impl..")
                .should().beAnnotatedWith("org.springframework.stereotype.Component")
                .orShould().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Service")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Repository")
                .orShould().beAnnotatedWith("org.springframework.context.annotation.Configuration")
                .orShould().beAnnotatedWith("org.springframework.boot.context.properties.ConfigurationProperties");

        rule.check(classes);
    }

    @Test
    void outbox_interfaces_should_not_depend_on_implementations() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.crablet.outbox..")
                .and().resideOutsideOfPackage("com.crablet.outbox.impl..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.crablet.outbox.impl..");

        rule.check(classes);
    }

    @Test
    void outbox_impl_can_use_spring_classes() {
        ArchRule rule = classes()
                .that().resideInAPackage("com.crablet.outbox.impl..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "com.crablet.outbox..",         // Outbox interfaces
                        "com.crablet.outbox.impl..",    // Other implementations
                        "com.crablet.core..",           // Core interfaces
                        "com.crablet.core.impl..",      // Core implementations
                        "org.springframework..",        // Spring framework
                        "java..",                       // Java standard library
                        "javax.sql..",                  // JDBC
                        "com.fasterxml.jackson..",      // JSON serialization
                        "org.slf4j..",                  // Logging
                        "com.zaxxer.hikari..",          // Connection pooling
                        "org.postgresql..",             // PostgreSQL driver
                        "io.github.resilience4j..",     // Resilience4j annotations
                        "io.micrometer.core..",         // Micrometer metrics
                        "jakarta.annotation.."         // Jakarta annotations
                );

        rule.check(classes);
    }

    @Test
    void outbox_publishers_should_implement_OutboxPublisher() {
        ArchRule rule = classes()
                .that().resideInAPackage("com.crablet.outbox.impl.publishers..")
                .and().haveSimpleNameEndingWith("Publisher")
                .and().areNotInnerClasses()
                .and().haveSimpleNameNotContaining("GlobalStatistics")
                .should().implement("com.crablet.outbox.OutboxPublisher");

        rule.check(classes);
    }

    @Test
    void outbox_processor_implementations_should_implement_OutboxProcessor() {
        ArchRule rule = classes()
                .that().resideInAPackage("com.crablet.outbox.impl..")
                .and().haveSimpleNameEndingWith("Processor")
                .and().areNotInnerClasses()
                .should().implement("com.crablet.outbox.OutboxProcessor");

        rule.check(classes);
    }

    @Test
    void outbox_should_not_depend_on_wallets() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.crablet.outbox..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.wallets..");

        rule.check(classes);
    }

    @Test
    void outbox_interfaces_should_only_contain_interfaces_and_contracts() {
        ArchRule rule = classes()
                .that().resideInAPackage("com.crablet.outbox..")
                .and().resideOutsideOfPackage("com.crablet.outbox.impl..")
                .should().beInterfaces()
                .orShould().beAssignableTo(Exception.class)
                .orShould().beAssignableTo(Enum.class)
                .orShould().haveSimpleNameEndingWith("Config")
                .orShould().haveSimpleNameEndingWith("Pair")
                .orShould().haveSimpleNameEndingWith("Mode")
                .orShould().haveSimpleNameEndingWith("Builder");

        rule.check(classes);
    }

    @Test
    void outbox_impl_should_have_spring_annotations() {
        ArchRule rule = classes()
                .that().resideInAPackage("com.crablet.outbox.impl..")
                .and().areNotInnerClasses()
                .and().haveSimpleNameEndingWith("Processor")
                .should().beAnnotatedWith("org.springframework.stereotype.Component");

        rule.check(classes);
    }
}
