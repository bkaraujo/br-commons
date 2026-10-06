package br.commons.html;

import br.commons.html.core.Attribute;
import br.commons.html.core.Content;
import br.commons.html.core.Keyword;
import br.commons.html.core.Node;
import br.commons.html.form.Field;
import br.commons.html.form.InputType;
import br.commons.html.form.Option;
import br.commons.locale.i18n.Errors;
import br.commons.locale.i18n.Text;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The HTML DSL common to HTML 4.01 Strict and HTML (WHATWG): the elements and attributes both define and neither
 * deprecates. Each version extends it with what is only its own ({@code html4.Html4}, {@code html5.Html5}); since
 * static members are inherited, one static import brings the whole version:
 *
 * <pre>{@code
 * import static br.commons.html.html5.Html5.*;
 *
 * document(html(lang("pt-BR"), body(header(h1(text(msg.title()))), main(form(method(FormMethod.POST))))))
 * }</pre>
 *
 * A view meant for both versions imports this class and passes the {@link Dialect} where it matters (form controls,
 * {@code meta} charset, document). A form control built from a {@link Field} gets its {@code type} and its browser
 * constraints from the dialect: this class is the context of that strategy.
 *
 * <p>Element names follow the spec; where a name is a Java keyword or clashes, the attribute is renamed
 * ({@code class} → {@link #cls}, {@code for} → {@link #htmlFor}, {@code http-equiv} → {@link #httpEquiv}).
 * Attributes a person reads ({@code title}, {@code alt}, {@code label}, {@code abbr}) take a {@link Text}.
 */
@NullMarked
public abstract class Html {

    /** Extended only by the versions. */
    protected Html() {}

    // ---------- document

    /** The whole page, with the doctype of the dialect. */
    public static String document(Dialect dialect, Node root) {
        return dialect.document(root);
    }

    /** The {@code meta} that declares the encoding, as the dialect writes it. */
    public static Node.Element metaCharset(Dialect dialect, String encoding) {
        return dialect.metaCharset(encoding);
    }

    // ---------- nodes

    public static Node text(Text text) {
        return new Node.Text(text.value());
    }

    public static Node fragment(Node... nodes) {
        return new Node.Fragment(List.of(nodes));
    }

    public static <T> Node each(Iterable<T> items, Function<? super T, ? extends Node> render) {
        val nodes = new ArrayList<Node>();
        items.forEach(item -> nodes.add(render.apply(item)));
        return new Node.Fragment(List.copyOf(nodes));
    }

    public static Node when(boolean condition, Supplier<? extends Node> node) {
        return condition ? node.get() : new Node.Fragment(List.of());
    }

    /** Markup without escaping. Only {@code br.commons.html} may call it ({@code HtmlArchitectureTest}, spec §14.1). */
    public static Node raw(String markup) {
        return new Node.Raw(markup);
    }

    /**
     * Body of a {@code <script>}: JavaScript is raw text, so {@link #text} (which escapes {@code <}, {@code &}, quotes)
     * would break it. Not escaped; refuses {@code </script} (any case), the only sequence that ends the element early.
     * Use only with code written by the developer — never with data; prefer {@code script(src(...))}.
     */
    public static Node scriptText(String javascript) {
        return rawText("script", javascript);
    }

    /** Body of a {@code <style>}: same rule as {@link #scriptText}, refusing {@code </style}. */
    public static Node styleText(String css) {
        return rawText("style", css);
    }

    private static Node rawText(String tag, String content) {
        if (content.toLowerCase(Locale.ROOT).contains("</" + tag)) {
            throw new IllegalArgumentException(Errors.get().rawContentClosingTag(tag).value());
        }
        return new Node.Raw(content);
    }

    /** Any element by tag, for what has no factory (custom elements, SVG children). */
    public static Node.Element element(String tag, Content... content) {
        val attributes = new ArrayList<Attribute>();
        val children = new ArrayList<Node>();
        for (val item : content) {
            switch (item) {
                case Attribute attribute -> attributes.add(attribute);
                case Node node -> children.add(node);
            }
        }
        return new Node.Element(tag, List.copyOf(attributes), List.copyOf(children));
    }

    // ---------- attribute building, for the versions

    /**
     * Any attribute, by name; only the versions use it. A visible attribute never gets a free string: views use
     * the typed factories, which take a {@link Text} where a person reads it ({@code HtmlArchitectureTest}).
     */
    protected static Attribute attribute(String name, String value) {
        return new Attribute(name, value);
    }

    protected static Attribute attribute(String name, Keyword value) {
        return new Attribute(name, value.token());
    }

    protected static Attribute attribute(String name, int value) {
        return new Attribute(name, String.valueOf(value));
    }

    /** A boolean attribute: present means true. */
    protected static Attribute flag(String name) {
        return new Attribute(name, null);
    }

    // ---------- global attributes (core attributes of HTML 4: id, class, style, title, lang, dir)

    /**
     * The {@code id} attribute: the element's ID.
     * <p>On every HTML element (global attribute).
     * Value: Text.
     * HTML 4.01: On {@code all elements but base}, {@code head}, {@code html}, {@code meta}, {@code script},
     * {@code style}, {@code title}: document-wide unique id (type {@code ID}, default {@code #IMPLIED}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/dom.html#the-id-attribute">HTML: id on every element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/global.html#adef-id">HTML 4.01: id</a>
     */
    public static Attribute id(String value) { return attribute("id", value); }

    /**
     * The {@code class} attribute: classes to which the element belongs.
     * <p>On every HTML element (global attribute).
     * Value: Set of space-separated tokens.
     * HTML 4.01: On {@code all elements but base}, {@code basefont}, {@code head}, {@code html}, {@code meta},
     * {@code param}, {@code script}, {@code style}, {@code title}: space-separated list of classes (type
     * {@code CDATA}, default {@code #IMPLIED}).
     * <p>Named {@code cls}: {@code class} is a Java keyword.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/dom.html#classes">HTML: class on every element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/global.html#adef-class">HTML 4.01: class</a>
     */
    public static Attribute cls(String value) { return attribute("class", value); }

    /**
     * The {@code style} attribute: presentational and formatting instructions.
     * <p>On every HTML element (global attribute).
     * Value: CSS declarations.
     * HTML 4.01: On {@code all elements but base}, {@code basefont}, {@code head}, {@code html}, {@code meta},
     * {@code param}, {@code script}, {@code style}, {@code title}: associated style info (type {@code %StyleSheet;},
     * default {@code #IMPLIED}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/dom.html#attr-style">HTML: style on every element</a>
     * @see <a href="https://www.w3.org/TR/html401/present/styles.html#adef-style">HTML 4.01: style</a>
     */
    public static Attribute style(String css) { return attribute("style", css); }

    /**
     * The {@code title} attribute, on each element where it applies:
     * <ul>
     * <li>every HTML element (global attribute) — Advisory information for the element. Value: Text.</li>
     * <li>{@code abbr}, {@code dfn} — Full term or expansion of abbreviation. Value: Text.</li>
     * <li>{@code input} — Description of pattern (when used with pattern attribute). Value: Text.</li>
     * <li>{@code link} — Title of the link. Value: Text.</li>
     * <li>{@code link}, {@code style} — CSS style sheet set name. Value: Text.</li>
     * </ul>
     * HTML 4.01: On {@code all elements but base}, {@code basefont}, {@code head}, {@code html}, {@code meta},
     * {@code param}, {@code script}, {@code title}: advisory title (type {@code %Text;}, default {@code #IMPLIED}).
     * <p>Takes a {@code Text}: a person reads it, so it is never a free string.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/dom.html#attr-title">HTML: title on every element</a>
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#attr-abbr-title">HTML: title on abbr</a>
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#attr-dfn-title">HTML: title on dfn</a>
     * @see <a href="https://html.spec.whatwg.org/dev/input.html#attr-input-title">HTML: title on input</a>
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#attr-link-title">HTML: title on link</a>
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#attr-style-title">HTML: title on style</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/global.html#adef-title">HTML 4.01: title</a>
     */
    public static Attribute title(Text text) { return attribute("title", text.value()); }

    /**
     * The {@code lang} attribute: language of the element.
     * <p>On every HTML element (global attribute).
     * Value: Valid BCP 47 language tag or the empty string.
     * HTML 4.01: On {@code all elements but applet}, {@code base}, {@code basefont}, {@code br}, {@code frame},
     * {@code frameset}, {@code iframe}, {@code param}, {@code script}: language code (type {@code %LanguageCode;},
     * default {@code #IMPLIED}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/dom.html#attr-lang">HTML: lang on every element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/dirlang.html#adef-lang">HTML 4.01: lang</a>
     */
    public static Attribute lang(String bcp47) { return attribute("lang", bcp47); }

    /**
     * The {@code dir} attribute, on each element where it applies:
     * <ul>
     * <li>every HTML element (global attribute) — The text directionality of the element. Value: "ltr"; "rtl";
     * "auto".</li>
     * <li>{@code bdo} — The text directionality of the element. Value: "ltr"; "rtl".</li>
     * </ul>
     * HTML 4.01:
     * <ul>
     * <li>On {@code all elements but applet}, {@code base}, {@code basefont}, {@code bdo}, {@code br}, {@code frame},
     * {@code frameset}, {@code iframe}, {@code param}, {@code script}: direction for weak/neutral text (type
     * {@code (ltr | rtl)}, default {@code #IMPLIED})</li>
     * <li>On {@code bdo}: directionality (type {@code (ltr | rtl)}, default {@code #REQUIRED})</li>
     * </ul>
     *
     * @see <a href="https://html.spec.whatwg.org/dev/dom.html#attr-dir">HTML: dir on every element</a>
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-bdo-element">HTML: dir on bdo</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/dirlang.html#adef-dir">HTML 4.01: dir</a>
     */
    public static Attribute dir(Direction direction) { return attribute("dir", direction); }

    /**
     * The {@code accesskey} attribute: keyboard shortcut to activate or focus element.
     * <p>On every HTML element (global attribute).
     * Value: Ordered set of unique space-separated tokens, none of which are identical to another, each consisting of
     * one code point in length.
     * HTML 4.01: On {@code a}, {@code area}, {@code button}, {@code input}, {@code label}, {@code legend},
     * {@code textarea}: accessibility key character (type {@code %Character;}, default {@code #IMPLIED}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/interaction.html#the-accesskey-attribute">HTML: accesskey on every element</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#adef-accesskey">HTML 4.01: accesskey</a>
     */
    public static Attribute accesskey(String key) { return attribute("accesskey", key); }

    /**
     * The {@code tabindex} attribute: whether the element is focusable and sequentially focusable, and the relative
     * order of the element for the purposes of sequential focus navigation.
     * <p>On every HTML element (global attribute).
     * Value: Valid integer.
     * HTML 4.01: On {@code a}, {@code area}, {@code button}, {@code input}, {@code object}, {@code select},
     * {@code textarea}: position in tabbing order (type {@code NUMBER}, default {@code #IMPLIED}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/interaction.html#attr-tabindex">HTML: tabindex on every element</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#adef-tabindex">HTML 4.01: tabindex</a>
     */
    public static Attribute tabindex(int index) { return attribute("tabindex", index); }

    // ---------- element attributes

    /**
     * The {@code abbr} attribute: alternative label to use for the header cell when referencing the cell in other
     * contexts.
     * <p>On {@code th}.
     * Value: Text.
     * HTML 4.01: On {@code td}, {@code th}: abbreviation for header cell (type {@code %Text;}, default
     * {@code #IMPLIED}).
     * <p>Takes a {@code Text}: a person reads it, so it is never a free string.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/tables.html#attr-th-abbr">HTML: abbr on th</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#adef-abbr">HTML 4.01: abbr</a>
     */
    public static Attribute abbr(Text text) { return attribute("abbr", text.value()); }

    /**
     * The {@code accept} attribute: hint for expected file type in file upload controls.
     * <p>On {@code input}.
     * Value: Set of comma-separated tokens consisting of valid MIME type strings with no parameters or audio/,
     * video/, or image/.
     * HTML 4.01: On {@code form}, {@code input}: list of MIME types for file upload (type {@code %ContentTypes;},
     * default {@code #IMPLIED}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/input.html#attr-input-accept">HTML: accept on input</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#adef-accept">HTML 4.01: accept</a>
     */
    public static Attribute accept(String types) { return attribute("accept", types); }

    /**
     * The {@code accept-charset} attribute: character encodings to use for form submission.
     * <p>On {@code form}.
     * Value: ASCII case-insensitive match for "UTF-8".
     * HTML 4.01: On {@code form}: list of supported charsets (type {@code %Charsets;}, default {@code #IMPLIED}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/forms.html#attr-form-accept-charset">HTML: accept-charset on form</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#adef-accept-charset">HTML 4.01: accept-charset</a>
     */
    public static Attribute acceptCharset(String charset) { return attribute("accept-charset", charset); }

    /**
     * The {@code action} attribute: uRL to use for form submission.
     * <p>On {@code form}.
     * Value: Valid non-empty URL potentially surrounded by spaces.
     * HTML 4.01: On {@code form}: server-side form handler (type {@code %URI;}, default {@code #REQUIRED}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-control-infrastructure.html#attr-fs-action">HTML: action on form</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#adef-action">HTML 4.01: action</a>
     */
    public static Attribute action(String url) { return attribute("action", url); }

    /**
     * The {@code alt} attribute: replacement text for use when images are not available.
     * <p>On {@code area}, {@code img}, {@code input}.
     * Value: Text.
     * HTML 4.01:
     * <ul>
     * <li>On {@code applet}: short description (type {@code %Text;}, default {@code #IMPLIED}, deprecated)</li>
     * <li>On {@code area}, {@code img}: short description (type {@code %Text;}, default {@code #REQUIRED})</li>
     * <li>On {@code input}: short description (type {@code CDATA}, default {@code #IMPLIED})</li>
     * </ul>
     * <p>Takes a {@code Text}: a person reads it, so it is never a free string.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/image-maps.html#attr-area-alt">HTML: alt on area</a>
     * @see <a href="https://html.spec.whatwg.org/dev/embedded-content.html#attr-img-alt">HTML: alt on img</a>
     * @see <a href="https://html.spec.whatwg.org/dev/input.html#attr-input-alt">HTML: alt on input</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/objects.html#adef-alt">HTML 4.01: alt</a>
     */
    public static Attribute alt(Text text) { return attribute("alt", text.value()); }

    /**
     * The {@code charset} attribute: character encoding declaration.
     * <p>On {@code meta}.
     * Value: "utf-8".
     * HTML 4.01: On {@code a}, {@code link}, {@code script}: char encoding of linked resource (type
     * {@code %Charset;}, default {@code #IMPLIED}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#attr-meta-charset">HTML: charset on meta</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/links.html#adef-charset">HTML 4.01: charset</a>
     */
    public static Attribute charset(String charset) { return attribute("charset", charset); }

    /**
     * The {@code checked} attribute: whether the control is checked.
     * <p>On {@code input}.
     * Value: Boolean attribute.
     * HTML 4.01: On {@code input}: for radio buttons and check boxes (type {@code (checked)}, default
     * {@code #IMPLIED}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/input.html#attr-input-checked">HTML: checked on input</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#adef-checked">HTML 4.01: checked</a>
     */
    public static Attribute checked() { return flag("checked"); }

    /**
     * The {@code cite} attribute: link to the source of the quotation or more information about the edit.
     * <p>On {@code blockquote}, {@code del}, {@code ins}, {@code q}.
     * Value: Valid URL potentially surrounded by spaces.
     * HTML 4.01:
     * <ul>
     * <li>On {@code blockquote}, {@code q}: URI for source document or msg (type {@code %URI;}, default
     * {@code #IMPLIED})</li>
     * <li>On {@code del}, {@code ins}: info on reason for change (type {@code %URI;}, default {@code #IMPLIED})</li>
     * </ul>
     *
     * @see <a href="https://html.spec.whatwg.org/dev/grouping-content.html#attr-blockquote-cite">HTML: cite on blockquote</a>
     * @see <a href="https://html.spec.whatwg.org/dev/edits.html#attr-mod-cite">HTML: cite on del</a>
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#attr-q-cite">HTML: cite on q</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/text.html#adef-cite-Q">HTML 4.01: cite</a>
     */
    public static Attribute cite(String url) { return attribute("cite", url); }

    /**
     * The {@code cols} attribute: maximum number of characters per line.
     * <p>On {@code textarea}.
     * Value: Valid non-negative integer greater than zero.
     * HTML 4.01:
     * <ul>
     * <li>On {@code frameset}: list of lengths, default: 100% (1 col) (type {@code %MultiLengths;}, default
     * {@code #IMPLIED})</li>
     * <li>On {@code textarea}:  (type {@code NUMBER}, default {@code #REQUIRED})</li>
     * </ul>
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-textarea-cols">HTML: cols on textarea</a>
     * @see <a href="https://www.w3.org/TR/html401/present/frames.html#adef-cols-FRAMESET">HTML 4.01: cols</a>
     */
    public static Attribute cols(int columns) { return attribute("cols", columns); }

    /**
     * The {@code colspan} attribute: number of columns that the cell is to span.
     * <p>On {@code td}, {@code th}.
     * Value: Valid non-negative integer greater than zero.
     * HTML 4.01: On {@code td}, {@code th}: number of cols spanned by cell (type {@code NUMBER}, default {@code 1}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/tables.html#attr-tdth-colspan">HTML: colspan on td</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#adef-colspan">HTML 4.01: colspan</a>
     */
    public static Attribute colspan(int columns) { return attribute("colspan", columns); }

    /**
     * The {@code content} attribute: value of the element.
     * <p>On {@code meta}.
     * Value: Text.
     * HTML 4.01: On {@code meta}: associated information (type {@code CDATA}, default {@code #REQUIRED}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#attr-meta-content">HTML: content on meta</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/global.html#adef-content">HTML 4.01: content</a>
     */
    public static Attribute content(String value) { return attribute("content", value); }

    /**
     * The {@code coords} attribute: coordinates for the shape to be created in an image map.
     * <p>On {@code area}.
     * Value: Valid list of floating-point numbers.
     * HTML 4.01:
     * <ul>
     * <li>On {@code area}: comma-separated list of lengths (type {@code %Coords;}, default {@code #IMPLIED})</li>
     * <li>On {@code a}: for use with client-side image maps (type {@code %Coords;}, default {@code #IMPLIED})</li>
     * </ul>
     *
     * @see <a href="https://html.spec.whatwg.org/dev/image-maps.html#attr-area-coords">HTML: coords on area</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/objects.html#adef-coords">HTML 4.01: coords</a>
     */
    public static Attribute coords(String coordinates) { return attribute("coords", coordinates); }

    /**
     * The {@code data} attribute: address of the resource.
     * <p>On {@code object}.
     * Value: Valid non-empty URL potentially surrounded by spaces.
     * HTML 4.01: On {@code object}: reference to object's data (type {@code %URI;}, default {@code #IMPLIED}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/iframe-embed-object.html#attr-object-data">HTML: data on object</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/objects.html#adef-data">HTML 4.01: data</a>
     */
    public static Attribute data(String url) { return attribute("data", url); }

    /**
     * The {@code datetime} attribute, on each element where it applies:
     * <ul>
     * <li>{@code del}, {@code ins} — Date and (optionally) time of the change. Value: Valid date string with optional
     * time.</li>
     * <li>{@code time} — Machine-readable value. Value: Valid month string, valid date string, valid yearless date
     * string, valid time string, valid local date and time string, valid time-zone offset string, valid global date
     * and time string, valid week string, valid non-negative integer, or valid duration string.</li>
     * </ul>
     * HTML 4.01: On {@code del}, {@code ins}: date and time of change (type {@code %Datetime;}, default
     * {@code #IMPLIED}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/edits.html#attr-mod-datetime">HTML: datetime on del</a>
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#attr-time-datetime">HTML: datetime on time</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/text.html#adef-datetime">HTML 4.01: datetime</a>
     */
    public static Attribute datetime(String value) { return attribute("datetime", value); }

    /**
     * The {@code defer} attribute: defer script execution.
     * <p>On {@code script}.
     * Value: Boolean attribute.
     * HTML 4.01: On {@code script}: UA may defer execution of script (type {@code (defer)}, default
     * {@code #IMPLIED}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/scripting.html#attr-script-defer">HTML: defer on script</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/scripts.html#adef-defer">HTML 4.01: defer</a>
     */
    public static Attribute defer() { return flag("defer"); }

    /**
     * The {@code disabled} attribute, on each element where it applies:
     * <ul>
     * <li>{@code button}, {@code input}, {@code optgroup}, {@code option}, {@code select}, {@code textarea},
     * {@code form-associated custom elements} — Whether the form control is disabled. Value: Boolean attribute.</li>
     * <li>{@code fieldset} — Whether the descendant form controls, except any inside legend, are disabled. Value:
     * Boolean attribute.</li>
     * <li>{@code link} — Whether the link is disabled. Value: Boolean attribute.</li>
     * </ul>
     * HTML 4.01: On {@code button}, {@code input}, {@code optgroup}, {@code option}, {@code select},
     * {@code textarea}: unavailable in this context (type {@code (disabled)}, default {@code #IMPLIED}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-control-infrastructure.html#attr-fe-disabled">HTML: disabled on button</a>
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-optgroup-disabled">HTML: disabled on optgroup</a>
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-option-disabled">HTML: disabled on option</a>
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-fieldset-disabled">HTML: disabled on fieldset</a>
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#attr-link-disabled">HTML: disabled on link</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#adef-disabled">HTML 4.01: disabled</a>
     */
    public static Attribute disabled() { return flag("disabled"); }

    /**
     * The {@code enctype} attribute: entry list encoding type to use for form submission.
     * <p>On {@code form}.
     * Value: "application/x-www-form-urlencoded"; "multipart/form-data"; "text/plain".
     * HTML 4.01: On {@code form}:  (type {@code %ContentType;}, default
     * {@code "application/x-www- form-urlencoded"}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-control-infrastructure.html#attr-fs-enctype">HTML: enctype on form</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#adef-enctype">HTML 4.01: enctype</a>
     */
    public static Attribute enctype(FormEnctype enctype) { return attribute("enctype", enctype); }

    /**
     * The {@code headers} attribute: the header cells for this cell.
     * <p>On {@code td}, {@code th}.
     * Value: Unordered set of unique space-separated tokens consisting of IDs.
     * HTML 4.01: On {@code td}, {@code th}: list of id's for header cells (type {@code IDREFS}, default
     * {@code #IMPLIED}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/tables.html#attr-tdth-headers">HTML: headers on td</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#adef-headers">HTML 4.01: headers</a>
     */
    public static Attribute headers(String ids) { return attribute("headers", ids); }

    /**
     * The {@code height} attribute: vertical dimension.
     * <p>On {@code canvas}, {@code embed}, {@code iframe}, {@code img}, {@code input}, {@code object},
     * {@code source (in picture)}, {@code video}.
     * Value: Valid non-negative integer.
     * HTML 4.01:
     * <ul>
     * <li>On {@code iframe}: frame height (type {@code %Length;}, default {@code #IMPLIED})</li>
     * <li>On {@code td}, {@code th}: height for cell (type {@code %Length;}, default {@code #IMPLIED},
     * deprecated)</li>
     * <li>On {@code img}, {@code object}: override height (type {@code %Length;}, default {@code #IMPLIED})</li>
     * <li>On {@code applet}: initial height (type {@code %Length;}, default {@code #REQUIRED}, deprecated)</li>
     * </ul>
     *
     * @see <a href="https://html.spec.whatwg.org/dev/canvas.html#attr-canvas-height">HTML: height on canvas</a>
     * @see <a href="https://html.spec.whatwg.org/dev/embedded-content-other.html#attr-dim-height">HTML: height on embed</a>
     * @see <a href="https://html.spec.whatwg.org/dev/embedded-content.html#the-picture-element">HTML: height on video</a>
     * @see <a href="https://www.w3.org/TR/html401/present/frames.html#adef-height-IFRAME">HTML 4.01: height</a>
     */
    public static Attribute height(int pixels) { return attribute("height", pixels); }

    /**
     * The {@code href} attribute, on each element where it applies:
     * <ul>
     * <li>{@code a}, {@code area} — Address of the hyperlink. Value: Valid URL potentially surrounded by spaces.</li>
     * <li>{@code link} — Address of the hyperlink. Value: Valid non-empty URL potentially surrounded by spaces.</li>
     * <li>{@code base} — Document base URL. Value: Valid URL potentially surrounded by spaces.</li>
     * </ul>
     * HTML 4.01:
     * <ul>
     * <li>On {@code a}, {@code area}, {@code link}: URI for linked resource (type {@code %URI;}, default
     * {@code #IMPLIED})</li>
     * <li>On {@code base}: URI that acts as base URI (type {@code %URI;}, default {@code #IMPLIED})</li>
     * </ul>
     *
     * @see <a href="https://html.spec.whatwg.org/dev/links.html#attr-hyperlink-href">HTML: href on a</a>
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#attr-link-href">HTML: href on link</a>
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#attr-base-href">HTML: href on base</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/links.html#adef-href">HTML 4.01: href</a>
     */
    public static Attribute href(String url) { return attribute("href", url); }

    /**
     * The {@code hreflang} attribute: language of the linked resource.
     * <p>On {@code a}, {@code link}.
     * Value: Valid BCP 47 language tag.
     * HTML 4.01: On {@code a}, {@code link}: language code (type {@code %LanguageCode;}, default {@code #IMPLIED}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/links.html#attr-hyperlink-hreflang">HTML: hreflang on a</a>
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#attr-link-hreflang">HTML: hreflang on link</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/links.html#adef-hreflang">HTML 4.01: hreflang</a>
     */
    public static Attribute hreflang(String bcp47) { return attribute("hreflang", bcp47); }

    /**
     * The {@code for} attribute, on each element where it applies:
     * <ul>
     * <li>{@code label} — Associate the label with form control. Value: ID.</li>
     * <li>{@code output} — Specifies controls from which the output was calculated. Value: Unordered set of unique
     * space-separated tokens consisting of IDs.</li>
     * <li>{@code template} — Updates existing content. Value: Text.</li>
     * </ul>
     * HTML 4.01: On {@code label}: matches field ID value (type {@code IDREF}, default {@code #IMPLIED}).
     * <p>Named {@code htmlFor}: {@code for} is a Java keyword.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/forms.html#attr-label-for">HTML: for on label</a>
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-output-for">HTML: for on output</a>
     * @see <a href="https://html.spec.whatwg.org/dev/scripting.html#attr-template-for">HTML: for on template</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#adef-for">HTML 4.01: for</a>
     */
    public static Attribute htmlFor(String id) { return attribute("for", id); }

    /**
     * The {@code http-equiv} attribute: pragma directive.
     * <p>On {@code meta}.
     * Value: "content-type"; "default-style"; "refresh"; "x-ua-compatible"; "content-security-policy".
     * HTML 4.01: On {@code meta}: HTTP response header name (type {@code NAME}, default {@code #IMPLIED}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#attr-meta-http-equiv">HTML: http-equiv on meta</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/global.html#adef-http-equiv">HTML 4.01: http-equiv</a>
     */
    public static Attribute httpEquiv(String header) { return attribute("http-equiv", header); }

    /**
     * The {@code ismap} attribute: whether the image is a server-side image map.
     * <p>On {@code img}.
     * Value: Boolean attribute.
     * HTML 4.01: On {@code img}, {@code input}: use server-side image map (type {@code (ismap)}, default
     * {@code #IMPLIED}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/embedded-content.html#attr-img-ismap">HTML: ismap on img</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/objects.html#adef-ismap">HTML 4.01: ismap</a>
     */
    public static Attribute ismap() { return flag("ismap"); }

    /**
     * The {@code label} attribute: user-visible label.
     * <p>On {@code optgroup}, {@code option}, {@code track}.
     * Value: Text.
     * HTML 4.01:
     * <ul>
     * <li>On {@code option}: for use in hierarchical menus (type {@code %Text;}, default {@code #IMPLIED})</li>
     * <li>On {@code optgroup}: for use in hierarchical menus (type {@code %Text;}, default {@code #REQUIRED})</li>
     * </ul>
     * <p>Takes a {@code Text}: a person reads it, so it is never a free string.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-optgroup-label">HTML: label on optgroup</a>
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-option-label">HTML: label on option</a>
     * @see <a href="https://html.spec.whatwg.org/dev/media.html#attr-track-label">HTML: label on track</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#adef-label-OPTION">HTML 4.01: label</a>
     */
    public static Attribute label(Text text) { return attribute("label", text.value()); }

    /**
     * The {@code maxlength} attribute: maximum length of value.
     * <p>On {@code input}, {@code textarea}.
     * Value: Valid non-negative integer.
     * HTML 4.01: On {@code input}: max chars for text fields (type {@code NUMBER}, default {@code #IMPLIED}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/input.html#attr-input-maxlength">HTML: maxlength on input</a>
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-textarea-maxlength">HTML: maxlength on textarea</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#adef-maxlength">HTML 4.01: maxlength</a>
     */
    public static Attribute maxlength(int length) { return attribute("maxlength", length); }

    /**
     * The {@code media} attribute: applicable media.
     * <p>On {@code link}, {@code meta}, {@code source}, {@code style}.
     * Value: Valid media query list.
     * HTML 4.01:
     * <ul>
     * <li>On {@code style}: designed for use with these media (type {@code %MediaDesc;}, default
     * {@code #IMPLIED})</li>
     * <li>On {@code link}: for rendering on these media (type {@code %MediaDesc;}, default {@code #IMPLIED})</li>
     * </ul>
     *
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#attr-link-media">HTML: media on link</a>
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#attr-meta-media">HTML: media on meta</a>
     * @see <a href="https://html.spec.whatwg.org/dev/embedded-content.html#attr-source-media">HTML: media on source</a>
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#attr-style-media">HTML: media on style</a>
     * @see <a href="https://www.w3.org/TR/html401/present/styles.html#adef-media">HTML 4.01: media</a>
     */
    public static Attribute media(String query) { return attribute("media", query); }

    /**
     * The {@code method} attribute: variant to use for form submission.
     * <p>On {@code form}.
     * Value: "get"; "post"; "dialog".
     * HTML 4.01: On {@code form}: HTTP method used to submit the form (type {@code (GET | POST)}, default
     * {@code GET}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-control-infrastructure.html#attr-fs-method">HTML: method on form</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#adef-method">HTML 4.01: method</a>
     */
    public static Attribute method(FormMethod method) { return attribute("method", method); }

    /**
     * The {@code multiple} attribute: whether to allow multiple values.
     * <p>On {@code input}, {@code select}.
     * Value: Boolean attribute.
     * HTML 4.01: On {@code select}: default is single selection (type {@code (multiple)}, default {@code #IMPLIED}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/input.html#attr-input-multiple">HTML: multiple on input</a>
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-select-multiple">HTML: multiple on select</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#adef-multiple">HTML 4.01: multiple</a>
     */
    public static Attribute multiple() { return flag("multiple"); }

    /**
     * The {@code name} attribute, on each element where it applies:
     * <ul>
     * <li>{@code button}, {@code fieldset}, {@code input}, {@code output}, {@code select}, {@code textarea},
     * {@code form-associated custom elements} — Name of the element to use for form submission and in the
     * form.elements API. Value: Text.</li>
     * <li>{@code details} — Name of group of mutually-exclusive details elements. Value: Text.</li>
     * <li>{@code form} — Name of form to use in the document.forms API. Value: Text.</li>
     * <li>{@code iframe}, {@code object} — Name of content navigable. Value: Valid navigable target name or
     * keyword.</li>
     * <li>{@code map} — Name of image map to reference from the usemap attribute. Value: Text.</li>
     * <li>{@code meta} — Metadata name. Value: Text.</li>
     * <li>{@code slot} — Name of shadow tree slot. Value: Text.</li>
     * </ul>
     * HTML 4.01:
     * <ul>
     * <li>On {@code button}, {@code textarea}:  (type {@code CDATA}, default {@code #IMPLIED})</li>
     * <li>On {@code applet}: allows applets to find each other (type {@code CDATA}, default {@code #IMPLIED},
     * deprecated)</li>
     * <li>On {@code select}: field name (type {@code CDATA}, default {@code #IMPLIED})</li>
     * <li>On {@code form}: name of form for scripting (type {@code CDATA}, default {@code #IMPLIED})</li>
     * <li>On {@code frame}, {@code iframe}: name of frame for targetting (type {@code CDATA}, default
     * {@code #IMPLIED})</li>
     * <li>On {@code img}: name of image for scripting (type {@code CDATA}, default {@code #IMPLIED})</li>
     * <li>On {@code a}: named link end (type {@code CDATA}, default {@code #IMPLIED})</li>
     * <li>On {@code input}, {@code object}: submit as part of form (type {@code CDATA}, default
     * {@code #IMPLIED})</li>
     * <li>On {@code map}: for reference by usemap (type {@code CDATA}, default {@code #REQUIRED})</li>
     * <li>On {@code param}: property name (type {@code CDATA}, default {@code #REQUIRED})</li>
     * <li>On {@code meta}: metainformation name (type {@code NAME}, default {@code #IMPLIED})</li>
     * </ul>
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-control-infrastructure.html#attr-fe-name">HTML: name on button</a>
     * @see <a href="https://html.spec.whatwg.org/dev/interactive-elements.html#attr-details-name">HTML: name on details</a>
     * @see <a href="https://html.spec.whatwg.org/dev/forms.html#attr-form-name">HTML: name on form</a>
     * @see <a href="https://html.spec.whatwg.org/dev/iframe-embed-object.html#attr-iframe-name">HTML: name on iframe</a>
     * @see <a href="https://html.spec.whatwg.org/dev/iframe-embed-object.html#attr-object-name">HTML: name on object</a>
     * @see <a href="https://html.spec.whatwg.org/dev/image-maps.html#attr-map-name">HTML: name on map</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#adef-name-BUTTON">HTML 4.01: name</a>
     */
    public static Attribute name(String value) { return attribute("name", value); }

    /**
     * The {@code readonly} attribute, on each element where it applies:
     * <ul>
     * <li>{@code input}, {@code textarea} — Whether to allow the value to be edited by the user. Value: Boolean
     * attribute.</li>
     * <li>{@code form-associated custom elements} — Affects willValidate, plus any behavior added by the custom
     * element author. Value: Boolean attribute.</li>
     * </ul>
     * HTML 4.01:
     * <ul>
     * <li>On {@code textarea}:  (type {@code (readonly)}, default {@code #IMPLIED})</li>
     * <li>On {@code input}: for text and passwd (type {@code (readonly)}, default {@code #IMPLIED})</li>
     * </ul>
     *
     * @see <a href="https://html.spec.whatwg.org/dev/input.html#attr-input-readonly">HTML: readonly on input</a>
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-textarea-readonly">HTML: readonly on textarea</a>
     * @see <a href="https://html.spec.whatwg.org/dev/custom-elements.html#attr-face-readonly">HTML: readonly on form-associated custom elements</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#adef-readonly">HTML 4.01: readonly</a>
     */
    public static Attribute readonly() { return flag("readonly"); }

    /**
     * The {@code rel} attribute, on each element where it applies:
     * <ul>
     * <li>{@code a}, {@code area} — Relationship between the location in the document containing the hyperlink and
     * the destination resource. Value: Unordered set of unique space-separated tokens.</li>
     * <li>{@code link} — Relationship between the document containing the hyperlink and the destination resource.
     * Value: Unordered set of unique space-separated tokens.</li>
     * </ul>
     * HTML 4.01: On {@code a}, {@code link}: forward link types (type {@code %LinkTypes;}, default {@code #IMPLIED}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/links.html#attr-hyperlink-rel">HTML: rel on a</a>
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#attr-link-rel">HTML: rel on link</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/links.html#adef-rel">HTML 4.01: rel</a>
     */
    public static Attribute rel(String types) { return attribute("rel", types); }

    /**
     * The {@code rows} attribute: number of lines to show.
     * <p>On {@code textarea}.
     * Value: Valid non-negative integer greater than zero.
     * HTML 4.01:
     * <ul>
     * <li>On {@code frameset}: list of lengths, default: 100% (1 row) (type {@code %MultiLengths;}, default
     * {@code #IMPLIED})</li>
     * <li>On {@code textarea}:  (type {@code NUMBER}, default {@code #REQUIRED})</li>
     * </ul>
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-textarea-rows">HTML: rows on textarea</a>
     * @see <a href="https://www.w3.org/TR/html401/present/frames.html#adef-rows-FRAMESET">HTML 4.01: rows</a>
     */
    public static Attribute rows(int rows) { return attribute("rows", rows); }

    /**
     * The {@code rowspan} attribute: number of rows that the cell is to span.
     * <p>On {@code td}, {@code th}.
     * Value: Valid non-negative integer.
     * HTML 4.01: On {@code td}, {@code th}: number of rows spanned by cell (type {@code NUMBER}, default {@code 1}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/tables.html#attr-tdth-rowspan">HTML: rowspan on td</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#adef-rowspan">HTML 4.01: rowspan</a>
     */
    public static Attribute rowspan(int rows) { return attribute("rowspan", rows); }

    /**
     * The {@code scope} attribute: specifies which cells the header cell applies to.
     * <p>On {@code th}.
     * Value: "row"; "col"; "rowgroup"; "colgroup".
     * HTML 4.01: On {@code td}, {@code th}: scope covered by header cells (type {@code %Scope;}, default
     * {@code #IMPLIED}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/tables.html#attr-th-scope">HTML: scope on th</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#adef-scope">HTML 4.01: scope</a>
     */
    public static Attribute scope(CellScope scope) { return attribute("scope", scope); }

    /**
     * The {@code selected} attribute: whether the option is selected by default.
     * <p>On {@code option}.
     * Value: Boolean attribute.
     * HTML 4.01: On {@code option}:  (type {@code (selected)}, default {@code #IMPLIED}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-option-selected">HTML: selected on option</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#adef-selected">HTML 4.01: selected</a>
     */
    public static Attribute selected() { return flag("selected"); }

    /**
     * The {@code shape} attribute: the kind of shape to be created in an image map.
     * <p>On {@code area}.
     * Value: "circle"; "default"; "poly"; "rect".
     * HTML 4.01:
     * <ul>
     * <li>On {@code area}: controls interpretation of coords (type {@code %Shape;}, default {@code rect})</li>
     * <li>On {@code a}: for use with client-side image maps (type {@code %Shape;}, default {@code rect})</li>
     * </ul>
     *
     * @see <a href="https://html.spec.whatwg.org/dev/image-maps.html#attr-area-shape">HTML: shape on area</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/objects.html#adef-shape">HTML 4.01: shape</a>
     */
    public static Attribute shape(AreaShape shape) { return attribute("shape", shape); }

    /**
     * The {@code size} attribute: size of the control.
     * <p>On {@code input}, {@code select}.
     * Value: Valid non-negative integer greater than zero.
     * HTML 4.01:
     * <ul>
     * <li>On {@code hr}:  (type {@code %Pixels;}, default {@code #IMPLIED}, deprecated)</li>
     * <li>On {@code font}: [+|-]nn e.g. size="+1", size="4" (type {@code CDATA}, default {@code #IMPLIED},
     * deprecated)</li>
     * <li>On {@code input}: specific to each type of field (type {@code CDATA}, default {@code #IMPLIED})</li>
     * <li>On {@code basefont}: base font size for FONT elements (type {@code CDATA}, default {@code #REQUIRED},
     * deprecated)</li>
     * <li>On {@code select}: rows visible (type {@code NUMBER}, default {@code #IMPLIED})</li>
     * </ul>
     *
     * @see <a href="https://html.spec.whatwg.org/dev/input.html#attr-input-size">HTML: size on input</a>
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-select-size">HTML: size on select</a>
     * @see <a href="https://www.w3.org/TR/html401/present/graphics.html#adef-size-HR">HTML 4.01: size</a>
     */
    public static Attribute size(int size) { return attribute("size", size); }

    /**
     * The {@code span} attribute: number of columns spanned by the element.
     * <p>On {@code col}, {@code colgroup}.
     * Value: Valid non-negative integer greater than zero.
     * HTML 4.01:
     * <ul>
     * <li>On {@code col}: COL attributes affect N columns (type {@code NUMBER}, default {@code 1})</li>
     * <li>On {@code colgroup}: default number of columns in group (type {@code NUMBER}, default {@code 1})</li>
     * </ul>
     *
     * @see <a href="https://html.spec.whatwg.org/dev/tables.html#attr-col-span">HTML: span on col</a>
     * @see <a href="https://html.spec.whatwg.org/dev/tables.html#attr-colgroup-span">HTML: span on colgroup</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#adef-span-COL">HTML 4.01: span</a>
     */
    public static Attribute span(int columns) { return attribute("span", columns); }

    /**
     * The {@code src} attribute: address of the resource.
     * <p>On {@code audio}, {@code embed}, {@code iframe}, {@code img}, {@code input}, {@code script},
     * {@code source (in video or audio)}, {@code track}, {@code video}.
     * Value: Valid non-empty URL potentially surrounded by spaces.
     * HTML 4.01:
     * <ul>
     * <li>On {@code script}: URI for an external script (type {@code %URI;}, default {@code #IMPLIED})</li>
     * <li>On {@code input}: for fields with images (type {@code %URI;}, default {@code #IMPLIED})</li>
     * <li>On {@code frame}, {@code iframe}: source of frame content (type {@code %URI;}, default
     * {@code #IMPLIED})</li>
     * <li>On {@code img}: URI of image to embed (type {@code %URI;}, default {@code #REQUIRED})</li>
     * </ul>
     *
     * @see <a href="https://html.spec.whatwg.org/dev/media.html#attr-media-src">HTML: src on audio</a>
     * @see <a href="https://html.spec.whatwg.org/dev/iframe-embed-object.html#attr-embed-src">HTML: src on embed</a>
     * @see <a href="https://html.spec.whatwg.org/dev/iframe-embed-object.html#attr-iframe-src">HTML: src on iframe</a>
     * @see <a href="https://html.spec.whatwg.org/dev/embedded-content.html#attr-img-src">HTML: src on img</a>
     * @see <a href="https://html.spec.whatwg.org/dev/input.html#attr-input-src">HTML: src on input</a>
     * @see <a href="https://html.spec.whatwg.org/dev/scripting.html#attr-script-src">HTML: src on script</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/scripts.html#adef-src-SCRIPT">HTML 4.01: src</a>
     */
    public static Attribute src(String url) { return attribute("src", url); }

    /**
     * The {@code type} attribute, on each element where it applies:
     * <ul>
     * <li>{@code a}, {@code link} — Hint for the type of the referenced resource. Value: Valid MIME type string.</li>
     * <li>{@code button} — Type of button. Value: "submit"; "reset"; "button".</li>
     * <li>{@code embed}, {@code object}, {@code source} — Type of embedded resource. Value: Valid MIME type
     * string.</li>
     * <li>{@code input} — Type of form control. Value: input type keyword.</li>
     * <li>{@code ol} — Kind of list marker. Value: "1"; "a"; "A"; "i"; "I".</li>
     * <li>{@code script} — Type of script. Value: "module"; "importmap"; "speculationrules"; a valid MIME type string
     * that is not a JavaScript MIME type essence match.</li>
     * </ul>
     * HTML 4.01:
     * <ul>
     * <li>On {@code a}, {@code link}: advisory content type (type {@code %ContentType;}, default
     * {@code #IMPLIED})</li>
     * <li>On {@code object}: content type for data (type {@code %ContentType;}, default {@code #IMPLIED})</li>
     * <li>On {@code param}: content type for value when valuetype=ref (type {@code %ContentType;}, default
     * {@code #IMPLIED})</li>
     * <li>On {@code script}: content type of script language (type {@code %ContentType;}, default
     * {@code #REQUIRED})</li>
     * <li>On {@code style}: content type of style language (type {@code %ContentType;}, default
     * {@code #REQUIRED})</li>
     * <li>On {@code input}: what kind of widget is needed (type {@code %InputType;}, default {@code TEXT})</li>
     * <li>On {@code li}: list item style (type {@code %LIStyle;}, default {@code #IMPLIED}, deprecated)</li>
     * <li>On {@code ol}: numbering style (type {@code %OLStyle;}, default {@code #IMPLIED}, deprecated)</li>
     * <li>On {@code ul}: bullet style (type {@code %ULStyle;}, default {@code #IMPLIED}, deprecated)</li>
     * <li>On {@code button}: for use as form button (type {@code (button | submit | reset)}, default
     * {@code submit})</li>
     * </ul>
     *
     * @see <a href="https://html.spec.whatwg.org/dev/links.html#attr-hyperlink-type">HTML: type on a</a>
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#attr-link-type">HTML: type on link</a>
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-button-type">HTML: type on button</a>
     * @see <a href="https://html.spec.whatwg.org/dev/iframe-embed-object.html#attr-embed-type">HTML: type on embed</a>
     * @see <a href="https://html.spec.whatwg.org/dev/iframe-embed-object.html#attr-object-type">HTML: type on object</a>
     * @see <a href="https://html.spec.whatwg.org/dev/embedded-content.html#attr-source-type">HTML: type on source</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/links.html#adef-type-A">HTML 4.01: type</a>
     */
    public static Attribute type(String value) { return attribute("type", value); }

    /**
     * The {@code type} attribute, on each element where it applies:
     * <ul>
     * <li>{@code a}, {@code link} — Hint for the type of the referenced resource. Value: Valid MIME type string.</li>
     * <li>{@code button} — Type of button. Value: "submit"; "reset"; "button".</li>
     * <li>{@code embed}, {@code object}, {@code source} — Type of embedded resource. Value: Valid MIME type
     * string.</li>
     * <li>{@code input} — Type of form control. Value: input type keyword.</li>
     * <li>{@code ol} — Kind of list marker. Value: "1"; "a"; "A"; "i"; "I".</li>
     * <li>{@code script} — Type of script. Value: "module"; "importmap"; "speculationrules"; a valid MIME type string
     * that is not a JavaScript MIME type essence match.</li>
     * </ul>
     * HTML 4.01:
     * <ul>
     * <li>On {@code a}, {@code link}: advisory content type (type {@code %ContentType;}, default
     * {@code #IMPLIED})</li>
     * <li>On {@code object}: content type for data (type {@code %ContentType;}, default {@code #IMPLIED})</li>
     * <li>On {@code param}: content type for value when valuetype=ref (type {@code %ContentType;}, default
     * {@code #IMPLIED})</li>
     * <li>On {@code script}: content type of script language (type {@code %ContentType;}, default
     * {@code #REQUIRED})</li>
     * <li>On {@code style}: content type of style language (type {@code %ContentType;}, default
     * {@code #REQUIRED})</li>
     * <li>On {@code input}: what kind of widget is needed (type {@code %InputType;}, default {@code TEXT})</li>
     * <li>On {@code li}: list item style (type {@code %LIStyle;}, default {@code #IMPLIED}, deprecated)</li>
     * <li>On {@code ol}: numbering style (type {@code %OLStyle;}, default {@code #IMPLIED}, deprecated)</li>
     * <li>On {@code ul}: bullet style (type {@code %ULStyle;}, default {@code #IMPLIED}, deprecated)</li>
     * <li>On {@code button}: for use as form button (type {@code (button | submit | reset)}, default
     * {@code submit})</li>
     * </ul>
     *
     * @see <a href="https://html.spec.whatwg.org/dev/links.html#attr-hyperlink-type">HTML: type on a</a>
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#attr-link-type">HTML: type on link</a>
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-button-type">HTML: type on button</a>
     * @see <a href="https://html.spec.whatwg.org/dev/iframe-embed-object.html#attr-embed-type">HTML: type on embed</a>
     * @see <a href="https://html.spec.whatwg.org/dev/iframe-embed-object.html#attr-object-type">HTML: type on object</a>
     * @see <a href="https://html.spec.whatwg.org/dev/embedded-content.html#attr-source-type">HTML: type on source</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/links.html#adef-type-A">HTML 4.01: type</a>
     */
    public static Attribute type(ButtonType type) { return attribute("type", type); }

    /**
     * The {@code usemap} attribute: name of image map to use.
     * <p>On {@code img}.
     * Value: Valid hash-name reference.
     * HTML 4.01: On {@code img}, {@code input}, {@code object}: use client-side image map (type {@code %URI;},
     * default {@code #IMPLIED}).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/image-maps.html#attr-hyperlink-usemap">HTML: usemap on img</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/objects.html#adef-usemap">HTML 4.01: usemap</a>
     */
    public static Attribute usemap(String map) { return attribute("usemap", map); }

    /**
     * The {@code value} attribute, on each element where it applies:
     * <ul>
     * <li>{@code button}, {@code option} — Value to be used for form submission. Value: Text.</li>
     * <li>{@code data} — Machine-readable value. Value: Text.</li>
     * <li>{@code input} — Value of the form control. Value: Varies.</li>
     * <li>{@code li} — Ordinal value of the list item. Value: Valid integer.</li>
     * <li>{@code meter}, {@code progress} — Current value of the element. Value: Valid floating-point number.</li>
     * </ul>
     * HTML 4.01:
     * <ul>
     * <li>On {@code input}: Specify for radio buttons and checkboxes (type {@code CDATA}, default
     * {@code #IMPLIED})</li>
     * <li>On {@code option}: defaults to element content (type {@code CDATA}, default {@code #IMPLIED})</li>
     * <li>On {@code param}: property value (type {@code CDATA}, default {@code #IMPLIED})</li>
     * <li>On {@code button}: sent to server when submitted (type {@code CDATA}, default {@code #IMPLIED})</li>
     * <li>On {@code li}: reset sequence number (type {@code NUMBER}, default {@code #IMPLIED}, deprecated)</li>
     * </ul>
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-button-value">HTML: value on button</a>
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-option-value">HTML: value on option</a>
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#attr-data-value">HTML: value on data</a>
     * @see <a href="https://html.spec.whatwg.org/dev/input.html#attr-input-value">HTML: value on input</a>
     * @see <a href="https://html.spec.whatwg.org/dev/grouping-content.html#attr-li-value">HTML: value on li</a>
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-meter-value">HTML: value on meter</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#adef-value-INPUT">HTML 4.01: value</a>
     */
    public static Attribute value(String value) { return attribute("value", value); }

    /**
     * The {@code width} attribute: horizontal dimension.
     * <p>On {@code canvas}, {@code embed}, {@code iframe}, {@code img}, {@code input}, {@code object},
     * {@code source (in picture)}, {@code video}.
     * Value: Valid non-negative integer.
     * HTML 4.01:
     * <ul>
     * <li>On {@code hr}:  (type {@code %Length;}, default {@code #IMPLIED}, deprecated)</li>
     * <li>On {@code iframe}: frame width (type {@code %Length;}, default {@code #IMPLIED})</li>
     * <li>On {@code img}, {@code object}: override width (type {@code %Length;}, default {@code #IMPLIED})</li>
     * <li>On {@code table}: table width (type {@code %Length;}, default {@code #IMPLIED})</li>
     * <li>On {@code td}, {@code th}: width for cell (type {@code %Length;}, default {@code #IMPLIED},
     * deprecated)</li>
     * <li>On {@code applet}: initial width (type {@code %Length;}, default {@code #REQUIRED}, deprecated)</li>
     * <li>On {@code col}: column width specification (type {@code %MultiLength;}, default {@code #IMPLIED})</li>
     * <li>On {@code colgroup}: default width for enclosed COLs (type {@code %MultiLength;}, default
     * {@code #IMPLIED})</li>
     * <li>On {@code pre}: previous next contents elements index (type {@code NUMBER}, default {@code #IMPLIED},
     * deprecated)</li>
     * </ul>
     *
     * @see <a href="https://html.spec.whatwg.org/dev/canvas.html#attr-canvas-width">HTML: width on canvas</a>
     * @see <a href="https://html.spec.whatwg.org/dev/embedded-content-other.html#attr-dim-width">HTML: width on embed</a>
     * @see <a href="https://html.spec.whatwg.org/dev/embedded-content.html#the-picture-element">HTML: width on video</a>
     * @see <a href="https://www.w3.org/TR/html401/present/graphics.html#adef-width-HR">HTML 4.01: width</a>
     */
    public static Attribute width(int pixels) { return attribute("width", pixels); }

    // ---------- elements: document and metadata

    /**
     * The {@code html} element: root element.
     * <p>Categories: none.
     * Parents: none.
     * Children: head; body.
     * Attributes: globals.
     * HTML 4.01: Document root element.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#the-html-element">HTML: the html element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/global.html#edef-HTML">HTML 4.01: HTML</a>
     */
    public static Node.Element html(Content... c) { return element("html", c); }

    /**
     * The {@code head} element: container for document metadata.
     * <p>Categories: none.
     * Parents: html.
     * Children: metadata content.
     * Attributes: globals.
     * HTML 4.01: Document head.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#the-head-element">HTML: the head element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/global.html#edef-HEAD">HTML 4.01: HEAD</a>
     */
    public static Node.Element head(Content... c) { return element("head", c); }

    /**
     * The {@code title} element: document title.
     * <p>Categories: metadata.
     * Parents: head.
     * Children: text.
     * Attributes: globals.
     * HTML 4.01: Document title.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#the-title-element">HTML: the title element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/global.html#edef-TITLE">HTML 4.01: TITLE</a>
     */
    public static Node.Element title(Content... c) { return element("title", c); }

    /**
     * The {@code base} element: base URL and default target navigable for hyperlinks and forms.
     * <p>Categories: metadata.
     * Parents: head.
     * Children: empty.
     * Attributes: globals; href; target.
     * Void element: no children and no end tag.
     * HTML 4.01: Document base URI. (empty element (no end tag))
     *
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#the-base-element">HTML: the base element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/links.html#edef-BASE">HTML 4.01: BASE</a>
     */
    public static Node.Element base(Content... c) { return element("base", c); }

    /**
     * The {@code link} element: link metadata.
     * <p>Categories: metadata; flow; phrasing.
     * Parents: head; noscript; phrasing.
     * Children: empty.
     * Attributes: globals; href; crossorigin; rel; as; media; hreflang; type; sizes; imagesrcset; imagesizes;
     * referrerpolicy; integrity; blocking; color; disabled; fetchpriority.
     * Void element: no children and no end tag.
     * HTML 4.01: A media-independent link. (empty element (no end tag))
     *
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#the-link-element">HTML: the link element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/links.html#edef-LINK">HTML 4.01: LINK</a>
     */
    public static Node.Element link(Content... c) { return element("link", c); }

    /**
     * The {@code meta} element: text metadata.
     * <p>Categories: metadata; flow; phrasing.
     * Parents: head; noscript; phrasing.
     * Children: empty.
     * Attributes: globals; name; http-equiv; content; charset; media.
     * Void element: no children and no end tag.
     * HTML 4.01: Generic metainformation. (empty element (no end tag))
     *
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#the-meta-element">HTML: the meta element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/global.html#edef-META">HTML 4.01: META</a>
     */
    public static Node.Element meta(Content... c) { return element("meta", c); }

    /**
     * The {@code style} element: embedded styling information.
     * <p>Categories: metadata.
     * Parents: head; noscript.
     * Children: text.
     * Attributes: globals; media; blocking.
     * HTML 4.01: Style info.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#the-style-element">HTML: the style element</a>
     * @see <a href="https://www.w3.org/TR/html401/present/styles.html#edef-STYLE">HTML 4.01: STYLE</a>
     */
    // Inline content goes through {@link #styleText}: {@code text(...)} escapes it and breaks the code.
    public static Node.Element style(Content... c) { return element("style", c); }

    /**
     * The {@code script} element: embedded script.
     * <p>Categories: metadata; flow; phrasing; script-supporting.
     * Parents: head; phrasing; script-supporting.
     * Children: script, data, or script documentation.
     * Attributes: globals; src; type; nomodule; async; defer; crossorigin; integrity; referrerpolicy; blocking;
     * fetchpriority.
     * HTML 4.01: Script statements.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/scripting.html#the-script-element">HTML: the script element</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/scripts.html#edef-SCRIPT">HTML 4.01: SCRIPT</a>
     */
    // Inline content goes through {@link #scriptText}: {@code text(...)} escapes it and breaks the code.
    public static Node.Element script(Content... c) { return element("script", c); }

    /**
     * The {@code noscript} element: fallback content for script.
     * <p>Categories: metadata; flow; phrasing.
     * Parents: head; phrasing; select; optgroup.
     * Children: varies.
     * Attributes: globals.
     * HTML 4.01: Alternate content container for non script-based rendering.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/scripting.html#the-noscript-element">HTML: the noscript element</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/scripts.html#edef-NOSCRIPT">HTML 4.01: NOSCRIPT</a>
     */
    public static Node.Element noscript(Content... c) { return element("noscript", c); }

    /**
     * The {@code body} element: document body.
     * <p>Categories: none.
     * Parents: html.
     * Children: flow.
     * Attributes: globals; onafterprint; onbeforeprint; onbeforeunload; onhashchange; onlanguagechange; onmessage;
     * onmessageerror; onoffline; ononline; onpageswap; onpagehide; onpagereveal; onpageshow; onpopstate;
     * onrejectionhandled; onstorage; onunhandledrejection; onunload.
     * HTML 4.01: Document body.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/sections.html#the-body-element">HTML: the body element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/global.html#edef-BODY">HTML 4.01: BODY</a>
     */
    public static Node.Element body(Content... c) { return element("body", c); }

    // ---------- elements: sections and grouping

    /**
     * The {@code address} element: contact information for a page or article element.
     * <p>Categories: flow; palpable.
     * Parents: flow.
     * Children: flow.
     * Attributes: globals.
     * HTML 4.01: Information on author.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/sections.html#the-address-element">HTML: the address element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/global.html#edef-ADDRESS">HTML 4.01: ADDRESS</a>
     */
    public static Node.Element address(Content... c) { return element("address", c); }

    /**
     * The {@code h1} element: heading.
     * <p>Categories: flow; heading; palpable.
     * Parents: legend; summary; flow.
     * Children: phrasing.
     * Attributes: globals.
     * HTML 4.01: Heading.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/sections.html#the-h1,-h2,-h3,-h4,-h5,-and-h6-elements">HTML: the h1 element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/global.html#edef-H1">HTML 4.01: H1</a>
     */
    public static Node.Element h1(Content... c) { return element("h1", c); }

    /**
     * The {@code h2} element: heading.
     * <p>Categories: flow; heading; palpable.
     * Parents: legend; summary; flow.
     * Children: phrasing.
     * Attributes: globals.
     * HTML 4.01: Heading.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/sections.html#the-h1,-h2,-h3,-h4,-h5,-and-h6-elements">HTML: the h2 element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/global.html#edef-H2">HTML 4.01: H2</a>
     */
    public static Node.Element h2(Content... c) { return element("h2", c); }

    /**
     * The {@code h3} element: heading.
     * <p>Categories: flow; heading; palpable.
     * Parents: legend; summary; flow.
     * Children: phrasing.
     * Attributes: globals.
     * HTML 4.01: Heading.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/sections.html#the-h1,-h2,-h3,-h4,-h5,-and-h6-elements">HTML: the h3 element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/global.html#edef-H3">HTML 4.01: H3</a>
     */
    public static Node.Element h3(Content... c) { return element("h3", c); }

    /**
     * The {@code h4} element: heading.
     * <p>Categories: flow; heading; palpable.
     * Parents: legend; summary; flow.
     * Children: phrasing.
     * Attributes: globals.
     * HTML 4.01: Heading.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/sections.html#the-h1,-h2,-h3,-h4,-h5,-and-h6-elements">HTML: the h4 element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/global.html#edef-H4">HTML 4.01: H4</a>
     */
    public static Node.Element h4(Content... c) { return element("h4", c); }

    /**
     * The {@code h5} element: heading.
     * <p>Categories: flow; heading; palpable.
     * Parents: legend; summary; flow.
     * Children: phrasing.
     * Attributes: globals.
     * HTML 4.01: Heading.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/sections.html#the-h1,-h2,-h3,-h4,-h5,-and-h6-elements">HTML: the h5 element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/global.html#edef-H5">HTML 4.01: H5</a>
     */
    public static Node.Element h5(Content... c) { return element("h5", c); }

    /**
     * The {@code h6} element: heading.
     * <p>Categories: flow; heading; palpable.
     * Parents: legend; summary; flow.
     * Children: phrasing.
     * Attributes: globals.
     * HTML 4.01: Heading.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/sections.html#the-h1,-h2,-h3,-h4,-h5,-and-h6-elements">HTML: the h6 element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/global.html#edef-H6">HTML 4.01: H6</a>
     */
    public static Node.Element h6(Content... c) { return element("h6", c); }

    /**
     * The {@code blockquote} element: a section quoted from another source.
     * <p>Categories: flow; palpable.
     * Parents: flow.
     * Children: flow.
     * Attributes: globals; cite.
     * HTML 4.01: Long quotation.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/grouping-content.html#the-blockquote-element">HTML: the blockquote element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/text.html#edef-BLOCKQUOTE">HTML 4.01: BLOCKQUOTE</a>
     */
    public static Node.Element blockquote(Content... c) { return element("blockquote", c); }

    /**
     * The {@code dd} element: content for corresponding dt element(s).
     * <p>Categories: none.
     * Parents: dl; div.
     * Children: flow.
     * Attributes: globals.
     * HTML 4.01: Definition description.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/grouping-content.html#the-dd-element">HTML: the dd element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/lists.html#edef-DD">HTML 4.01: DD</a>
     */
    public static Node.Element dd(Content... c) { return element("dd", c); }

    /**
     * The {@code div} element: generic flow container, or container for name-value groups in dl elements.
     * <p>Categories: flow; palpable.
     * Parents: flow; dl; option; optgroup; select.
     * Children: flow.
     * Attributes: globals.
     * HTML 4.01: Generic language/style container.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/grouping-content.html#the-div-element">HTML: the div element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/global.html#edef-DIV">HTML 4.01: DIV</a>
     */
    public static Node.Element div(Content... c) { return element("div", c); }

    /**
     * The {@code dl} element: association list consisting of zero or more name-value groups.
     * <p>Categories: flow; palpable.
     * Parents: flow.
     * Children: dt; dd; div; script-supporting elements.
     * Attributes: globals.
     * HTML 4.01: Definition list.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/grouping-content.html#the-dl-element">HTML: the dl element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/lists.html#edef-DL">HTML 4.01: DL</a>
     */
    public static Node.Element dl(Content... c) { return element("dl", c); }

    /**
     * The {@code dt} element: legend for corresponding dd element(s).
     * <p>Categories: none.
     * Parents: dl; div.
     * Children: flow.
     * Attributes: globals.
     * HTML 4.01: Definition term.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/grouping-content.html#the-dt-element">HTML: the dt element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/lists.html#edef-DT">HTML 4.01: DT</a>
     */
    public static Node.Element dt(Content... c) { return element("dt", c); }

    /**
     * The {@code hr} element: thematic break.
     * <p>Categories: flow.
     * Parents: flow; select.
     * Children: empty.
     * Attributes: globals.
     * Void element: no children and no end tag.
     * HTML 4.01: Horizontal rule. (empty element (no end tag))
     *
     * @see <a href="https://html.spec.whatwg.org/dev/grouping-content.html#the-hr-element">HTML: the hr element</a>
     * @see <a href="https://www.w3.org/TR/html401/present/graphics.html#edef-HR">HTML 4.01: HR</a>
     */
    public static Node.Element hr(Content... c) { return element("hr", c); }

    /**
     * The {@code li} element: list item.
     * <p>Categories: none.
     * Parents: ol; ul; menu.
     * Children: flow.
     * Attributes: globals; value.
     * HTML 4.01: List item.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/grouping-content.html#the-li-element">HTML: the li element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/lists.html#edef-LI">HTML 4.01: LI</a>
     */
    public static Node.Element li(Content... c) { return element("li", c); }

    /**
     * The {@code ol} element: ordered list.
     * <p>Categories: flow; palpable.
     * Parents: flow.
     * Children: li; script-supporting elements.
     * Attributes: globals; reversed; start; type.
     * HTML 4.01: Ordered list.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/grouping-content.html#the-ol-element">HTML: the ol element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/lists.html#edef-OL">HTML 4.01: OL</a>
     */
    public static Node.Element ol(Content... c) { return element("ol", c); }

    /**
     * The {@code p} element: paragraph.
     * <p>Categories: flow; palpable.
     * Parents: flow.
     * Children: phrasing.
     * Attributes: globals.
     * HTML 4.01: Paragraph.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/grouping-content.html#the-p-element">HTML: the p element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/text.html#edef-P">HTML 4.01: P</a>
     */
    public static Node.Element p(Content... c) { return element("p", c); }

    /**
     * The {@code pre} element: block of preformatted text.
     * <p>Categories: flow; palpable.
     * Parents: flow.
     * Children: phrasing.
     * Attributes: globals.
     * HTML 4.01: Preformatted text.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/grouping-content.html#the-pre-element">HTML: the pre element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/text.html#edef-PRE">HTML 4.01: PRE</a>
     */
    public static Node.Element pre(Content... c) { return element("pre", c); }

    /**
     * The {@code ul} element: list.
     * <p>Categories: flow; palpable.
     * Parents: flow.
     * Children: li; script-supporting elements.
     * Attributes: globals.
     * HTML 4.01: Unordered list.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/grouping-content.html#the-ul-element">HTML: the ul element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/lists.html#edef-UL">HTML 4.01: UL</a>
     */
    public static Node.Element ul(Content... c) { return element("ul", c); }

    // ---------- elements: text-level semantics

    /**
     * The {@code a} element: hyperlink.
     * <p>Categories: flow; phrasing; interactive; palpable.
     * Parents: phrasing.
     * Children: transparent.
     * Attributes: globals; href; target; download; ping; rel; hreflang; type; referrerpolicy.
     * HTML 4.01: Anchor.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-a-element">HTML: the a element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/links.html#edef-A">HTML 4.01: A</a>
     */
    public static Node.Element a(Content... c) { return element("a", c); }

    /**
     * The {@code abbr} element: abbreviation.
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals.
     * HTML 4.01: Abbreviated form (e.g., WWW, HTTP, etc.).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-abbr-element">HTML: the abbr element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/text.html#edef-ABBR">HTML 4.01: ABBR</a>
     */
    public static Node.Element abbr(Content... c) { return element("abbr", c); }

    /**
     * The {@code b} element: keywords.
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals.
     * HTML 4.01: Bold text style.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-b-element">HTML: the b element</a>
     * @see <a href="https://www.w3.org/TR/html401/present/graphics.html#edef-B">HTML 4.01: B</a>
     */
    public static Node.Element b(Content... c) { return element("b", c); }

    /**
     * The {@code bdo} element: text directionality formatting.
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals.
     * HTML 4.01: I18N BiDi over-ride.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-bdo-element">HTML: the bdo element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/dirlang.html#edef-BDO">HTML 4.01: BDO</a>
     */
    public static Node.Element bdo(Content... c) { return element("bdo", c); }

    /**
     * The {@code br} element: line break, e.g. in poem or postal address.
     * <p>Categories: flow; phrasing.
     * Parents: phrasing.
     * Children: empty.
     * Attributes: globals.
     * Void element: no children and no end tag.
     * HTML 4.01: Forced line break. (empty element (no end tag))
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-br-element">HTML: the br element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/text.html#edef-BR">HTML 4.01: BR</a>
     */
    public static Node.Element br(Content... c) { return element("br", c); }

    /**
     * The {@code cite} element: title of a work.
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals.
     * HTML 4.01: Citation.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-cite-element">HTML: the cite element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/text.html#edef-EM">HTML 4.01: CITE</a>
     */
    public static Node.Element cite(Content... c) { return element("cite", c); }

    /**
     * The {@code code} element: computer code.
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals.
     * HTML 4.01: Computer code fragment.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-code-element">HTML: the code element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/text.html#edef-CODE">HTML 4.01: CODE</a>
     */
    public static Node.Element code(Content... c) { return element("code", c); }

    /**
     * The {@code dfn} element: defining instance.
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals.
     * HTML 4.01: Instance definition.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-dfn-element">HTML: the dfn element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/text.html#edef-DFN">HTML 4.01: DFN</a>
     */
    public static Node.Element dfn(Content... c) { return element("dfn", c); }

    /**
     * The {@code em} element: stress emphasis.
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals.
     * HTML 4.01: Emphasis.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-em-element">HTML: the em element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/text.html#edef-EM">HTML 4.01: EM</a>
     */
    public static Node.Element em(Content... c) { return element("em", c); }

    /**
     * The {@code i} element: alternate voice.
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals.
     * HTML 4.01: Italic text style.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-i-element">HTML: the i element</a>
     * @see <a href="https://www.w3.org/TR/html401/present/graphics.html#edef-I">HTML 4.01: I</a>
     */
    public static Node.Element i(Content... c) { return element("i", c); }

    /**
     * The {@code kbd} element: user input.
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals.
     * HTML 4.01: Text to be entered by the user.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-kbd-element">HTML: the kbd element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/text.html#edef-KBD">HTML 4.01: KBD</a>
     */
    public static Node.Element kbd(Content... c) { return element("kbd", c); }

    /**
     * The {@code q} element: quotation.
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals; cite.
     * HTML 4.01: Short inline quotation.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-q-element">HTML: the q element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/text.html#edef-Q">HTML 4.01: Q</a>
     */
    public static Node.Element q(Content... c) { return element("q", c); }

    /**
     * The {@code samp} element: computer output.
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals.
     * HTML 4.01: Sample program output, scripts, etc.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-samp-element">HTML: the samp element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/text.html#edef-SAMP">HTML 4.01: SAMP</a>
     */
    public static Node.Element samp(Content... c) { return element("samp", c); }

    /**
     * The {@code small} element: side comment.
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals.
     * HTML 4.01: Small text style.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-small-element">HTML: the small element</a>
     * @see <a href="https://www.w3.org/TR/html401/present/graphics.html#edef-SMALL">HTML 4.01: SMALL</a>
     */
    public static Node.Element small(Content... c) { return element("small", c); }

    /**
     * The {@code span} element: generic phrasing container.
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals.
     * HTML 4.01: Generic language/style container.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-span-element">HTML: the span element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/global.html#edef-SPAN">HTML 4.01: SPAN</a>
     */
    public static Node.Element span(Content... c) { return element("span", c); }

    /**
     * The {@code strong} element: importance.
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals.
     * HTML 4.01: Strong emphasis.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-strong-element">HTML: the strong element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/text.html#edef-STRONG">HTML 4.01: STRONG</a>
     */
    public static Node.Element strong(Content... c) { return element("strong", c); }

    /**
     * The {@code sub} element: subscript.
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals.
     * HTML 4.01: Subscript.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-sub-and-sup-elements">HTML: the sub element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/text.html#edef-SUP">HTML 4.01: SUB</a>
     */
    public static Node.Element sub(Content... c) { return element("sub", c); }

    /**
     * The {@code sup} element: superscript.
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals.
     * HTML 4.01: Superscript.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-sub-and-sup-elements">HTML: the sup element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/text.html#edef-SUP">HTML 4.01: SUP</a>
     */
    public static Node.Element sup(Content... c) { return element("sup", c); }

    /**
     * The {@code var} element: variable.
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals.
     * HTML 4.01: Instance of a variable or program argument previous next contents attributes index.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-var-element">HTML: the var element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/text.html#edef-VAR">HTML 4.01: VAR</a>
     */
    public static Node.Element var(Content... c) { return element("var", c); }

    // ---------- elements: edits, embedded content, image maps

    /**
     * The {@code del} element: a removal from the document.
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: transparent.
     * Attributes: globals; cite; datetime.
     * HTML 4.01: Deleted text.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/edits.html#the-del-element">HTML: the del element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/text.html#edef-del">HTML 4.01: DEL</a>
     */
    public static Node.Element del(Content... c) { return element("del", c); }

    /**
     * The {@code ins} element: an addition to the document.
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: transparent.
     * Attributes: globals; cite; datetime.
     * HTML 4.01: Inserted text.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/edits.html#the-ins-element">HTML: the ins element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/text.html#edef-ins">HTML 4.01: INS</a>
     */
    public static Node.Element ins(Content... c) { return element("ins", c); }

    /**
     * The {@code img} element: image.
     * <p>Categories: flow; phrasing; embedded; interactive; form-associated; palpable.
     * Parents: phrasing; picture.
     * Children: empty.
     * Attributes: globals; alt; src; srcset; sizes; crossorigin; usemap; ismap; controls; width; height;
     * referrerpolicy; decoding; loading; fetchpriority.
     * Void element: no children and no end tag.
     * HTML 4.01: Embedded image. (empty element (no end tag))
     *
     * @see <a href="https://html.spec.whatwg.org/dev/embedded-content.html#the-img-element">HTML: the img element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/objects.html#edef-IMG">HTML 4.01: IMG</a>
     */
    public static Node.Element img(Content... c) { return element("img", c); }

    /**
     * The {@code object} element: image, child navigable, or plugin.
     * <p>Categories: flow; phrasing; embedded; interactive; listed; form-associated; palpable.
     * Parents: phrasing.
     * Children: transparent.
     * Attributes: globals; data; type; name; form; width; height.
     * HTML 4.01: Generic embedded object.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/iframe-embed-object.html#the-object-element">HTML: the object element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/objects.html#edef-OBJECT">HTML 4.01: OBJECT</a>
     */
    public static Node.Element object(Content... c) { return element("object", c); }

    /**
     * The {@code map} element: image map.
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: transparent; area.
     * Attributes: globals; name.
     * HTML 4.01: Client-side image map.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/image-maps.html#the-map-element">HTML: the map element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/objects.html#edef-MAP">HTML 4.01: MAP</a>
     */
    public static Node.Element map(Content... c) { return element("map", c); }

    /**
     * The {@code area} element: hyperlink or dead area on an image map.
     * <p>Categories: flow; phrasing.
     * Parents: phrasing.
     * Children: empty.
     * Attributes: globals; alt; coords; shape; href; target; download; ping; rel; referrerpolicy.
     * Void element: no children and no end tag.
     * HTML 4.01: Client-side image map area. (empty element (no end tag))
     *
     * @see <a href="https://html.spec.whatwg.org/dev/image-maps.html#the-area-element">HTML: the area element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/objects.html#edef-MAP">HTML 4.01: AREA</a>
     */
    public static Node.Element area(Content... c) { return element("area", c); }

    // ---------- elements: tables

    /**
     * The {@code table} element: table.
     * <p>Categories: flow; palpable.
     * Parents: flow.
     * Children: caption; colgroup; thead; tbody; tfoot; tr; script-supporting elements.
     * Attributes: globals.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/tables.html#the-table-element">HTML: the table element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#edef-TABLE">HTML 4.01: TABLE</a>
     */
    public static Node.Element table(Content... c) { return element("table", c); }

    /**
     * The {@code caption} element: table caption.
     * <p>Categories: none.
     * Parents: table.
     * Children: flow.
     * Attributes: globals.
     * HTML 4.01: Table caption.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/tables.html#the-caption-element">HTML: the caption element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#edef-CAPTION">HTML 4.01: CAPTION</a>
     */
    public static Node.Element caption(Content... c) { return element("caption", c); }

    /**
     * The {@code colgroup} element: group of columns in a table.
     * <p>Categories: none.
     * Parents: table.
     * Children: col; template.
     * Attributes: globals; span.
     * HTML 4.01: Table column group.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/tables.html#the-colgroup-element">HTML: the colgroup element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#edef-COLGROUP">HTML 4.01: COLGROUP</a>
     */
    public static Node.Element colgroup(Content... c) { return element("colgroup", c); }

    /**
     * The {@code col} element: table column.
     * <p>Categories: none.
     * Parents: colgroup.
     * Children: empty.
     * Attributes: globals; span.
     * Void element: no children and no end tag.
     * HTML 4.01: Table column. (empty element (no end tag))
     *
     * @see <a href="https://html.spec.whatwg.org/dev/tables.html#the-col-element">HTML: the col element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#edef-COL">HTML 4.01: COL</a>
     */
    public static Node.Element col(Content... c) { return element("col", c); }

    /**
     * The {@code thead} element: group of heading rows in a table.
     * <p>Categories: none.
     * Parents: table.
     * Children: tr; script-supporting elements.
     * Attributes: globals.
     * HTML 4.01: Table header.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/tables.html#the-thead-element">HTML: the thead element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#edef-THEAD">HTML 4.01: THEAD</a>
     */
    public static Node.Element thead(Content... c) { return element("thead", c); }

    /**
     * The {@code tbody} element: group of rows in a table.
     * <p>Categories: none.
     * Parents: table.
     * Children: tr; script-supporting elements.
     * Attributes: globals.
     * HTML 4.01: Table body.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/tables.html#the-tbody-element">HTML: the tbody element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#edef-TBODY">HTML 4.01: TBODY</a>
     */
    public static Node.Element tbody(Content... c) { return element("tbody", c); }

    /**
     * The {@code tfoot} element: group of footer rows in a table.
     * <p>Categories: none.
     * Parents: table.
     * Children: tr; script-supporting elements.
     * Attributes: globals.
     * HTML 4.01: Table footer.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/tables.html#the-tfoot-element">HTML: the tfoot element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#edef-TFOOT">HTML 4.01: TFOOT</a>
     */
    public static Node.Element tfoot(Content... c) { return element("tfoot", c); }

    /**
     * The {@code tr} element: table row.
     * <p>Categories: none.
     * Parents: table; thead; tbody; tfoot.
     * Children: th; td; script-supporting elements.
     * Attributes: globals.
     * HTML 4.01: Table row.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/tables.html#the-tr-element">HTML: the tr element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#h-11.2.5">HTML 4.01: TR</a>
     */
    public static Node.Element tr(Content... c) { return element("tr", c); }

    /**
     * The {@code th} element: table header cell.
     * <p>Categories: interactive.
     * Parents: tr.
     * Children: flow.
     * Attributes: globals; colspan; rowspan; headers; scope; abbr.
     * HTML 4.01: Table header cell.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/tables.html#the-th-element">HTML: the th element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#edef-TD">HTML 4.01: TH</a>
     */
    public static Node.Element th(Content... c) { return element("th", c); }

    /**
     * The {@code td} element: table cell.
     * <p>Categories: none.
     * Parents: tr.
     * Children: flow.
     * Attributes: globals; colspan; rowspan; headers.
     * HTML 4.01: Table data cell.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/tables.html#the-td-element">HTML: the td element</a>
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#edef-TD">HTML 4.01: TD</a>
     */
    public static Node.Element td(Content... c) { return element("td", c); }

    // ---------- elements: forms

    /**
     * The {@code form} element: user-submittable form.
     * <p>Categories: flow; palpable.
     * Parents: flow.
     * Children: flow.
     * Attributes: globals; accept-charset; action; autocomplete; enctype; method; name; novalidate; rel; target.
     * HTML 4.01: Interactive form.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/forms.html#the-form-element">HTML: the form element</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#edef-FORM">HTML 4.01: FORM</a>
     */
    public static Node.Element form(Content... c) { return element("form", c); }

    /**
     * The {@code fieldset} element: group of form controls.
     * <p>Categories: flow; listed; form-associated; palpable.
     * Parents: flow.
     * Children: legend; flow.
     * Attributes: globals; disabled; form; name.
     * HTML 4.01: Form control group.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#the-fieldset-element">HTML: the fieldset element</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#edef-FIELDSET">HTML 4.01: FIELDSET</a>
     */
    public static Node.Element fieldset(Content... c) { return element("fieldset", c); }

    /**
     * The {@code legend} element: caption for fieldset.
     * <p>Categories: none.
     * Parents: fieldset; optgroup.
     * Children: phrasing; heading content.
     * Attributes: globals.
     * HTML 4.01: Fieldset legend.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#the-legend-element">HTML: the legend element</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#edef-LEGEND">HTML 4.01: LEGEND</a>
     */
    public static Node.Element legend(Content... c) { return element("legend", c); }

    /**
     * The {@code label} element: caption for a form control.
     * <p>Categories: flow; phrasing; interactive; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals; for.
     * HTML 4.01: Form field label text.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/forms.html#the-label-element">HTML: the label element</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#edef-LABEL">HTML 4.01: LABEL</a>
     */
    public static Node.Element label(Content... c) { return element("label", c); }

    /**
     * The {@code input} element: form control.
     * <p>Categories: flow; phrasing; interactive; listed; labelable; submittable; resettable; form-associated;
     * palpable.
     * Parents: phrasing.
     * Children: empty.
     * Attributes: globals; accept; alpha; alt; autocomplete; checked; colorspace; dirname; disabled; form;
     * formaction; formenctype; formmethod; formnovalidate; formtarget; height; list; max; maxlength; min; minlength;
     * multiple; name; pattern; placeholder; popovertarget; popovertargetaction; readonly; required; size; src; step;
     * type; value; width.
     * Void element: no children and no end tag.
     * HTML 4.01: Form control. (empty element (no end tag))
     *
     * @see <a href="https://html.spec.whatwg.org/dev/input.html#the-input-element">HTML: the input element</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#edef-INPUT">HTML 4.01: INPUT</a>
     */
    public static Node.Element input(Content... c) { return element("input", c); }

    /**
     * The {@code button} element: button control.
     * <p>Categories: flow; phrasing; interactive; listed; labelable; submittable; form-associated; palpable.
     * Parents: phrasing; select.
     * Children: phrasing.
     * Attributes: globals; command; commandfor; disabled; form; formaction; formenctype; formmethod; formnovalidate;
     * formtarget; name; popovertarget; popovertargetaction; type; value.
     * HTML 4.01: Push button.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#the-button-element">HTML: the button element</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#edef-BUTTON">HTML 4.01: BUTTON</a>
     */
    public static Node.Element button(Content... c) { return element("button", c); }

    /**
     * The {@code select} element: list box control.
     * <p>Categories: flow; phrasing; interactive; listed; labelable; submittable; resettable; form-associated;
     * palpable.
     * Parents: phrasing.
     * Children: option; optgroup; hr; script-supporting elements; noscript; div; button.
     * Attributes: globals; autocomplete; disabled; form; multiple; name; required; size.
     * HTML 4.01: Option selector.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#the-select-element">HTML: the select element</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#edef-SELECT">HTML 4.01: SELECT</a>
     */
    public static Node.Element select(Content... c) { return element("select", c); }

    /**
     * The {@code optgroup} element: group of options in a list box.
     * <p>Categories: none.
     * Parents: select; div.
     * Children: option; script-supporting elements; noscript; div; legend.
     * Attributes: globals; disabled; label.
     * HTML 4.01: Option group.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#the-optgroup-element">HTML: the optgroup element</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#edef-OPTGROUP">HTML 4.01: OPTGROUP</a>
     */
    public static Node.Element optgroup(Content... c) { return element("optgroup", c); }

    /**
     * The {@code option} element: option in a list box or combo box control.
     * <p>Categories: none.
     * Parents: select; datalist; optgroup; div.
     * Children: text; div; phrasing.
     * Attributes: globals; disabled; label; selected; value.
     * HTML 4.01: Selectable choice.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#the-option-element">HTML: the option element</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#edef-OPTION">HTML 4.01: OPTION</a>
     */
    public static Node.Element option(Content... c) { return element("option", c); }

    /**
     * The {@code textarea} element: multiline text controls.
     * <p>Categories: flow; phrasing; interactive; listed; labelable; submittable; resettable; form-associated;
     * palpable.
     * Parents: phrasing.
     * Children: text.
     * Attributes: globals; autocomplete; cols; dirname; disabled; form; maxlength; minlength; name; placeholder;
     * readonly; required; rows; wrap.
     * HTML 4.01: Multi-line text field.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#the-textarea-element">HTML: the textarea element</a>
     * @see <a href="https://www.w3.org/TR/html401/interact/forms.html#edef-TEXTAREA">HTML 4.01: TEXTAREA</a>
     */
    public static Node.Element textarea(Content... c) { return element("textarea", c); }

    // ---------- form controls from a Field, in the dialect

    /** An {@code <input>} with the current value; a password or file never echoes it. */
    public static Node.Element input(Dialect dialect, Field field, String current, Content... extra) {
        val content = control(dialect, "input", field);
        if (!current.isEmpty() && field.type() != InputType.PASSWORD && field.type() != InputType.FILE) {
            content.add(value(current));
        }
        return element("input", join(content, extra));
    }

    /** One checkbox or radio of the field, submitting {@code choice} when checked. */
    public static Node.Element choice(Dialect dialect, Field field, String choice, boolean checked, Content... extra) {
        val content = control(dialect, "input", field);
        content.add(value(choice));
        if (checked) {
            content.add(checked());
        }
        return element("input", join(content, extra));
    }

    /**
     * A {@code <textarea>} with the current value as its content. A newline is written right after the start tag:
     * the HTML parser drops one newline there, so a value that starts with a newline keeps it.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/syntax.html#element-restrictions">HTML: restrictions on
     * contents of textarea</a>
     */
    public static Node.Element textarea(Dialect dialect, Field field, String current, Content... extra) {
        val content = control(dialect, "textarea", field);
        content.addAll(List.of(extra));
        content.add(text(Text.data("\n" + current)));
        return element("textarea", content.toArray(Content[]::new));
    }

    public static Node.Element select(Dialect dialect, Field field, List<Option> options, Collection<String> selected,
                                      Content... extra) {
        val content = control(dialect, "select", field);
        if (field.multiple()) {
            content.add(multiple());
        }
        content.addAll(List.of(extra));
        options.forEach(item -> content.add(selected.contains(item.value())
                ? option(value(item.value()), selected(), text(item.label()))
                : option(value(item.value()), text(item.label()))));
        return element("select", content.toArray(Content[]::new));
    }

    private static List<Content> control(Dialect dialect, String tag, Field field) {
        val content = new ArrayList<Content>();
        if ("input".equals(tag)) {
            content.add(type(dialect.inputType(field.type())));
        }
        content.add(name(field.name()));
        content.addAll(dialect.constraints(tag, field));
        return content;
    }

    private static Content[] join(List<Content> content, Content... extra) {
        content.addAll(List.of(extra));
        return content.toArray(Content[]::new);
    }
}
