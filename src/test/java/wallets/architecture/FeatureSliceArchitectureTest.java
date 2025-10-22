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
 * Architecture tests for feature slice isolation and boundaries.
 * <p>
 * These tests ensure that feature slices (deposit, withdraw, transfer, openwallet)
 * remain isolated and don't create unwanted coupling between business capabilities.
 */
class FeatureSliceArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.wallets");
    }

    @Test
    void feature_slices_should_not_depend_on_each_other() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..features.deposit..")
                .should().dependOnClassesThat().resideInAPackage("..features.withdraw..")
                .andShould().dependOnClassesThat().resideInAPackage("..features.transfer..")
                .andShould().dependOnClassesThat().resideInAPackage("..features.openwallet..");

        rule.check(classes);
    }

    @Test
    void feature_slices_should_only_share_domain_and_infrastructure() {
        ArchRule rule = classes()
                .that().resideInAnyPackage("..features.deposit..", "..features.withdraw..", "..features.transfer..", "..features.openwallet..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("..domain..", "..infrastructure..", "..features.query..", "com.crablet.core..", "java..", "org.springframework..", "am.ik.yavi..", "jakarta.validation..", "com.fasterxml.jackson..", "org.slf4j..")
                .orShould().resideInAnyPackage("..features.deposit..", "..features.withdraw..", "..features.transfer..", "..features.openwallet..");

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