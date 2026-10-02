package br.commons;

import br.commons.html.Html;
import br.commons.html.core.Attribute;
import br.commons.html.core.Node;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import lombok.val;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Visible attributes ({@code title}, {@code placeholder}, {@code aria-label}, {@code alt}) take only a
 * {@link br.commons.i18n.Text} (spec §2.5, §13.2). Naming an attribute freely is {@code protected} in
 * {@link Html}, for the versions; outside {@code br.commons.html} (and {@code br.commons.htmx}, which builds the htmx
 * attributes) nobody may extend it to reach that, nor build an {@link Attribute} directly. Everyone else uses the
 * typed factories.
 */
class HtmlArchitectureTest {

    static final ArchRule ATTRIBUTES_BY_NAME_ONLY_IN_HTML = noClasses()
            .that().resideOutsideOfPackages("br.commons.html..", "br.commons.htmx..")
            .should().beAssignableTo(Html.class)
            .orShould().callConstructor(Attribute.class, String.class, String.class)
            .because("a free string must not reach a visible attribute; use the typed factories of Html4/Html5");

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("br.commons");

    @Test
    void onlyTheHtmlPackageNamesAttributes() {
        ATTRIBUTES_BY_NAME_ONLY_IN_HTML.check(CLASSES);
    }

    /**
     * Layers without cycles: {@code core} (node model and rendering) ← {@code form} (fields and server check)
     * ← {@code html} (common DSL, the {@code Dialect} strategy and its context) ← {@code html4} / {@code html5}
     * (each version, independent of the other).
     */
    @Test
    void layersDependOnlyDownwards() {
        noClasses().that().resideInAPackage("br.commons.html.core..")
                .should().dependOnClassesThat().resideInAnyPackage("br.commons.html", "br.commons.html.form..",
                        "br.commons.html.html4..", "br.commons.html.html5..")
                .check(CLASSES);
        noClasses().that().resideInAPackage("br.commons.html.form..")
                .should().dependOnClassesThat().resideInAnyPackage("br.commons.html", "br.commons.html.html4..",
                        "br.commons.html.html5..")
                .check(CLASSES);
        noClasses().that().resideInAPackage("br.commons.html")
                .should().dependOnClassesThat().resideInAnyPackage("br.commons.html.html4..", "br.commons.html.html5..")
                .check(CLASSES);
        noClasses().that().resideInAPackage("br.commons.html.html4..")
                .should().dependOnClassesThat().resideInAPackage("br.commons.html.html5..")
                .check(CLASSES);
        noClasses().that().resideInAPackage("br.commons.html.html5..")
                .should().dependOnClassesThat().resideInAPackage("br.commons.html.html4..")
                .check(CLASSES);
    }

    /** htmx sits on top of HTML and of the pure HTTP layer; neither knows it, and it does not know web. */
    @Test
    void htmxIsALayerAboveHtmlAndHttp() {
        noClasses().that().resideInAnyPackage("br.commons.html..", "br.commons.http..")
                .should().dependOnClassesThat().resideInAPackage("br.commons.htmx..")
                .check(CLASSES);
        noClasses().that().resideInAPackage("br.commons.htmx..")
                .should().dependOnClassesThat().resideInAPackage("br.commons.web..")
                .check(CLASSES);
    }

    @Test
    void catchesAViewThatNamesAnAttribute() {
        val classes = new ClassFileImporter().importClasses(FreeAltView.class, FreeTitleHtml.class);
        val report = ATTRIBUTES_BY_NAME_ONLY_IN_HTML.evaluate(classes).getFailureReport().getDetails();
        assertTrue(report.stream().anyMatch(line -> line.contains(FreeAltView.class.getSimpleName())));
        assertTrue(report.stream().anyMatch(line -> line.contains(FreeTitleHtml.class.getSimpleName())));
    }

    /** A view outside {@code br.commons.html} that builds an attribute with a free string. */
    static final class FreeAltView {
        Node render() {
            return Html.element("img", new Attribute("alt", "free text"));
        }
    }

    /** A subclass outside {@code br.commons.html}, to reach the {@code protected} free-name attribute. */
    static final class FreeTitleHtml extends Html {
        static Node render() {
            return div(attribute("title", "free text"));
        }
    }
}
