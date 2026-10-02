package br.commons.http;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The pure HTTP layer does not see the conveniences of {@code br.commons.web} nor serializes body.
 * (Adapted from br-community: pure ArchUnit under JUnit; serialization here is {@code br.commons.serializer}.)
 */
class HTTPArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("br.commons");
    }

    @Test
    void httpDoesNotDependOnWeb() {
        noClasses().that().resideInAPackage("br.commons.http..")
                .should().dependOnClassesThat().resideInAPackage("br.commons.web..")
                .check(classes);
    }

    @Test
    void httpDoesNotSerialize() {
        noClasses().that().resideInAPackage("br.commons.http..")
                .should().dependOnClassesThat().resideInAPackage("tools.jackson..")
                .orShould().dependOnClassesThat().resideInAPackage("br.commons.serializer..")
                .check(classes);
    }
}
