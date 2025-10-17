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
 * Architecture tests for command handler patterns and constraints.
 * 
 * These tests ensure that command handlers follow the proper patterns
 * and don't create unwanted dependencies or violate architectural boundaries.
 */
class CommandHandlerArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.wallets");
    }

    @Test
    void handlers_should_implement_CommandHandler() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Handler")
            .and().resideInAnyPackage("..features.deposit..", "..features.withdraw..", "..features.transfer..", "..features.openwallet..")
            .should().implement("com.crablet.core.CommandHandler");
        
        rule.check(classes);
    }

    @Test
    void handlers_should_only_depend_on_domain_and_infrastructure() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Handler")
            .and().resideInAnyPackage("..features.deposit..", "..features.withdraw..", "..features.transfer..", "..features.openwallet..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("..domain..", "..infrastructure..", "com.crablet.core..", "com.fasterxml.jackson..", "java..", "org.springframework..", "am.ik.yavi..", "jakarta.validation..")
            .orShould().resideInAnyPackage("..features.deposit..", "..features.withdraw..", "..features.transfer..", "..features.openwallet..");
        
        rule.check(classes);
    }

    @Test
    void handlers_should_not_depend_on_web_layer() {
        ArchRule rule = noClasses()
            .that().haveSimpleNameEndingWith("Handler")
            .and().resideInAnyPackage("..features.deposit..", "..features.withdraw..", "..features.transfer..", "..features.openwallet..")
            .should().dependOnClassesThat().resideInAPackage("..web..");
        
        rule.check(classes);
    }

    @Test
    void handlers_should_not_depend_on_feature_slices_other_than_their_own() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..features.deposit..")
            .and().haveSimpleNameEndingWith("Handler")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..features.withdraw..", "..features.transfer..", "..features.openwallet..");
        
        rule.check(classes);
    }

    @Test
    void withdraw_handlers_should_not_depend_on_other_feature_slices() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..features.withdraw..")
            .and().haveSimpleNameEndingWith("Handler")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..features.deposit..", "..features.transfer..", "..features.openwallet..");
        
        rule.check(classes);
    }

    @Test
    void transfer_handlers_should_not_depend_on_other_feature_slices() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..transfer..")
            .and().haveSimpleNameEndingWith("Handler")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..deposit..", "..withdraw..", "..openwallet..");
        
        rule.check(classes);
    }

    @Test
    void openwallet_handlers_should_not_depend_on_other_feature_slices() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..openwallet..")
            .and().haveSimpleNameEndingWith("Handler")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..deposit..", "..withdraw..", "..transfer..");
        
        rule.check(classes);
    }
}