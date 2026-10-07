package br.commons.serializer;

import br.commons.lang.Json;
import br.commons.lang.Yaml;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Outside the serializer, only {@link Serializer} is API, built by the facades {@link Json} and {@link Yaml}. The
 * formats behind them ({@code br.commons.lang.json}, {@code br.commons.lang.yaml}) live in {@code br.commons.lang}
 * beside the other languages and are the only ones allowed to build the representation
 * ({@code br.commons.serializer.document}). Sub-packages are public only because Java has no sub-package access;
 * these rules keep them internal.
 */
class SerializerArchitectureTest {

    private static final String[] FORMATS = {"br.commons.lang.json..", "br.commons.lang.yaml.."};

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("br.commons");
    }

    @Test
    void onlyTheFormatsSeeTheRepresentation() {
        noClasses().that().resideOutsideOfPackages("br.commons.serializer..", FORMATS[0], FORMATS[1])
                .should().dependOnClassesThat().resideInAPackage("br.commons.serializer.document..")
                .check(classes);
    }

    @Test
    void formatsAreReachedOnlyThroughTheFacades() {
        noClasses().that().resideOutsideOfPackages("br.commons.lang", FORMATS[0], FORMATS[1])
                .should().dependOnClassesThat().resideInAnyPackage(FORMATS)
                .check(classes);
    }

    @Test
    void formatsDoNotSeeEachOther() {
        noClasses().that().resideInAPackage(FORMATS[0])
                .should().dependOnClassesThat().resideInAPackage(FORMATS[1])
                .check(classes);
        noClasses().that().resideInAPackage(FORMATS[1])
                .should().dependOnClassesThat().resideInAPackage(FORMATS[0])
                .check(classes);
    }

    @Test
    void theRepresentationDoesNotKnowFormats() {
        noClasses().that().resideInAPackage("br.commons.serializer..")
                .should().dependOnClassesThat().resideInAPackage("br.commons.lang..")
                .check(classes);
    }
}
