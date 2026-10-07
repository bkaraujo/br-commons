package br.commons.web;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Ready-made filters stand alone over the pure HTTP layer and the vocabulary of this root package
 * ({@link WebResponse}, {@link ErrorPage}): the server installs some of them, so they never see the server nor the
 * router back.
 */
class WebArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("br.commons");
    }

    @Test
    void filtersDoNotDependOnServerOrRouter() {
        noClasses().that().resideInAPackage("br.commons.web.filter..")
                .should().dependOnClassesThat().resideInAnyPackage("br.commons.web.server..", "br.commons.web.router..")
                .check(classes);
    }
}
