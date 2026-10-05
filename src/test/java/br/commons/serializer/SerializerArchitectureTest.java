package br.commons.serializer;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Outside the serializer, only the root package is API ({@link Serializer}, {@link Json}, {@link Yaml}). The
 * sub-packages are public only because Java has no sub-package access; this rule keeps them internal.
 */
class SerializerArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("br.commons");
    }

    @Test
    void onlyTheRootPackageIsVisibleOutside() {
        noClasses().that().resideOutsideOfPackage("br.commons.serializer..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "br.commons.serializer.document..",
                        "br.commons.serializer.json..",
                        "br.commons.serializer.yaml..")
                .check(classes);
    }

    @Test
    void formatsDoNotSeeEachOther() {
        noClasses().that().resideInAPackage("br.commons.serializer.json..")
                .should().dependOnClassesThat().resideInAPackage("br.commons.serializer.yaml..")
                .check(classes);
        noClasses().that().resideInAPackage("br.commons.serializer.yaml..")
                .should().dependOnClassesThat().resideInAPackage("br.commons.serializer.json..")
                .check(classes);
    }

    @Test
    void theRepresentationDoesNotKnowFormats() {
        noClasses().that().resideInAPackage("br.commons.serializer.document..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "br.commons.serializer.json..",
                        "br.commons.serializer.yaml..")
                .check(classes);
    }
}
