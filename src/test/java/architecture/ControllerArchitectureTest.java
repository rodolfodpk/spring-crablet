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
 * Architecture tests for controller patterns and REST constraints.
 * 
 * These tests ensure that controllers follow proper REST patterns,
 * are properly organized, and maintain clean architectural boundaries.
 */
class ControllerArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.wallets");
    }

    @Test
    void controllers_should_only_be_in_controller_packages() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().resideInAnyPackage("..features.deposit..", "..features.withdraw..", "..features.transfer..", "..features.openwallet..", "..features.query..");
        
        rule.check(classes);
    }

    @Test
    void controllers_should_have_rest_annotations() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().beAnnotatedWith("org.springframework.web.bind.annotation.RestController");
        
        rule.check(classes);
    }

    @Test
    void request_dtos_should_be_in_feature_slices() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Request")
            .should().resideInAnyPackage("..features.deposit..", "..features.withdraw..", "..features.transfer..", "..features.openwallet..");
        
        rule.check(classes);
    }

    @Test
    void request_dtos_should_be_records() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Request")
            .should().beRecords();
        
        rule.check(classes);
    }

    @Test
    void response_dtos_should_be_in_query_package() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Response")
            .should().resideInAPackage("..features.query..");
        
        rule.check(classes);
    }

    @Test
    void controllers_should_only_depend_on_their_feature_slice() {
        ArchRule rule = classes()
            .that().resideInAPackage("..features.deposit..")
            .and().haveSimpleNameEndingWith("Controller")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("..features.deposit..", "..domain..", "..infrastructure..", "com.crablet.core..", "java..", "org.springframework..", "jakarta.validation..", "io.github.resilience4j..", "org.slf4j..");
        
        rule.check(classes);
    }

    @Test
    void withdraw_controllers_should_not_depend_on_other_feature_slices() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..features.withdraw..")
            .and().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..features.deposit..", "..features.transfer..", "..features.openwallet..");
        
        rule.check(classes);
    }

    @Test
    void transfer_controllers_should_not_depend_on_other_feature_slices() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..features.transfer..")
            .and().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..features.deposit..", "..features.withdraw..", "..features.openwallet..");
        
        rule.check(classes);
    }

    @Test
    void openwallet_controllers_should_not_depend_on_other_feature_slices() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..features.openwallet..")
            .and().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..features.deposit..", "..features.withdraw..", "..features.transfer..");
        
        rule.check(classes);
    }
}