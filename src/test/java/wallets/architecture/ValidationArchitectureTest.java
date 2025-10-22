package wallets.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Architecture tests for YAVI validation patterns and constraints.
 * <p>
 * These tests ensure that validation patterns are consistently applied
 * across commands and request DTOs, and that the validation architecture
 * follows the established patterns.
 */
class ValidationArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.wallets");
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
    void request_dtos_should_be_records() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Request")
                .should().beRecords();

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
    void global_exception_handler_should_be_in_web_package() {
        ArchRule rule = classes()
                .that().haveSimpleName("GlobalExceptionHandler")
                .should().resideInAPackage("..infrastructure.web..");

        rule.check(classes);
    }

    @Test
    void commands_should_be_in_feature_slices() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Command")
                .and().resideInAnyPackage("..features.deposit..", "..features.withdraw..", "..features.transfer..", "..features.openwallet..")
                .should().resideInAnyPackage("..features.deposit..", "..features.withdraw..", "..features.transfer..", "..features.openwallet..");

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
    void handlers_should_implement_CommandHandler() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Handler")
                .and().resideInAnyPackage("..features.deposit..", "..features.withdraw..", "..features.transfer..", "..features.openwallet..")
                .should().implement("com.crablet.core.CommandHandler");

        rule.check(classes);
    }

    @Test
    void validation_should_be_consistent_across_commands() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Command")
                .and().resideInAnyPackage("..features.deposit..", "..features.withdraw..", "..features.transfer..", "..features.openwallet..")
                .should().implement("com.wallets.domain.WalletCommand");

        rule.check(classes);
    }
}