package architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture tests for domain layer purity and patterns.
 * <p>
 * These tests ensure that the domain layer remains clean and doesn't depend
 * on infrastructure or web layers, maintaining proper separation of concerns.
 */
class DomainArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.wallets");
    }

    @Test
    void domain_should_not_depend_on_infrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

        rule.check(classes);
    }

    @Test
    void domain_should_not_depend_on_web_layer() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("..web..");

        rule.check(classes);
    }

    @Test
    void domain_should_not_depend_on_feature_slices() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..features.deposit..", "..features.withdraw..", "..features.transfer..", "..features.openwallet..");

        rule.check(classes);
    }

    @Test
    void domain_exceptions_should_extend_runtime_exception() {
        ArchRule rule = classes()
                .that().resideInAPackage("..domain.exception..")
                .should().beAssignableTo(RuntimeException.class);

        rule.check(classes);
    }

    @Test
    void commands_should_implement_WalletCommand() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Command")
                .and().resideInAnyPackage("..features.deposit..", "..features.withdraw..", "..features.transfer..", "..features.openwallet..")
                .should().implement("com.wallets.domain.WalletCommand");

        rule.check(classes);
    }


    @Test
    void domain_events_should_be_in_event_package() {
        ArchRule rule = classes()
                .that().implement("com.wallets.domain.event.WalletEvent")
                .should().resideInAPackage("..domain.event..");

        rule.check(classes);
    }

    @Test
    void domain_commands_should_be_in_feature_slices() {
        ArchRule rule = classes()
                .that().implement("com.wallets.domain.WalletCommand")
                .should().resideInAnyPackage("..features.deposit..", "..features.withdraw..", "..features.transfer..", "..features.openwallet..");

        rule.check(classes);
    }

    @Test
    void domain_projections_should_be_in_projections_package() {
        ArchRule rule = classes()
                .that().resideInAPackage("..domain..")
                .and().haveSimpleNameContaining("Projector")
                .should().resideInAPackage("..domain.projections..");

        rule.check(classes);
    }

    @Test
    void domain_should_only_depend_on_java_and_jackson() {
        ArchRule rule = classes()
                .that().resideInAPackage("..domain..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "com.fasterxml.jackson..", "com.wallets.domain..", "com.crablet.core..", "org.springframework..", "org.slf4j..");

        rule.check(classes);
    }
}