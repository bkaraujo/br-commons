package br.commons.html.html4;

import br.commons.html.Html;
import br.commons.html.core.Attribute;
import br.commons.html.core.Content;
import br.commons.html.core.Node;
import br.commons.locale.i18n.Text;
import org.jspecify.annotations.NullMarked;

/**
 * HTML 4.01 Strict (W3C): the common DSL of {@link Html} plus what this version has and HTML (WHATWG) dropped,
 * bound to {@link Html4Dialect}
 * ({@link #DIALECT}, passed to the form controls of {@link Html}: {@code input(DIALECT, field, value)}).
 * {@code import static br.commons.html.html4.Html4.*} brings both.
 *
 * <p>Only Strict: elements and attributes the 4.01 DTD marks deprecated or Transitional/Frameset only
 * ({@code font}, {@code center}, {@code iframe}, {@code target}, {@code bgcolor}…) are left out. Without sectioning
 * elements, a layout uses {@code div}.
 */
@NullMarked
public abstract class Html4 extends Html {

    public static final Html4Dialect DIALECT = Html4Dialect.INSTANCE;

    /** Not instantiated: a static DSL. */
    private Html4() {}

    // ---------- document, in this version

    /** The whole page, starting with the HTML 4.01 Strict doctype. */
    public static String document(Node root) { return document(DIALECT, root); }

    /** {@code <meta http-equiv="Content-Type" content="text/html; charset=…">}: HTML 4 has no {@code charset}. */
    public static Node.Element metaCharset(String encoding) { return metaCharset(DIALECT, encoding); }

    // ---------- attributes obsolete in HTML (WHATWG)

    /**
     * The {@code align} attribute. HTML 4.01:
     * <ul>
     * <li>On {@code caption}: relative to table (type {@code %CAlign;}, default {@code #IMPLIED}, deprecated)</li>
     * <li>On {@code applet}, {@code iframe}, {@code img}, {@code input}, {@code object}: vertical or horizontal
     * alignment (type {@code %IAlign;}, default {@code #IMPLIED}, deprecated)</li>
     * <li>On {@code legend}: relative to fieldset (type {@code %LAlign;}, default {@code #IMPLIED}, deprecated)</li>
     * <li>On {@code table}: table position relative to window (type {@code %TAlign;}, default {@code #IMPLIED},
     * deprecated)</li>
     * <li>On {@code hr}:  (type {@code (left | center | right)}, default {@code #IMPLIED}, deprecated)</li>
     * <li>On {@code div}, {@code h1}, {@code h2}, {@code h3}, {@code h4}, {@code h5}, {@code h6}, {@code p}: align,
     * text alignment (type {@code (left | center | right | justify)}, default {@code #IMPLIED}, deprecated)</li>
     * <li>On {@code col}, {@code colgroup}, {@code tbody}, {@code td}, {@code tfoot}, {@code th}, {@code thead},
     * {@code tr}:  (type {@code (left | center | right | justify | char)}, default {@code #IMPLIED})</li>
     * </ul>
     *
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#adef-align-CAPTION">HTML 4.01: align</a>
     */
    public static Attribute align(CellAlign align) { return attribute("align", align); }

    /**
     * The {@code axis} attribute. HTML 4.01: On {@code td}, {@code th}: comma-separated list of related headers (type
     * {@code CDATA}, default {@code #IMPLIED}).
     *
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#adef-axis">HTML 4.01: axis</a>
     */
    public static Attribute axis(String categories) { return attribute("axis", categories); }

    /**
     * The {@code border} attribute. HTML 4.01:
     * <ul>
     * <li>On {@code table}: controls frame width around table (type {@code %Pixels;}, default {@code #IMPLIED})</li>
     * <li>On {@code img}, {@code object}: link border width (type {@code %Pixels;}, default {@code #IMPLIED},
     * deprecated)</li>
     * </ul>
     *
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#adef-border-TABLE">HTML 4.01: border</a>
     */
    public static Attribute border(int pixels) { return attribute("border", pixels); }

    /**
     * The {@code cellpadding} attribute. HTML 4.01: On {@code table}: spacing within cells (type {@code %Length;},
     * default {@code #IMPLIED}).
     *
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#adef-cellpadding">HTML 4.01: cellpadding</a>
     */
    public static Attribute cellpadding(String length) { return attribute("cellpadding", length); }

    /**
     * The {@code cellspacing} attribute. HTML 4.01: On {@code table}: spacing between cells (type {@code %Length;},
     * default {@code #IMPLIED}).
     *
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#adef-cellspacing">HTML 4.01: cellspacing</a>
     */
    public static Attribute cellspacing(String length) { return attribute("cellspacing", length); }

    /**
     * The {@code char} attribute. HTML 4.01: On {@code col}, {@code colgroup}, {@code tbody}, {@code td},
     * {@code tfoot}, {@code th}, {@code thead}, {@code tr}: alignment char, e.g. char=':' (type {@code %Character;},
     * default {@code #IMPLIED}).
     * <p>Named {@code alignChar}: {@code char} is a Java keyword.
     * {@code char} of table cells ({@code char} is a Java keyword).
     *
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#adef-char">HTML 4.01: char</a>
     */
    public static Attribute alignChar(String character) { return attribute("char", character); }

    /**
     * The {@code charoff} attribute. HTML 4.01: On {@code col}, {@code colgroup}, {@code tbody}, {@code td},
     * {@code tfoot}, {@code th}, {@code thead}, {@code tr}: offset for alignment char (type {@code %Length;}, default
     * {@code #IMPLIED}).
     *
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#adef-charoff">HTML 4.01: charoff</a>
     */
    public static Attribute charoff(String length) { return attribute("charoff", length); }

    /**
     * The {@code classid} attribute. HTML 4.01: On {@code object}: identifies an implementation (type {@code %URI;},
     * default {@code #IMPLIED}).
     *
     * @see <a href="https://www.w3.org/TR/html401/struct/objects.html#adef-classid">HTML 4.01: classid</a>
     */
    public static Attribute classid(String url) { return attribute("classid", url); }

    /**
     * The {@code codebase} attribute. HTML 4.01:
     * <ul>
     * <li>On {@code object}: base URI for classid, data, archive (type {@code %URI;}, default {@code #IMPLIED})</li>
     * <li>On {@code applet}: optional base URI for applet (type {@code %URI;}, default {@code #IMPLIED},
     * deprecated)</li>
     * </ul>
     *
     * @see <a href="https://www.w3.org/TR/html401/struct/objects.html#adef-codebase-OBJECT">HTML 4.01: codebase</a>
     */
    public static Attribute codebase(String url) { return attribute("codebase", url); }

    /**
     * The {@code codetype} attribute. HTML 4.01: On {@code object}: content type for code (type
     * {@code %ContentType;}, default {@code #IMPLIED}).
     *
     * @see <a href="https://www.w3.org/TR/html401/struct/objects.html#adef-codetype">HTML 4.01: codetype</a>
     */
    public static Attribute codetype(String mediaType) { return attribute("codetype", mediaType); }

    /**
     * The {@code declare} attribute. HTML 4.01: On {@code object}: declare but don't instantiate flag (type
     * {@code (declare)}, default {@code #IMPLIED}).
     *
     * @see <a href="https://www.w3.org/TR/html401/struct/objects.html#adef-declare">HTML 4.01: declare</a>
     */
    public static Attribute declare() { return flag("declare"); }

    /**
     * The {@code frame} attribute. HTML 4.01: On {@code table}: which parts of frame to render (type
     * {@code %TFrame;}, default {@code #IMPLIED}).
     *
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#adef-frame">HTML 4.01: frame</a>
     */
    public static Attribute frame(TableFrame frame) { return attribute("frame", frame); }

    /**
     * The {@code height} attribute. HTML 4.01:
     * <ul>
     * <li>On {@code iframe}: frame height (type {@code %Length;}, default {@code #IMPLIED})</li>
     * <li>On {@code td}, {@code th}: height for cell (type {@code %Length;}, default {@code #IMPLIED},
     * deprecated)</li>
     * <li>On {@code img}, {@code object}: override height (type {@code %Length;}, default {@code #IMPLIED})</li>
     * <li>On {@code applet}: initial height (type {@code %Length;}, default {@code #REQUIRED}, deprecated)</li>
     * </ul>
     * <p>{@code height} as a length: pixels or percentage ({@code "50%"}).
     *
     * @see <a href="https://www.w3.org/TR/html401/present/frames.html#adef-height-IFRAME">HTML 4.01: height</a>
     */
    public static Attribute height(String length) { return attribute("height", length); }

    /**
     * The {@code longdesc} attribute. HTML 4.01:
     * <ul>
     * <li>On {@code img}: link to long description (complements alt) (type {@code %URI;}, default
     * {@code #IMPLIED})</li>
     * <li>On {@code frame}, {@code iframe}: link to long description (complements title) (type {@code %URI;}, default
     * {@code #IMPLIED})</li>
     * </ul>
     *
     * @see <a href="https://www.w3.org/TR/html401/struct/objects.html#adef-longdesc-IMG">HTML 4.01: longdesc</a>
     */
    public static Attribute longdesc(String url) { return attribute("longdesc", url); }

    /**
     * The {@code nohref} attribute. HTML 4.01: On {@code area}: this region has no action (type {@code (nohref)},
     * default {@code #IMPLIED}).
     *
     * @see <a href="https://www.w3.org/TR/html401/struct/objects.html#adef-nohref">HTML 4.01: nohref</a>
     */
    public static Attribute nohref() { return flag("nohref"); }

    /**
     * The {@code rev} attribute. HTML 4.01: On {@code a}, {@code link}: reverse link types (type {@code %LinkTypes;},
     * default {@code #IMPLIED}).
     *
     * @see <a href="https://www.w3.org/TR/html401/struct/links.html#adef-rev">HTML 4.01: rev</a>
     */
    public static Attribute rev(String types) { return attribute("rev", types); }

    /**
     * The {@code rules} attribute. HTML 4.01: On {@code table}: rulings between rows and cols (type {@code %TRules;},
     * default {@code #IMPLIED}).
     *
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#adef-rules">HTML 4.01: rules</a>
     */
    public static Attribute rules(TableRules rules) { return attribute("rules", rules); }

    /**
     * The {@code scheme} attribute. HTML 4.01: On {@code meta}: select form of content (type {@code CDATA}, default
     * {@code #IMPLIED}).
     *
     * @see <a href="https://www.w3.org/TR/html401/struct/global.html#adef-scheme">HTML 4.01: scheme</a>
     */
    public static Attribute scheme(String scheme) { return attribute("scheme", scheme); }

    /**
     * The {@code standby} attribute. HTML 4.01: On {@code object}: message to show while loading (type
     * {@code %Text;}, default {@code #IMPLIED}).
     * <p>Takes a {@code Text}: a person reads it, so it is never a free string.
     *
     * @see <a href="https://www.w3.org/TR/html401/struct/objects.html#adef-standby">HTML 4.01: standby</a>
     */
    public static Attribute standby(Text text) { return attribute("standby", text.value()); }

    /**
     * The {@code summary} attribute. HTML 4.01: On {@code table}: purpose/structure for speech output (type
     * {@code %Text;}, default {@code #IMPLIED}).
     * <p>Takes a {@code Text}: a person reads it, so it is never a free string.
     *
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#adef-summary">HTML 4.01: summary</a>
     */
    public static Attribute summary(Text text) { return attribute("summary", text.value()); }

    /**
     * The {@code valign} attribute. HTML 4.01: On {@code col}, {@code colgroup}, {@code tbody}, {@code td},
     * {@code tfoot}, {@code th}, {@code thead}, {@code tr}: vertical alignment in cells (type
     * {@code (top | middle | bottom | baseline)}, default {@code #IMPLIED}).
     *
     * @see <a href="https://www.w3.org/TR/html401/struct/tables.html#adef-valign">HTML 4.01: valign</a>
     */
    public static Attribute valign(CellVerticalAlign align) { return attribute("valign", align); }

    /**
     * The {@code valuetype} attribute. HTML 4.01: On {@code param}: How to interpret value (type
     * {@code (DATA | REF | OBJECT)}, default {@code DATA}).
     *
     * @see <a href="https://www.w3.org/TR/html401/struct/objects.html#adef-valuetype">HTML 4.01: valuetype</a>
     */
    public static Attribute valuetype(ParamValueType type) { return attribute("valuetype", type); }

    /**
     * The {@code width} attribute. HTML 4.01:
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
     * <p>{@code width} as a length: pixels or percentage ({@code "50%"}).
     *
     * @see <a href="https://www.w3.org/TR/html401/present/graphics.html#adef-width-HR">HTML 4.01: width</a>
     */
    public static Attribute width(String length) { return attribute("width", length); }

    // ---------- elements obsolete in HTML (WHATWG)

    /**
     * The {@code acronym} element. HTML 4.01: Indicates an acronym (e.g., WAC, radar, etc.).
     *
     * @see <a href="https://www.w3.org/TR/html401/struct/text.html#edef-ACRONYM">HTML 4.01: ACRONYM</a>
     */
    public static Node.Element acronym(Content... c) { return element("acronym", c); }

    /**
     * The {@code big} element. HTML 4.01: Large text style.
     *
     * @see <a href="https://www.w3.org/TR/html401/present/graphics.html#edef-BIG">HTML 4.01: BIG</a>
     */
    public static Node.Element big(Content... c) { return element("big", c); }

    /**
     * The {@code param} element. HTML 4.01: Named property value. (empty element (no end tag))
     *
     * @see <a href="https://www.w3.org/TR/html401/struct/objects.html#edef-PARAM">HTML 4.01: PARAM</a>
     */
    public static Node.Element param(Content... c) { return element("param", c); }

    /**
     * The {@code tt} element. HTML 4.01: Teletype or monospaced text style.
     *
     * @see <a href="https://www.w3.org/TR/html401/present/graphics.html#edef-TT">HTML 4.01: TT</a>
     */
    public static Node.Element tt(Content... c) { return element("tt", c); }
}
