package br.commons;

import br.commons.lang.Html4;
import br.commons.lang.Html5;
import br.commons.lang.Htmx;
import br.commons.lang.html.Html;
import br.commons.lang.html.core.Attribute;
import br.commons.lang.html.core.Node;
import br.commons.locale.i18n.Text;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.core.domain.properties.HasOwner;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import lombok.val;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Visible attributes ({@code title}, {@code placeholder}, {@code aria-label}, {@code alt}) take only a
 * {@link Text} (spec §2.5, §13.2). Naming an attribute freely is {@code protected} in
 * {@link Html}, for the versions; outside {@code br.commons.lang.html} (and {@code br.commons.lang.htmx}, which builds
 * the htmx attributes, and the facades {@code Html4}/{@code Html5}/{@code Htmx} of {@code br.commons.lang}) nobody may
 * extend it to reach that, nor build an {@link Attribute} directly. Everyone else uses the typed factories.
 */
class HtmlArchitectureTest {

    static final ArchRule ATTRIBUTES_BY_NAME_ONLY_IN_HTML = noClasses()
            .that().resideOutsideOfPackages("br.commons.lang", "br.commons.lang.html..", "br.commons.lang.htmx..")
            .should().beAssignableTo(Html.class)
            .orShould().callConstructor(Attribute.class, String.class, String.class)
            .because("a free string must not reach a visible attribute; use the typed factories of Html4/Html5");

    /**
     * Markup without escaping ({@link Html#raw}, {@link Node.Raw}) is written as is, so only the reviewed code of
     * {@code br.commons.lang.html} produces it (spec §14.1): {@code scriptText}/{@code styleText} with their own guard.
     */
    static final ArchRule RAW_ONLY_IN_HTML = noClasses()
            .that().resideOutsideOfPackage("br.commons.lang.html..")
            .should().callMethodWhere(JavaCall.Predicates.target(HasName.Predicates.name("raw"))
                    .and(JavaCall.Predicates.target(HasOwner.Predicates.With.owner(
                            JavaClass.Predicates.assignableTo(Html.class)))))
            .orShould().callConstructor(Node.Raw.class, String.class)
            .because("markup that is not escaped is an injection risk; render with the DSL or scriptText/styleText");

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
     * (each version, independent of the other) ← the facades {@link Html4}, {@link Html5} and {@link Htmx} of
     * {@code br.commons.lang}, which nothing below sees.
     */
    @Test
    void layersDependOnlyDownwards() {
        noClasses().that().resideInAPackage("br.commons.lang.html.core..")
                .should().dependOnClassesThat().resideInAnyPackage("br.commons.lang.html",
                        "br.commons.lang.html.form..", "br.commons.lang.html.html4..", "br.commons.lang.html.html5..")
                .check(CLASSES);
        noClasses().that().resideInAPackage("br.commons.lang.html.form..")
                .should().dependOnClassesThat().resideInAnyPackage("br.commons.lang.html",
                        "br.commons.lang.html.html4..", "br.commons.lang.html.html5..")
                .check(CLASSES);
        noClasses().that().resideInAPackage("br.commons.lang.html")
                .should().dependOnClassesThat().resideInAnyPackage("br.commons.lang.html.html4..",
                        "br.commons.lang.html.html5..")
                .check(CLASSES);
        noClasses().that().resideInAPackage("br.commons.lang.html.html4..")
                .should().dependOnClassesThat().resideInAPackage("br.commons.lang.html.html5..")
                .check(CLASSES);
        noClasses().that().resideInAPackage("br.commons.lang.html.html5..")
                .should().dependOnClassesThat().resideInAPackage("br.commons.lang.html.html4..")
                .check(CLASSES);
        noClasses().that().resideInAnyPackage("br.commons.lang.html..", "br.commons.lang.htmx..")
                .should().dependOnClassesThat().belongToAnyOf(Html4.class, Html5.class, Htmx.class)
                .check(CLASSES);
    }

    /** htmx sits on top of HTML and of the pure HTTP layer; neither knows it, and it does not know web. */
    @Test
    void htmxIsALayerAboveHtmlAndHttp() {
        noClasses().that().resideInAnyPackage("br.commons.lang.html..", "br.commons.protocol..")
                .should().dependOnClassesThat().resideInAPackage("br.commons.lang.htmx..")
                .check(CLASSES);
        noClasses().that().resideInAPackage("br.commons.lang.htmx..")
                .should().dependOnClassesThat().resideInAPackage("br.commons.web..")
                .check(CLASSES);
    }

    @Test
    void onlyTheHtmlPackageWritesRawMarkup() {
        RAW_ONLY_IN_HTML.check(CLASSES);
    }

    @Test
    void catchesAViewThatWritesRawMarkup() {
        val classes = new ClassFileImporter().importClasses(RawView.class);
        val report = RAW_ONLY_IN_HTML.evaluate(classes).getFailureReport().getDetails();
        assertTrue(report.stream().anyMatch(line -> line.contains(RawView.class.getSimpleName() + ".viaFactory")));
        assertTrue(report.stream().anyMatch(line -> line.contains(RawView.class.getSimpleName() + ".viaRecord")));
    }

    @Test
    void catchesAViewThatNamesAnAttribute() {
        val classes = new ClassFileImporter().importClasses(FreeAltView.class, FreeTitleHtml.class);
        val report = ATTRIBUTES_BY_NAME_ONLY_IN_HTML.evaluate(classes).getFailureReport().getDetails();
        assertTrue(report.stream().anyMatch(line -> line.contains(FreeAltView.class.getSimpleName())));
        assertTrue(report.stream().anyMatch(line -> line.contains(FreeTitleHtml.class.getSimpleName())));
    }

    /** A view outside {@code br.commons.lang} that builds an attribute with a free string. */
    static final class FreeAltView {
        Node render() {
            return Html.element("img", new Attribute("alt", "free text"));
        }
    }

    /** A view outside {@code br.commons.lang} that writes markup without escaping, by the factory and directly. */
    static final class RawView {
        static Node viaFactory() {
            return Html.raw("<b>free</b>");
        }

        static Node viaRecord() {
            return new Node.Raw("<b>free</b>");
        }
    }

    /** A subclass outside {@code br.commons.lang}, to reach the {@code protected} free-name attribute. */
    static final class FreeTitleHtml extends Html {
        static Node render() {
            return div(attribute("title", "free text"));
        }
    }
}
