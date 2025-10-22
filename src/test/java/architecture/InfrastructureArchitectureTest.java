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
 * Architecture tests for infrastructure layer constraints and patterns.
 * <p>
 * These tests ensure that the infrastructure layer remains properly isolated
 * and doesn't create unwanted dependencies on feature slices or domain logic.
 */
class InfrastructureArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.wallets", "com.crablet.core", "com.crablet.core.impl");
    }

    @Test
    void infrastructure_should_not_depend_on_feature_slices() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..infrastructure..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..features.deposit..", "..features.withdraw..", "..features.transfer..", "..features.openwallet..");

        rule.check(classes);
    }

    @Test
    void event_store_should_only_be_in_infrastructure_or_crablet_impl() {
        ArchRule rule = classes()
                .that().implement("com.crablet.core.EventStore")
                .should().resideInAnyPackage("..infrastructure..", "com.crablet.core.impl..");

        rule.check(classes);
    }

    @Test
    void crablet_core_should_not_depend_on_implementations() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.crablet.core..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure.crablet.impl..");

        rule.check(classes);
    }

    @Test
    void config_classes_should_be_in_config_or_core_package() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Config")
                .should().resideInAnyPackage("..infrastructure.config..", "..core..", "..impl..");

        rule.check(classes);
    }

    @Test
    void web_layer_should_be_in_infrastructure() {
        ArchRule rule = classes()
                .that().resideInAPackage("..web..")
                .should().resideInAPackage("..infrastructure.web..");

        rule.check(classes);
    }

    @Test
    void global_exception_handler_should_be_in_web_package() {
        ArchRule rule = classes()
                .that().haveSimpleName("GlobalExceptionHandler")
                .should().resideInAPackage("..infrastructure.web..");

        rule.check(classes);
    }

    @Test
    void infrastructure_should_not_depend_on_query_layer() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..infrastructure..")
                .should().dependOnClassesThat().resideInAPackage("..features.query..");

        rule.check(classes);
    }

    @Test
    void crablet_implementations_should_implement_core_interfaces() {
        ArchRule rule = classes()
                .that().resideInAnyPackage("..infrastructure.crablet.core.impl..", "com.crablet.core.impl..")
                .and().haveSimpleNameEndingWith("EventStore")
                .should().implement("com.crablet.core.EventStore");

        rule.check(classes);
    }
}