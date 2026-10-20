package br.commons.lang;

import br.commons.lang.html.FormEnctype;
import br.commons.lang.html.FormMethod;
import br.commons.lang.html.Html;
import br.commons.lang.html.core.Attribute;
import br.commons.lang.html.core.Content;
import br.commons.lang.html.core.Node;
import br.commons.lang.html.html5.*;
import br.commons.locale.i18n.Text;
import org.jspecify.annotations.NullMarked;

/**
 * HTML (WHATWG living standard, as in MDN): the common DSL of {@link Html} plus what HTML 4.01 Strict does not
 * have, bound to {@link Html5Dialect}
 * ({@link #DIALECT}, passed to the form controls of {@link Html}: {@code input(DIALECT, field, value)}).
 * {@code import static br.commons.lang.Html5.*} brings both.
 *
 * <p>Elements deprecated by the living standard ({@code acronym}, {@code big}, {@code tt}, {@code param}…) are
 * left out; {@code s}, {@code u} and {@code menu}, deprecated in HTML 4, are back with new meaning.
 * {@code geolocation} and {@code selectedcontent} are experimental in MDN.
 */
@NullMarked
public abstract class Html5 extends Html {

    public static final Html5Dialect DIALECT = Html5Dialect.INSTANCE;

    /** Not instantiated: a static DSL. */
    private Html5() {}

    // ---------- document, in this version

    /** The whole page, starting with {@code <!DOCTYPE html>}. */
    public static String document(Node root) { return document(DIALECT, root); }

    /** {@code <meta charset="…">}. */
    public static Node.Element metaCharset(String encoding) { return metaCharset(DIALECT, encoding); }

    // ---------- global attributes

    /**
     * The {@code autocapitalize} attribute: recommended autocapitalization behavior (for supported input methods).
     * <p>On every HTML element (global attribute).
     * Value: "on"; "off"; "none"; "sentences"; "words"; "characters".
     *
     * @see <a href="https://html.spec.whatwg.org/dev/interaction.html#attr-autocapitalize">HTML: autocapitalize on every element</a>
     */
    public static Attribute autocapitalize(Autocapitalize mode) { return attribute("autocapitalize", mode); }

    /**
     * The {@code autofocus} attribute: automatically focus the element when the page is loaded.
     * <p>On every HTML element (global attribute).
     * Value: Boolean attribute.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/interaction.html#attr-fe-autofocus">HTML: autofocus on every element</a>
     */
    public static Attribute autofocus() { return flag("autofocus"); }

    /**
     * The {@code contenteditable} attribute: whether the element is editable.
     * <p>On every HTML element (global attribute).
     * Value: "true"; "false"; "plaintext-only"; the empty string.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/interaction.html#attr-contenteditable">HTML: contenteditable on every element</a>
     */
    public static Attribute contenteditable(boolean editable) { return attribute("contenteditable", String.valueOf(editable)); }

    /**
     * The {@code dir} attribute, on each element where it applies:
     * <ul>
     * <li>every HTML element (global attribute) — The text directionality of the element. Value: "ltr"; "rtl";
     * "auto".</li>
     * <li>{@code bdo} — The text directionality of the element. Value: "ltr"; "rtl".</li>
     * </ul>
     * <p>Writes {@code dir="auto"}: the direction comes from the first strong character of the content.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/dom.html#attr-dir-auto">HTML: the auto state of dir</a>
     * @see <a href="https://html.spec.whatwg.org/dev/dom.html#attr-dir">HTML: dir on every element</a>
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-bdo-element">HTML: dir on bdo</a>
     */
    public static Attribute dirAuto() { return attribute("dir", "auto"); }

    /**
     * The {@code draggable} attribute: whether the element is draggable.
     * <p>On every HTML element (global attribute).
     * Value: "true"; "false".
     *
     * @see <a href="https://html.spec.whatwg.org/dev/dnd.html#attr-draggable">HTML: draggable on every element</a>
     */
    public static Attribute draggable(boolean draggable) { return attribute("draggable", String.valueOf(draggable)); }

    /**
     * The {@code enterkeyhint} attribute: hint for selecting an enter key action.
     * <p>On every HTML element (global attribute).
     * Value: "enter"; "done"; "go"; "next"; "previous"; "search"; "send".
     *
     * @see <a href="https://html.spec.whatwg.org/dev/interaction.html#attr-enterkeyhint">HTML: enterkeyhint on every element</a>
     */
    public static Attribute enterkeyhint(EnterKeyHint hint) { return attribute("enterkeyhint", hint); }

    /**
     * The {@code hidden} attribute: whether the element is relevant.
     * <p>On every HTML element (global attribute).
     * Value: "until-found"; "hidden"; the empty string.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/interaction.html#attr-hidden">HTML: hidden on every element</a>
     */
    public static Attribute hidden() { return flag("hidden"); }

    /**
     * The {@code hidden} attribute: whether the element is relevant.
     * <p>On every HTML element (global attribute).
     * Value: "until-found"; "hidden"; the empty string.
     * <p>Writes {@code hidden="until-found"}: hidden, but found by find-in-page and fragment navigation, which reveal
     * it.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/interaction.html#attr-hidden-until-found-state">HTML: the hidden until found state</a>
     * @see <a href="https://html.spec.whatwg.org/dev/interaction.html#attr-hidden">HTML: hidden on every element</a>
     */
    public static Attribute hiddenUntilFound() { return attribute("hidden", "until-found"); }

    /**
     * The {@code inert} attribute: whether the element is inert.
     * <p>On every HTML element (global attribute).
     * Value: Boolean attribute.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/interaction.html#the-inert-attribute">HTML: inert on every element</a>
     */
    public static Attribute inert() { return flag("inert"); }

    /**
     * The {@code inputmode} attribute: hint for selecting an input modality.
     * <p>On every HTML element (global attribute).
     * Value: "none"; "text"; "tel"; "email"; "url"; "numeric"; "decimal"; "search".
     *
     * @see <a href="https://html.spec.whatwg.org/dev/interaction.html#attr-inputmode">HTML: inputmode on every element</a>
     */
    public static Attribute inputmode(InputMode mode) { return attribute("inputmode", mode); }

    /**
     * The {@code itemid} attribute: global identifier for a microdata item.
     * <p>On every HTML element (global attribute).
     * Value: Valid URL potentially surrounded by spaces.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/microdata.html#attr-itemid">HTML: itemid on every element</a>
     */
    public static Attribute itemid(String url) { return attribute("itemid", url); }

    /**
     * The {@code itemprop} attribute: property names of a microdata item.
     * <p>On every HTML element (global attribute).
     * Value: Unordered set of unique space-separated tokens consisting of valid absolute URLs, defined property
     * names, or text.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/microdata.html#names:-the-itemprop-attribute">HTML: itemprop on every element</a>
     */
    public static Attribute itemprop(String names) { return attribute("itemprop", names); }

    /**
     * The {@code itemref} attribute: referenced elements.
     * <p>On every HTML element (global attribute).
     * Value: Unordered set of unique space-separated tokens consisting of IDs.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/microdata.html#attr-itemref">HTML: itemref on every element</a>
     */
    public static Attribute itemref(String ids) { return attribute("itemref", ids); }

    /**
     * The {@code itemscope} attribute: introduces a microdata item.
     * <p>On every HTML element (global attribute).
     * Value: Boolean attribute.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/microdata.html#attr-itemscope">HTML: itemscope on every element</a>
     */
    public static Attribute itemscope() { return flag("itemscope"); }

    /**
     * The {@code itemtype} attribute: item types of a microdata item.
     * <p>On every HTML element (global attribute).
     * Value: Unordered set of unique space-separated tokens consisting of valid absolute URLs.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/microdata.html#attr-itemtype">HTML: itemtype on every element</a>
     */
    public static Attribute itemtype(String urls) { return attribute("itemtype", urls); }

    /**
     * The {@code nonce} attribute: cryptographic nonce used in Content Security Policy checks [CSP].
     * <p>On every HTML element (global attribute).
     * Value: Text.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/urls-and-fetching.html#attr-nonce">HTML: nonce on every element</a>
     */
    public static Attribute nonce(String nonce) { return attribute("nonce", nonce); }

    /**
     * The {@code part} attribute: the space-separated names of this element as a part of its shadow tree, which CSS
     * outside it styles with {@code ::part()}. Global attribute.
     *
     * @see <a href="https://www.w3.org/TR/css-shadow-parts-1/#part-attr">CSS Shadow Parts: the part attribute</a>
     */
    public static Attribute part(String names) { return attribute("part", names); }

    /**
     * The {@code popover} attribute: makes the element a popover element.
     * <p>On every HTML element (global attribute).
     * Value: "auto"; "manual"; "hint"; the empty string.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/popover.html#attr-popover">HTML: popover on every element</a>
     */
    public static Attribute popover(PopoverState state) { return attribute("popover", state); }

    /**
     * The {@code role} attribute (WAI-ARIA): the semantics of the element for assistive technology, one of the ARIA
     * roles. Prefer the native element when one exists (a {@code button}, not {@code role="button"}).
     *
     * @see <a href="https://www.w3.org/TR/wai-aria-1.2/#host_general_role">WAI-ARIA 1.2: role attribute</a>
     */
    public static Attribute role(String role) { return attribute("role", role); }

    /**
     * The {@code slot} attribute: the element's desired slot.
     * <p>On every HTML element (global attribute).
     * Value: Text.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/dom.html#the-id-attribute">HTML: slot on every element</a>
     */
    public static Attribute slot(String name) { return attribute("slot", name); }

    /**
     * The {@code spellcheck} attribute: whether the element is to have its spelling and grammar checked.
     * <p>On every HTML element (global attribute).
     * Value: "true"; "false"; the empty string.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/interaction.html#attr-spellcheck">HTML: spellcheck on every element</a>
     */
    public static Attribute spellcheck(boolean check) { return attribute("spellcheck", String.valueOf(check)); }

    /**
     * The {@code translate} attribute: whether the element is to be translated when the page is localized.
     * <p>On every HTML element (global attribute).
     * Value: "yes"; "no"; the empty string.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/dom.html#attr-translate">HTML: translate on every element</a>
     */
    public static Attribute translate(boolean translate) { return attribute("translate", translate ? "yes" : "no"); }

    /**
     * A custom data attribute {@code data-*}: private data for the page's scripts and CSS, with no meaning to the
     * browser. The name after {@code data-} must have no uppercase ASCII letters; scripts read it as
     * {@code element.dataset}. Global attribute.
     * <p>{@code data-<name>}, e.g. texts that the page script ({@code app.js}) shows ({@code data-msg-*}, spec §2.5).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/dom.html#embedding-custom-non-visible-data-with-the-data-*-attributes">HTML: custom data attributes</a>
     */
    public static Attribute data(String name, String value) { return attribute("data-" + name, value); }

    /**
     * The {@code aria-label} attribute (WAI-ARIA): a string that labels the element when no visible text does (an
     * icon button). Global for HTML elements; ignored when the role prohibits naming.
     * <p>Takes a {@code Text}: a person reads it, so it is never a free string.
     *
     * @see <a href="https://www.w3.org/TR/wai-aria-1.2/#aria-label">WAI-ARIA 1.2: aria-label</a>
     */
    public static Attribute ariaLabel(Text text) { return attribute("aria-label", text.value()); }

    /**
     * The {@code aria-labelledby} attribute (WAI-ARIA): the ids of the elements whose text labels this one; wins over
     * {@code aria-label}.
     *
     * @see <a href="https://www.w3.org/TR/wai-aria-1.2/#aria-labelledby">WAI-ARIA 1.2: aria-labelledby</a>
     */
    public static Attribute ariaLabelledby(String ids) { return attribute("aria-labelledby", ids); }

    /**
     * The {@code aria-describedby} attribute (WAI-ARIA): the ids of the elements whose text describes this one (help,
     * format, errors).
     *
     * @see <a href="https://www.w3.org/TR/wai-aria-1.2/#aria-describedby">WAI-ARIA 1.2: aria-describedby</a>
     */
    public static Attribute ariaDescribedby(String ids) { return attribute("aria-describedby", ids); }

    /**
     * The {@code aria-hidden} attribute (WAI-ARIA): {@code true} removes the element and its subtree from the
     * accessibility tree; it stays visible. Never on focusable content.
     *
     * @see <a href="https://www.w3.org/TR/wai-aria-1.2/#aria-hidden">WAI-ARIA 1.2: aria-hidden</a>
     */
    public static Attribute ariaHidden(boolean hidden) { return attribute("aria-hidden", String.valueOf(hidden)); }

    /**
     * The {@code aria-expanded} attribute (WAI-ARIA): whether the content the element controls (a disclosure button,
     * a menu button) is shown.
     *
     * @see <a href="https://www.w3.org/TR/wai-aria-1.2/#aria-expanded">WAI-ARIA 1.2: aria-expanded</a>
     */
    public static Attribute ariaExpanded(boolean expanded) { return attribute("aria-expanded", String.valueOf(expanded)); }

    /**
     * The {@code aria-controls} attribute (WAI-ARIA): the ids of the elements whose content or presence this one
     * controls.
     *
     * @see <a href="https://www.w3.org/TR/wai-aria-1.2/#aria-controls">WAI-ARIA 1.2: aria-controls</a>
     */
    public static Attribute ariaControls(String ids) { return attribute("aria-controls", ids); }

    // ---------- element attributes

    /**
     * The {@code allow} attribute: permissions policy to be applied to the iframe's contents.
     * <p>On {@code iframe}.
     * Value: Serialized permissions policy.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/iframe-embed-object.html#attr-iframe-allow">HTML: allow on iframe</a>
     */
    public static Attribute allow(String policy) { return attribute("allow", policy); }

    /**
     * The {@code as} attribute: destination for a preload request (for rel="preload" and rel="modulepreload").
     * <p>On {@code link}.
     * Value: Preload destination, for rel="preload"; module preload destination, for rel="modulepreload".
     *
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#attr-link-as">HTML: as on link</a>
     */
    public static Attribute as(String destination) { return attribute("as", destination); }

    /**
     * The {@code async} attribute: execute script when available, without blocking while fetching.
     * <p>On {@code script}.
     * Value: Boolean attribute.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/scripting.html#attr-script-async">HTML: async on script</a>
     */
    public static Attribute async() { return flag("async"); }

    /**
     * The {@code autocomplete} attribute, on each element where it applies:
     * <ul>
     * <li>{@code form} — Default setting for autofill feature for controls in the form. Value: "on"; "off".</li>
     * <li>{@code input}, {@code select}, {@code textarea} — Hint for form autofill feature. Value: Autofill field
     * name and related tokens.</li>
     * </ul>
     *
     * @see <a href="https://html.spec.whatwg.org/dev/forms.html#attr-form-autocomplete">HTML: autocomplete on form</a>
     * @see <a href="https://html.spec.whatwg.org/dev/form-control-infrastructure.html#attr-fe-autocomplete">HTML: autocomplete on input</a>
     */
    public static Attribute autocomplete(String tokens) { return attribute("autocomplete", tokens); }

    /**
     * The {@code autoplay} attribute: hint that the media resource can be started automatically when the page is
     * loaded.
     * <p>On {@code audio}, {@code video}.
     * Value: Boolean attribute.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/media.html#attr-media-autoplay">HTML: autoplay on audio</a>
     */
    public static Attribute autoplay() { return flag("autoplay"); }

    /**
     * The {@code capture} attribute (HTML Media Capture) of {@code input type=file}: asks to capture new media with
     * the device camera or microphone instead of choosing a file; {@code user} for the user-facing camera,
     * {@code environment} for the outward-facing one.
     *
     * @see <a href="https://www.w3.org/TR/html-media-capture/#the-capture-attribute">HTML Media Capture: the capture attribute</a>
     */
    public static Attribute capture(String facing) { return attribute("capture", facing); }

    /**
     * The {@code controls} attribute: show user agent controls.
     * <p>On {@code audio}, {@code video img}.
     * Value: Boolean attribute.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/media.html#attr-media-controls">HTML: controls on audio</a>
     * @see <a href="https://html.spec.whatwg.org/dev/embedded-content.html#attr-img-controls">HTML: controls on video img</a>
     */
    public static Attribute controls() { return flag("controls"); }

    /**
     * The {@code crossorigin} attribute: how the element handles crossorigin requests.
     * <p>On {@code audio}, {@code img}, {@code link}, {@code script}, {@code video}.
     * Value: "anonymous"; "use-credentials"; the empty string.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/media.html#attr-media-crossorigin">HTML: crossorigin on audio</a>
     * @see <a href="https://html.spec.whatwg.org/dev/embedded-content.html#attr-img-crossorigin">HTML: crossorigin on img</a>
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#attr-link-crossorigin">HTML: crossorigin on link</a>
     * @see <a href="https://html.spec.whatwg.org/dev/scripting.html#attr-script-crossorigin">HTML: crossorigin on script</a>
     */
    public static Attribute crossorigin(CrossOrigin mode) { return attribute("crossorigin", mode); }

    /**
     * The {@code decoding} attribute: decoding hint to use when processing this image for presentation.
     * <p>On {@code img}.
     * Value: "sync"; "async"; "auto".
     *
     * @see <a href="https://html.spec.whatwg.org/dev/embedded-content.html#attr-img-decoding">HTML: decoding on img</a>
     */
    public static Attribute decoding(ImageDecoding decoding) { return attribute("decoding", decoding); }

    /**
     * The {@code default} attribute: enable the track if no other text track is more suitable.
     * <p>On {@code track}.
     * Value: Boolean attribute.
     * <p>Named {@code defaultTrack}: {@code default} is a Java keyword.
     * {@code default} of {@code track} ({@code default} is a Java keyword).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/media.html#attr-track-default">HTML: default on track</a>
     */
    public static Attribute defaultTrack() { return flag("default"); }

    /**
     * The {@code dirname} attribute: name of form control to use for sending the element's directionality in form
     * submission.
     * <p>On {@code input}, {@code textarea}.
     * Value: Text.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-control-infrastructure.html#attr-fe-dirname">HTML: dirname on input</a>
     */
    public static Attribute dirname(String name) { return attribute("dirname", name); }

    /**
     * The {@code download} attribute: whether to download the resource instead of navigating to it, and its filename
     * if so.
     * <p>On {@code a}, {@code area}.
     * Value: Text.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/links.html#attr-hyperlink-download">HTML: download on a</a>
     */
    public static Attribute download() { return flag("download"); }

    /**
     * The {@code download} attribute: whether to download the resource instead of navigating to it, and its filename
     * if so.
     * <p>On {@code a}, {@code area}.
     * Value: Text.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/links.html#attr-hyperlink-download">HTML: download on a</a>
     */
    public static Attribute download(String filename) { return attribute("download", filename); }

    /**
     * The {@code fetchpriority} attribute: sets the priority for fetches initiated by the element.
     * <p>On {@code img}, {@code link}, {@code script}.
     * Value: "auto"; "high"; "low".
     *
     * @see <a href="https://html.spec.whatwg.org/dev/embedded-content.html#attr-img-fetchpriority">HTML: fetchpriority on img</a>
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#attr-link-fetchpriority">HTML: fetchpriority on link</a>
     * @see <a href="https://html.spec.whatwg.org/dev/scripting.html#attr-script-fetchpriority">HTML: fetchpriority on script</a>
     */
    public static Attribute fetchpriority(FetchPriority priority) { return attribute("fetchpriority", priority); }

    /**
     * The {@code form} attribute: associates the element with a form element.
     * <p>On {@code button}, {@code fieldset}, {@code input}, {@code object}, {@code output}, {@code select},
     * {@code textarea}, {@code form-associated custom elements}.
     * Value: ID.
     * <p>{@code form}: the {@code id} of the form that owns the control, wherever it is.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-control-infrastructure.html#attr-fae-form">HTML: form on button</a>
     */
    public static Attribute form(String id) { return attribute("form", id); }

    /**
     * The {@code formaction} attribute: uRL to use for form submission.
     * <p>On {@code button}, {@code input}.
     * Value: Valid non-empty URL potentially surrounded by spaces.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-control-infrastructure.html#attr-fs-formaction">HTML: formaction on button</a>
     */
    public static Attribute formaction(String url) { return attribute("formaction", url); }

    /**
     * The {@code formenctype} attribute: entry list encoding type to use for form submission.
     * <p>On {@code button}, {@code input}.
     * Value: "application/x-www-form-urlencoded"; "multipart/form-data"; "text/plain".
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-control-infrastructure.html#attr-fs-formenctype">HTML: formenctype on button</a>
     */
    public static Attribute formenctype(FormEnctype enctype) { return attribute("formenctype", enctype); }

    /**
     * The {@code formmethod} attribute: variant to use for form submission.
     * <p>On {@code button}, {@code input}.
     * Value: "get"; "post"; "dialog".
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-control-infrastructure.html#attr-fs-formmethod">HTML: formmethod on button</a>
     */
    public static Attribute formmethod(FormMethod method) { return attribute("formmethod", method); }

    /**
     * The {@code formnovalidate} attribute: bypass form control validation for form submission.
     * <p>On {@code button}, {@code input}.
     * Value: Boolean attribute.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-control-infrastructure.html#attr-fs-formnovalidate">HTML: formnovalidate on button</a>
     */
    public static Attribute formnovalidate() { return flag("formnovalidate"); }

    /**
     * The {@code formtarget} attribute: navigable for form submission.
     * <p>On {@code button}, {@code input}.
     * Value: Valid navigable target name or keyword.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-control-infrastructure.html#attr-fs-formtarget">HTML: formtarget on button</a>
     */
    public static Attribute formtarget(LinkTarget target) { return attribute("formtarget", target); }

    /**
     * The {@code high} attribute: low limit of high range.
     * <p>On {@code meter}.
     * Value: Valid floating-point number.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-meter-high">HTML: high on meter</a>
     */
    public static Attribute high(String number) { return attribute("high", number); }

    /**
     * The {@code integrity} attribute: integrity metadata used in Subresource Integrity checks [SRI].
     * <p>On {@code link}, {@code script}.
     * Value: Text.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#attr-link-integrity">HTML: integrity on link</a>
     * @see <a href="https://html.spec.whatwg.org/dev/scripting.html#attr-script-integrity">HTML: integrity on script</a>
     */
    public static Attribute integrity(String hash) { return attribute("integrity", hash); }

    /**
     * The {@code kind} attribute: the type of text track.
     * <p>On {@code track}.
     * Value: "subtitles"; "captions"; "descriptions"; "chapters"; "metadata".
     *
     * @see <a href="https://html.spec.whatwg.org/dev/media.html#attr-track-kind">HTML: kind on track</a>
     */
    public static Attribute kind(TrackKind kind) { return attribute("kind", kind); }

    /**
     * The {@code list} attribute: list of autocomplete options.
     * <p>On {@code input}.
     * Value: ID.
     * <p>{@code id} of the {@code datalist} that suggests values to the input.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/input.html#attr-input-list">HTML: list on input</a>
     */
    public static Attribute list(String id) { return attribute("list", id); }

    /**
     * The {@code loading} attribute: used when determining loading deferral.
     * <p>On {@code iframe}, {@code img}, {@code audio}, {@code video}.
     * Value: "lazy"; "eager".
     *
     * @see <a href="https://html.spec.whatwg.org/dev/iframe-embed-object.html#attr-iframe-loading">HTML: loading on iframe</a>
     * @see <a href="https://html.spec.whatwg.org/dev/embedded-content.html#attr-img-loading">HTML: loading on img</a>
     * @see <a href="https://html.spec.whatwg.org/dev/media.html#attr-media-loading">HTML: loading on audio</a>
     */
    public static Attribute loading(Loading loading) { return attribute("loading", loading); }

    /**
     * The {@code loop} attribute: whether to loop the media resource.
     * <p>On {@code audio}, {@code video}.
     * Value: Boolean attribute.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/media.html#attr-media-loop">HTML: loop on audio</a>
     */
    public static Attribute loop() { return flag("loop"); }

    /**
     * The {@code low} attribute: high limit of low range.
     * <p>On {@code meter}.
     * Value: Valid floating-point number.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-meter-low">HTML: low on meter</a>
     */
    public static Attribute low(String number) { return attribute("low", number); }

    /**
     * The {@code max} attribute, on each element where it applies:
     * <ul>
     * <li>{@code input} — Maximum value. Value: Varies.</li>
     * <li>{@code meter}, {@code progress} — Upper bound of range. Value: Valid floating-point number.</li>
     * </ul>
     *
     * @see <a href="https://html.spec.whatwg.org/dev/input.html#attr-input-max">HTML: max on input</a>
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-meter-max">HTML: max on meter</a>
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-progress-max">HTML: max on progress</a>
     */
    public static Attribute max(String value) { return attribute("max", value); }

    /**
     * The {@code method} attribute: variant to use for form submission.
     * <p>On {@code form}.
     * Value: "get"; "post"; "dialog".
     * <p>Writes {@code method="dialog"}: submitting closes the {@code dialog} the form is in, with no request.
     * {@code method="dialog"}: submitting closes the enclosing {@code dialog}.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-control-infrastructure.html#attr-fs-method-dialog-keyword">HTML: the dialog method</a>
     * @see <a href="https://html.spec.whatwg.org/dev/form-control-infrastructure.html#attr-fs-method">HTML: method on form</a>
     */
    public static Attribute methodDialog() { return attribute("method", "dialog"); }

    /**
     * The {@code min} attribute, on each element where it applies:
     * <ul>
     * <li>{@code input} — Minimum value. Value: Varies.</li>
     * <li>{@code meter} — Lower bound of range. Value: Valid floating-point number.</li>
     * </ul>
     *
     * @see <a href="https://html.spec.whatwg.org/dev/input.html#attr-input-min">HTML: min on input</a>
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-meter-min">HTML: min on meter</a>
     */
    public static Attribute min(String value) { return attribute("min", value); }

    /**
     * The {@code minlength} attribute: minimum length of value.
     * <p>On {@code input}, {@code textarea}.
     * Value: Valid non-negative integer.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/input.html#attr-input-minlength">HTML: minlength on input</a>
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-textarea-minlength">HTML: minlength on textarea</a>
     */
    public static Attribute minlength(int length) { return attribute("minlength", length); }

    /**
     * The {@code muted} attribute: whether to mute the media resource by default.
     * <p>On {@code audio}, {@code video}.
     * Value: Boolean attribute.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/media.html#attr-media-muted">HTML: muted on audio</a>
     */
    public static Attribute muted() { return flag("muted"); }

    /**
     * The {@code novalidate} attribute: bypass form control validation for form submission.
     * <p>On {@code form}.
     * Value: Boolean attribute.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-control-infrastructure.html#attr-fs-novalidate">HTML: novalidate on form</a>
     */
    public static Attribute novalidate() { return flag("novalidate"); }

    /**
     * The {@code open} attribute, on each element where it applies:
     * <ul>
     * <li>{@code details} — Whether the details are visible. Value: Boolean attribute.</li>
     * <li>{@code dialog} — Whether the dialog box is showing. Value: Boolean attribute.</li>
     * </ul>
     *
     * @see <a href="https://html.spec.whatwg.org/dev/interactive-elements.html#attr-details-open">HTML: open on details</a>
     * @see <a href="https://html.spec.whatwg.org/dev/interactive-elements.html#attr-dialog-open">HTML: open on dialog</a>
     */
    public static Attribute open() { return flag("open"); }

    /**
     * The {@code optimum} attribute: optimum value in gauge.
     * <p>On {@code meter}.
     * Value: Valid floating-point number.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-meter-optimum">HTML: optimum on meter</a>
     */
    public static Attribute optimum(String number) { return attribute("optimum", number); }

    /**
     * The {@code pattern} attribute: pattern to be matched by the form control's value.
     * <p>On {@code input}.
     * Value: Regular expression matching the JavaScript Pattern production.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/input.html#attr-input-pattern">HTML: pattern on input</a>
     */
    public static Attribute pattern(String regex) { return attribute("pattern", regex); }

    /**
     * The {@code ping} attribute: uRLs to ping.
     * <p>On {@code a}, {@code area}.
     * Value: Set of space-separated tokens consisting of valid non-empty URLs.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/links.html#ping">HTML: ping on a</a>
     */
    public static Attribute ping(String urls) { return attribute("ping", urls); }

    /**
     * The {@code placeholder} attribute: user-visible label to be placed within the form control.
     * <p>On {@code input}, {@code textarea}.
     * Value: Text.
     * <p>Takes a {@code Text}: a person reads it, so it is never a free string.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/input.html#attr-input-placeholder">HTML: placeholder on input</a>
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-textarea-placeholder">HTML: placeholder on textarea</a>
     */
    public static Attribute placeholder(Text text) { return attribute("placeholder", text.value()); }

    /**
     * The {@code playsinline} attribute: encourage the user agent to display video content within the element's
     * playback area.
     * <p>On {@code video}.
     * Value: Boolean attribute.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/media.html#attr-video-playsinline">HTML: playsinline on video</a>
     */
    public static Attribute playsinline() { return flag("playsinline"); }

    /**
     * The {@code popovertarget} attribute: targets a popover element to toggle, show, or hide.
     * <p>On {@code button}, {@code input}.
     * Value: ID.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/popover.html#attr-popovertarget">HTML: popovertarget on button</a>
     */
    public static Attribute popovertarget(String id) { return attribute("popovertarget", id); }

    /**
     * The {@code poster} attribute: poster frame to show prior to video playback.
     * <p>On {@code video}.
     * Value: Valid non-empty URL potentially surrounded by spaces.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/media.html#attr-video-poster">HTML: poster on video</a>
     */
    public static Attribute poster(String url) { return attribute("poster", url); }

    /**
     * The {@code preload} attribute: hints how much buffering the media resource will likely need.
     * <p>On {@code audio}, {@code video}.
     * Value: "none"; "metadata"; "auto"; the empty string.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/media.html#attr-media-preload">HTML: preload on audio</a>
     */
    public static Attribute preload(Preload preload) { return attribute("preload", preload); }

    /**
     * The {@code referrerpolicy} attribute: referrer policy for fetches initiated by the element.
     * <p>On {@code a}, {@code area}, {@code iframe}, {@code img}, {@code link}, {@code script}.
     * Value: Referrer policy.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/links.html#attr-hyperlink-referrerpolicy">HTML: referrerpolicy on a</a>
     * @see <a href="https://html.spec.whatwg.org/dev/iframe-embed-object.html#attr-iframe-referrerpolicy">HTML: referrerpolicy on iframe</a>
     * @see <a href="https://html.spec.whatwg.org/dev/embedded-content.html#attr-img-referrerpolicy">HTML: referrerpolicy on img</a>
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#attr-link-referrerpolicy">HTML: referrerpolicy on link</a>
     * @see <a href="https://html.spec.whatwg.org/dev/scripting.html#attr-script-referrerpolicy">HTML: referrerpolicy on script</a>
     */
    public static Attribute referrerpolicy(ReferrerPolicy policy) { return attribute("referrerpolicy", policy); }

    /**
     * The {@code required} attribute: whether the control is required for form submission.
     * <p>On {@code input}, {@code select}, {@code textarea}.
     * Value: Boolean attribute.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/input.html#attr-input-required">HTML: required on input</a>
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-select-required">HTML: required on select</a>
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-textarea-required">HTML: required on textarea</a>
     */
    public static Attribute required() { return flag("required"); }

    /**
     * The {@code reversed} attribute: number the list backwards.
     * <p>On {@code ol}.
     * Value: Boolean attribute.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/grouping-content.html#attr-ol-reversed">HTML: reversed on ol</a>
     */
    public static Attribute reversed() { return flag("reversed"); }

    /**
     * The {@code sandbox} attribute: security rules for nested content.
     * <p>On {@code iframe}.
     * Value: Unordered set of unique space-separated tokens, ASCII case-insensitive, consisting of "allow-downloads";
     * "allow-forms"; "allow-modals"; "allow-orientation-lock"; "allow-pointer-lock"; "allow-popups";
     * "allow-popups-to-escape-sandbox"; "allow-presentation"; "allow-same-origin"; "allow-scripts";
     * "allow-top-navigation"; "allow-top-navigation-by-user-activation"; "allow-top-navigation-to-custom-protocols".
     *
     * @see <a href="https://html.spec.whatwg.org/dev/iframe-embed-object.html#attr-iframe-sandbox">HTML: sandbox on iframe</a>
     */
    public static Attribute sandbox(String tokens) { return attribute("sandbox", tokens); }

    /**
     * The {@code sizes} attribute, on each element where it applies:
     * <ul>
     * <li>{@code link} — Sizes of the icons (for rel="icon"). Value: Unordered set of unique space-separated tokens,
     * ASCII case-insensitive, consisting of sizes.</li>
     * <li>{@code img}, {@code source} — Image sizes for different page layouts. Value: Valid source size list.</li>
     * </ul>
     *
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#attr-link-sizes">HTML: sizes on link</a>
     * @see <a href="https://html.spec.whatwg.org/dev/embedded-content.html#attr-img-sizes">HTML: sizes on img</a>
     * @see <a href="https://html.spec.whatwg.org/dev/embedded-content.html#attr-source-sizes">HTML: sizes on source</a>
     */
    public static Attribute sizes(String sizes) { return attribute("sizes", sizes); }

    /**
     * The {@code srcdoc} attribute: a document to render in the iframe.
     * <p>On {@code iframe}.
     * Value: The source of an iframe srcdoc document.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/iframe-embed-object.html#attr-iframe-srcdoc">HTML: srcdoc on iframe</a>
     */
    public static Attribute srcdoc(String html) { return attribute("srcdoc", html); }

    /**
     * The {@code srclang} attribute: language of the text track.
     * <p>On {@code track}.
     * Value: Valid BCP 47 language tag.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/media.html#attr-track-srclang">HTML: srclang on track</a>
     */
    public static Attribute srclang(String bcp47) { return attribute("srclang", bcp47); }

    /**
     * The {@code srcset} attribute: images to use in different situations, e.g., high-resolution displays, small
     * monitors, etc.
     * <p>On {@code img}, {@code source}.
     * Value: Comma-separated list of image candidate strings.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/embedded-content.html#attr-img-srcset">HTML: srcset on img</a>
     * @see <a href="https://html.spec.whatwg.org/dev/embedded-content.html#attr-source-srcset">HTML: srcset on source</a>
     */
    public static Attribute srcset(String candidates) { return attribute("srcset", candidates); }

    /**
     * The {@code start} attribute: starting value of the list.
     * <p>On {@code ol}.
     * Value: Valid integer.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/grouping-content.html#attr-ol-start">HTML: start on ol</a>
     */
    public static Attribute start(int number) { return attribute("start", number); }

    /**
     * The {@code step} attribute: granularity to be matched by the form control's value.
     * <p>On {@code input}.
     * Value: Valid floating-point number greater than zero, or "any".
     *
     * @see <a href="https://html.spec.whatwg.org/dev/input.html#attr-input-step">HTML: step on input</a>
     */
    public static Attribute step(String step) { return attribute("step", step); }

    /**
     * The {@code target} attribute, on each element where it applies:
     * <ul>
     * <li>{@code a}, {@code area} — Navigable for hyperlink navigation. Value: Valid navigable target name or
     * keyword.</li>
     * <li>{@code base} — Default navigable for hyperlink navigation and form submission. Value: Valid navigable
     * target name or keyword.</li>
     * <li>{@code form} — Navigable for form submission. Value: Valid navigable target name or keyword.</li>
     * </ul>
     *
     * @see <a href="https://html.spec.whatwg.org/dev/links.html#attr-hyperlink-target">HTML: target on a</a>
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#attr-base-target">HTML: target on base</a>
     * @see <a href="https://html.spec.whatwg.org/dev/form-control-infrastructure.html#attr-fs-target">HTML: target on form</a>
     */
    public static Attribute target(LinkTarget target) { return attribute("target", target); }

    /**
     * The {@code target} attribute, on each element where it applies:
     * <ul>
     * <li>{@code a}, {@code area} — Navigable for hyperlink navigation. Value: Valid navigable target name or
     * keyword.</li>
     * <li>{@code base} — Default navigable for hyperlink navigation and form submission. Value: Valid navigable
     * target name or keyword.</li>
     * <li>{@code form} — Navigable for form submission. Value: Valid navigable target name or keyword.</li>
     * </ul>
     * <p>{@code target} naming a browsing context ({@code iframe} name, window name).
     *
     * @see <a href="https://html.spec.whatwg.org/dev/links.html#attr-hyperlink-target">HTML: target on a</a>
     * @see <a href="https://html.spec.whatwg.org/dev/semantics.html#attr-base-target">HTML: target on base</a>
     * @see <a href="https://html.spec.whatwg.org/dev/form-control-infrastructure.html#attr-fs-target">HTML: target on form</a>
     */
    public static Attribute target(String name) { return attribute("target", name); }

    /**
     * The {@code wrap} attribute: how the value of the form control is to be wrapped for form submission.
     * <p>On {@code textarea}.
     * Value: "soft"; "hard".
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#attr-textarea-wrap">HTML: wrap on textarea</a>
     */
    public static Attribute wrap(TextWrap wrap) { return attribute("wrap", wrap); }

    // ---------- elements: sections

    /**
     * The {@code article} element: self-contained syndicatable or reusable composition.
     * <p>Categories: flow; sectioning; palpable.
     * Parents: flow.
     * Children: flow.
     * Attributes: globals.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/sections.html#the-article-element">HTML: the article element</a>
     */
    public static Node.Element article(Content... c) { return element("article", c); }

    /**
     * The {@code aside} element: sidebar for tangentially related content.
     * <p>Categories: flow; sectioning; palpable.
     * Parents: flow.
     * Children: flow.
     * Attributes: globals.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/sections.html#the-aside-element">HTML: the aside element</a>
     */
    public static Node.Element aside(Content... c) { return element("aside", c); }

    /**
     * The {@code footer} element: footer for a page or section.
     * <p>Categories: flow; palpable.
     * Parents: flow.
     * Children: flow.
     * Attributes: globals.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/sections.html#the-footer-element">HTML: the footer element</a>
     */
    public static Node.Element footer(Content... c) { return element("footer", c); }

    /**
     * The {@code header} element: introductory or navigational aids for a page or section.
     * <p>Categories: flow; palpable.
     * Parents: flow.
     * Children: flow.
     * Attributes: globals.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/sections.html#the-header-element">HTML: the header element</a>
     */
    public static Node.Element header(Content... c) { return element("header", c); }

    /**
     * The {@code hgroup} element: heading container.
     * <p>Categories: flow; palpable.
     * Parents: legend; summary; flow.
     * Children: h1; h2; h3; h4; h5; h6; p; script-supporting elements.
     * Attributes: globals.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/sections.html#the-hgroup-element">HTML: the hgroup element</a>
     */
    public static Node.Element hgroup(Content... c) { return element("hgroup", c); }

    /**
     * The {@code main} element: container for the dominant contents of the document.
     * <p>Categories: flow; palpable.
     * Parents: flow.
     * Children: flow.
     * Attributes: globals.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/grouping-content.html#the-main-element">HTML: the main element</a>
     */
    public static Node.Element main(Content... c) { return element("main", c); }

    /**
     * The {@code nav} element: section with navigational links.
     * <p>Categories: flow; sectioning; palpable.
     * Parents: flow.
     * Children: flow.
     * Attributes: globals.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/sections.html#the-nav-element">HTML: the nav element</a>
     */
    public static Node.Element nav(Content... c) { return element("nav", c); }

    /**
     * The {@code search} element: container for search controls.
     * <p>Categories: flow; palpable.
     * Parents: flow.
     * Children: flow.
     * Attributes: globals.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/grouping-content.html#the-search-element">HTML: the search element</a>
     */
    public static Node.Element search(Content... c) { return element("search", c); }

    /**
     * The {@code section} element: generic document or application section.
     * <p>Categories: flow; sectioning; palpable.
     * Parents: flow.
     * Children: flow.
     * Attributes: globals.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/sections.html#the-section-element">HTML: the section element</a>
     */
    public static Node.Element section(Content... c) { return element("section", c); }

    // ---------- elements: grouping and text-level semantics

    /**
     * The {@code figcaption} element: caption for figure.
     * <p>Categories: none.
     * Parents: figure.
     * Children: flow.
     * Attributes: globals.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/grouping-content.html#the-figcaption-element">HTML: the figcaption element</a>
     */
    public static Node.Element figcaption(Content... c) { return element("figcaption", c); }

    /**
     * The {@code figure} element: figure with optional caption.
     * <p>Categories: flow; palpable.
     * Parents: flow.
     * Children: figcaption; flow.
     * Attributes: globals.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/grouping-content.html#the-figure-element">HTML: the figure element</a>
     */
    public static Node.Element figure(Content... c) { return element("figure", c); }

    /**
     * The {@code menu} element: menu of commands.
     * <p>Categories: flow; palpable.
     * Parents: flow.
     * Children: li; script-supporting elements.
     * Attributes: globals.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/grouping-content.html#the-menu-element">HTML: the menu element</a>
     */
    public static Node.Element menu(Content... c) { return element("menu", c); }

    /**
     * The {@code bdi} element: text directionality isolation.
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-bdi-element">HTML: the bdi element</a>
     */
    public static Node.Element bdi(Content... c) { return element("bdi", c); }

    /**
     * The {@code data} element: machine-readable equivalent.
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals; value.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-data-element">HTML: the data element</a>
     */
    public static Node.Element data(Content... c) { return element("data", c); }

    /**
     * The {@code mark} element: highlight.
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-mark-element">HTML: the mark element</a>
     */
    public static Node.Element mark(Content... c) { return element("mark", c); }

    /**
     * The {@code rp} element: parenthesis for ruby annotation text.
     * <p>Categories: none.
     * Parents: ruby.
     * Children: text.
     * Attributes: globals.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-rp-element">HTML: the rp element</a>
     */
    public static Node.Element rp(Content... c) { return element("rp", c); }

    /**
     * The {@code rt} element: ruby annotation text.
     * <p>Categories: none.
     * Parents: ruby.
     * Children: phrasing.
     * Attributes: globals.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-rt-element">HTML: the rt element</a>
     */
    public static Node.Element rt(Content... c) { return element("rt", c); }

    /**
     * The {@code ruby} element: ruby annotation(s).
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: phrasing; rt; rp.
     * Attributes: globals.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-ruby-element">HTML: the ruby element</a>
     */
    public static Node.Element ruby(Content... c) { return element("ruby", c); }

    /**
     * The {@code s} element: inaccurate text.
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-s-element">HTML: the s element</a>
     */
    public static Node.Element s(Content... c) { return element("s", c); }

    /**
     * The {@code time} element: machine-readable equivalent of date- or time-related data.
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals; datetime.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-time-element">HTML: the time element</a>
     */
    public static Node.Element time(Content... c) { return element("time", c); }

    /**
     * The {@code u} element: unarticulated annotation.
     * <p>Categories: flow; phrasing; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-u-element">HTML: the u element</a>
     */
    public static Node.Element u(Content... c) { return element("u", c); }

    /**
     * The {@code wbr} element: line breaking opportunity.
     * <p>Categories: flow; phrasing.
     * Parents: phrasing.
     * Children: empty.
     * Attributes: globals.
     * Void element: no children and no end tag.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/text-level-semantics.html#the-wbr-element">HTML: the wbr element</a>
     */
    public static Node.Element wbr(Content... c) { return element("wbr", c); }

    // ---------- elements: media and embedded content

    /**
     * The {@code audio} element: audio player.
     * <p>Categories: flow; phrasing; embedded; interactive; palpable.
     * Parents: phrasing.
     * Children: source; track; transparent.
     * Attributes: globals; src; crossorigin; preload; autoplay; loading; loop; muted; controls.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/media.html#the-audio-element">HTML: the audio element</a>
     */
    public static Node.Element audio(Content... c) { return element("audio", c); }

    /**
     * The {@code video} element: video player.
     * <p>Categories: flow; phrasing; embedded; interactive; palpable.
     * Parents: phrasing.
     * Children: source; track; transparent.
     * Attributes: globals; src; crossorigin; poster; preload; autoplay; playsinline; loading; loop; muted; controls;
     * width; height.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/media.html#the-video-element">HTML: the video element</a>
     */
    public static Node.Element video(Content... c) { return element("video", c); }

    /**
     * The {@code track} element: timed text track.
     * <p>Categories: none.
     * Parents: audio; video.
     * Children: empty.
     * Attributes: globals; default; kind; label; src; srclang.
     * Void element: no children and no end tag.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/media.html#the-track-element">HTML: the track element</a>
     */
    public static Node.Element track(Content... c) { return element("track", c); }

    /**
     * The {@code source} element: image source for img or media source for video or audio.
     * <p>Categories: none.
     * Parents: picture; video; audio.
     * Children: empty.
     * Attributes: globals; type; media; src; srcset; sizes; width; height.
     * Void element: no children and no end tag.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/embedded-content.html#the-source-element">HTML: the source element</a>
     */
    public static Node.Element source(Content... c) { return element("source", c); }

    /**
     * The {@code picture} element: image.
     * <p>Categories: flow; phrasing; embedded; palpable.
     * Parents: phrasing.
     * Children: source; one img; script-supporting elements.
     * Attributes: globals.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/embedded-content.html#the-picture-element">HTML: the picture element</a>
     */
    public static Node.Element picture(Content... c) { return element("picture", c); }

    /**
     * The {@code embed} element: plugin.
     * <p>Categories: flow; phrasing; embedded; interactive; palpable.
     * Parents: phrasing.
     * Children: empty.
     * Attributes: globals; src; type; width; height; any.
     * Void element: no children and no end tag.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/iframe-embed-object.html#the-embed-element">HTML: the embed element</a>
     */
    public static Node.Element embed(Content... c) { return element("embed", c); }

    /**
     * The {@code iframe} element: child navigable.
     * <p>Categories: flow; phrasing; embedded; interactive; palpable.
     * Parents: phrasing.
     * Children: empty.
     * Attributes: globals; src; srcdoc; name; sandbox; allow; allowfullscreen; width; height; referrerpolicy;
     * loading.
     * Void element: no children and no end tag.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/iframe-embed-object.html#the-iframe-element">HTML: the iframe element</a>
     */
    public static Node.Element iframe(Content... c) { return element("iframe", c); }

    /**
     * The {@code canvas} element: scriptable bitmap canvas.
     * <p>Categories: flow; phrasing; embedded; palpable.
     * Parents: phrasing.
     * Children: transparent.
     * Attributes: globals; width; height.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/canvas.html#the-canvas-element">HTML: the canvas element</a>
     */
    public static Node.Element canvas(Content... c) { return element("canvas", c); }

    /**
     * The {@code svg} element: an SVG image inline in the page, in the SVG namespace (the HTML parser switches to
     * it). Children are SVG elements, built here with {@code element(…)}.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/embedded-content-other.html#svg-0">HTML: SVG</a>
     */
    public static Node.Element svg(Content... c) { return element("svg", c); }

    /**
     * The {@code math} element: MathML inline in the page, in the MathML namespace (the HTML parser switches to it).
     * Children are MathML elements, built here with {@code element(…)}.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/embedded-content-other.html#mathml">HTML: MathML</a>
     */
    public static Node.Element math(Content... c) { return element("math", c); }

    // ---------- elements: forms, interactive, web components

    /**
     * The {@code datalist} element: container for options for combo box control.
     * <p>Categories: flow; phrasing.
     * Parents: phrasing.
     * Children: phrasing; option; script-supporting elements.
     * Attributes: globals.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#the-datalist-element">HTML: the datalist element</a>
     */
    public static Node.Element datalist(Content... c) { return element("datalist", c); }

    /**
     * The {@code meter} element: gauge.
     * <p>Categories: flow; phrasing; labelable; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals; value; min; max; low; high; optimum.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#the-meter-element">HTML: the meter element</a>
     */
    public static Node.Element meter(Content... c) { return element("meter", c); }

    /**
     * The {@code output} element: calculated output value.
     * <p>Categories: flow; phrasing; listed; labelable; resettable; form-associated; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals; for; form; name.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#the-output-element">HTML: the output element</a>
     */
    public static Node.Element output(Content... c) { return element("output", c); }

    /**
     * The {@code progress} element: progress bar.
     * <p>Categories: flow; phrasing; labelable; palpable.
     * Parents: phrasing.
     * Children: phrasing.
     * Attributes: globals; value; max.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#the-progress-element">HTML: the progress element</a>
     */
    public static Node.Element progress(Content... c) { return element("progress", c); }

    /**
     * The {@code selectedcontent} element: mirrors content from an option.
     * <p>Categories: none.
     * Parents: button.
     * Children: empty.
     * Attributes: globals.
     * Void element: no children and no end tag.
     * <p>Experimental (MDN): mirrors the selected {@code option} inside a customizable {@code select}.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/form-elements.html#the-selectedcontent-element">HTML: the selectedcontent element</a>
     */
    public static Node.Element selectedcontent(Content... c) { return element("selectedcontent", c); }

    /**
     * The {@code details} element: disclosure control for hiding details.
     * <p>Categories: flow; interactive; palpable.
     * Parents: flow.
     * Children: summary; flow.
     * Attributes: globals; name; open.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/interactive-elements.html#the-details-element">HTML: the details element</a>
     */
    public static Node.Element details(Content... c) { return element("details", c); }

    /**
     * The {@code summary} element: caption for details.
     * <p>Categories: none.
     * Parents: details.
     * Children: phrasing; heading content.
     * Attributes: globals.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/interactive-elements.html#the-summary-element">HTML: the summary element</a>
     */
    public static Node.Element summary(Content... c) { return element("summary", c); }

    /**
     * The {@code dialog} element: dialog box or window.
     * <p>Categories: flow.
     * Parents: flow.
     * Children: flow.
     * Attributes: globals; open.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/interactive-elements.html#the-dialog-element">HTML: the dialog element</a>
     */
    public static Node.Element dialog(Content... c) { return element("dialog", c); }

    /**
     * The {@code geolocation} element (experimental, not yet in the HTML standard): a button the person presses to
     * share their location with the page, instead of a permission prompt opened by script.
     * <p>Experimental (MDN): a control to share the user's location.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Elements/geolocation">MDN: &lt;geolocation&gt;</a>
     */
    public static Node.Element geolocation(Content... c) { return element("geolocation", c); }

    /**
     * The {@code slot} element: shadow tree slot.
     * <p>Categories: flow; phrasing.
     * Parents: phrasing.
     * Children: transparent.
     * Attributes: globals; name.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/scripting.html#the-slot-element">HTML: the slot element</a>
     */
    public static Node.Element slot(Content... c) { return element("slot", c); }

    /**
     * The {@code template} element: template.
     * <p>Categories: metadata; flow; phrasing; script-supporting.
     * Parents: metadata; phrasing; script-supporting; colgroup.
     * Children: empty.
     * Attributes: globals; for; shadowrootmode; shadowrootdelegatesfocus; shadowrootserializable;
     * shadowrootslotassignment; shadowrootclonable; shadowrootcustomelementregistry.
     * Void element: no children and no end tag.
     *
     * @see <a href="https://html.spec.whatwg.org/dev/scripting.html#the-template-element">HTML: the template element</a>
     */
    public static Node.Element template(Content... c) { return element("template", c); }
}
