package wallets.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture tests for query layer and read model constraints.
 * <p>
 * These tests ensure that the query layer remains properly isolated
 * and doesn't create unwanted dependencies on command handlers or feature slices.
 */
class QueryArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.wallets", "com.crablet.core");
    }

    @Test
    void query_layer_should_not_depend_on_feature_slices() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..query..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..features.deposit..", "..features.withdraw..", "..features.transfer..", "..features.openwallet..");

        rule.check(classes);
    }

    @Test
    void query_controllers_should_only_depend_on_query_and_infrastructure() {
        ArchRule rule = classes()
                .that().resideInAPackage("..query..")
                .and().haveSimpleNameEndingWith("Controller")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("..features.query..", "..infrastructure..", "com.crablet.core..", "io.swagger..", "java..", "org.springframework..", "org.slf4j..");

        rule.check(classes);
    }

    @Test
    void query_services_should_not_depend_on_command_handlers() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..query..")
                .and().haveSimpleNameEndingWith("Service")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Handler");

        rule.check(classes);
    }

    @Test
    void projectors_should_implement_StateProjector() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Projector")
                .and().resideOutsideOfPackage("com.crablet.core..")
                .should().implement("com.crablet.core.StateProjector");

        rule.check(classes);
    }

    @Test
    void query_repositories_should_be_in_query_package() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Repository")
                .and().resideInAPackage("..query..")
                .should().resideInAPackage("..query..");

        rule.check(classes);
    }

    @Test
    void query_dtos_should_be_in_query_package() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("DTO")
                .and().resideInAPackage("..query..")
                .should().resideInAPackage("..query..");

        rule.check(classes);
    }

    @Test
    void query_responses_should_be_in_query_package() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Response")
                .and().resideInAPackage("..query..")
                .should().resideInAPackage("..query..");

        rule.check(classes);
    }

    @Test
    void query_layer_should_not_depend_on_web_layer() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..query..")
                .and().haveSimpleNameNotEndingWith("Controller")
                .should().dependOnClassesThat().resideInAPackage("..web..");

        rule.check(classes);
    }

    @Test
    void query_controllers_should_have_rest_annotations() {
        ArchRule rule = classes()
                .that().resideInAPackage("..query..")
                .and().haveSimpleNameEndingWith("Controller")
                .should().beAnnotatedWith("org.springframework.web.bind.annotation.RestController");

        rule.check(classes);
    }
}