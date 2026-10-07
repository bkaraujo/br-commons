package br.commons.lang;

import br.commons.lang.html.Html;
import br.commons.lang.html.core.Attribute;
import br.commons.lang.html.core.Content;
import br.commons.lang.html.core.Keyword;
import br.commons.lang.html.core.Node;
import br.commons.lang.htmx.HtmxRequest;
import br.commons.lang.htmx.HxSwap;
import br.commons.lang.htmx.HxSync;
import br.commons.lang.htmx.SwapSpec;
import br.commons.locale.i18n.Text;
import org.jspecify.annotations.NullMarked;

import java.util.Map;

/**
 * Attributes of htmx (https://htmx.org/reference/), as a static DSL to use beside the HTML one. Names keep the
 * {@code hx} prefix of the attribute ({@code hx-get} → {@link #hxGet}), so a view imports both without clashes:
 *
 * <pre>{@code
 * import static br.commons.lang.Html5.*;
 * import static br.commons.lang.Htmx.*;
 *
 * button(hxPost("/likes"), hxTarget("#likes"), hxSwap(HxSwap.OUTER_HTML), hxConfirm(msg.sure()), text(msg.like()))
 * }</pre>
 *
 * Texts htmx shows to a person ({@code hx-confirm}, {@code hx-prompt}) take a {@link Text}. {@code hx-vars} is
 * left out: deprecated in favor of {@code hx-vals}.
 */
@NullMarked
public final class Htmx {

    private Htmx() {}

    // ---------- requests

    /**
     * Issues a {@code GET} to the URL when the element is triggered and swaps the response into the target. Default
     * trigger: {@code click}; {@code change} on {@code input}, {@code select}, {@code textarea}; {@code submit} on
     * {@code form}. Default target: the element itself, {@code innerHTML}. The parameters go in the query string.
     *
     * <p>Parameters: an element sends its own value when it has a {@code name}; a {@code form} sends all its inputs;
     * a non-GET request also sends the inputs of the closest enclosing form. Add more with {@code hx-include},
     * {@code hx-vals}; filter with {@code hx-params}.
     *
     * @see <a href="https://htmx.org/attributes/hx-get/">htmx: hx-get</a>
     */
    public static Attribute hxGet(String url) { return attribute("hx-get", url); }

    /**
     * Issues a {@code POST} to the URL when the element is triggered and swaps the response into the target. The
     * parameters go in the body, {@code application/x-www-form-urlencoded} ({@code multipart/form-data} with
     * {@code hx-encoding}). <p>Parameters: an element sends its own value when it has a {@code name}; a {@code form}
     * sends all its inputs; a non-GET request also sends the inputs of the closest enclosing form. Add more with
     * {@code hx-include}, {@code hx-vals}; filter with {@code hx-params}.
     *
     * @see <a href="https://htmx.org/attributes/hx-post/">htmx: hx-post</a>
     */
    public static Attribute hxPost(String url) { return attribute("hx-post", url); }

    /**
     * Issues a {@code PUT} to the URL when the element is triggered and swaps the response into the target; the
     * parameters go in the body, as for {@code hx-post}. <p>Parameters: an element sends its own value when it has a
     * {@code name}; a {@code form} sends all its inputs; a non-GET request also sends the inputs of the closest
     * enclosing form. Add more with {@code hx-include}, {@code hx-vals}; filter with {@code hx-params}.
     *
     * @see <a href="https://htmx.org/attributes/hx-put/">htmx: hx-put</a>
     */
    public static Attribute hxPut(String url) { return attribute("hx-put", url); }

    /**
     * Issues a {@code PATCH} to the URL when the element is triggered and swaps the response into the target; the
     * parameters go in the body, as for {@code hx-post}. <p>Parameters: an element sends its own value when it has a
     * {@code name}; a {@code form} sends all its inputs; a non-GET request also sends the inputs of the closest
     * enclosing form. Add more with {@code hx-include}, {@code hx-vals}; filter with {@code hx-params}.
     *
     * @see <a href="https://htmx.org/attributes/hx-patch/">htmx: hx-patch</a>
     */
    public static Attribute hxPatch(String url) { return attribute("hx-patch", url); }

    /**
     * Issues a {@code DELETE} to the URL when the element is triggered and swaps the response into the target. In
     * htmx 2 the parameters of a {@code DELETE} go in the query string ({@code htmx.config.methodsThatUseUrlParams}).
     * An empty {@code 200} removes the target with {@code outerHTML}; a {@code 204} swaps nothing.
     *
     * @see <a href="https://htmx.org/attributes/hx-delete/">htmx: hx-delete</a>
     */
    public static Attribute hxDelete(String url) { return attribute("hx-delete", url); }

    // ---------- what triggers, what is sent

    /**
     * What triggers the request: an event name, with an optional filter in brackets ({@code click[ctrlKey]}) and
     * modifiers — {@code once}, {@code changed}, {@code delay:<time>}, {@code throttle:<time>},
     * {@code from:<selector>}, {@code target:<selector>}, {@code consume}, {@code queue:<first|last|all|none>}.
     * Special events: {@code load}, {@code revealed}, {@code intersect} (with {@code root}, {@code threshold}), and
     * polling with {@code every <time>}. Several triggers are separated by commas. Not inherited.
     *
     * @see <a href="https://htmx.org/attributes/hx-trigger/">htmx: hx-trigger</a>
     */
    public static Attribute hxTrigger(String trigger) { return attribute("hx-trigger", trigger); }

    /**
     * Values added to the parameters of the request, as a JSON object. Inherited, and a child's value for a key
     * overrides its parent's. This method writes the map with the serializer of the library
     * ({@code br.commons.lang.Json}); the {@code js:} form, which evaluates script, is not offered.
     *
     * @see <a href="https://htmx.org/attributes/hx-vals/">htmx: hx-vals</a>
     */
    public static Attribute hxVals(Map<String, ?> values) { return attribute("hx-vals", Json.write(values)); }

    /**
     * Headers added to the request, as a JSON object. Inherited, and a child's header overrides its parent's. Written
     * with {@code br.commons.lang.Json}.
     *
     * @see <a href="https://htmx.org/attributes/hx-headers/">htmx: hx-headers</a>
     */
    public static Attribute hxHeaders(Map<String, String> headers) { return attribute("hx-headers", Json.write(headers)); }

    /**
     * Other elements whose values are sent with the request: a CSS selector, or an extended one — {@code this},
     * {@code closest <selector>}, {@code find <selector>}, {@code next <selector>}, {@code previous <selector>}.
     * Inherited.
     *
     * @see <a href="https://htmx.org/attributes/hx-include/">htmx: hx-include</a>
     */
    public static Attribute hxInclude(String selector) { return attribute("hx-include", selector); }

    /**
     * {@code hx-params="*"}: sends every parameter (the default). Inherited.
     *
     * @see <a href="https://htmx.org/attributes/hx-params/">htmx: hx-params</a>
     */
    public static Attribute hxParamsAll() { return attribute("hx-params", "*"); }

    /**
     * {@code hx-params="none"}: sends no parameter. Inherited.
     *
     * @see <a href="https://htmx.org/attributes/hx-params/">htmx: hx-params</a>
     */
    public static Attribute hxParamsNone() { return attribute("hx-params", "none"); }

    /**
     * {@code hx-params="a,b"}: sends only the named parameters. Inherited.
     *
     * @see <a href="https://htmx.org/attributes/hx-params/">htmx: hx-params</a>
     */
    public static Attribute hxParams(String... names) { return attribute("hx-params", String.join(",", names)); }

    /**
     * {@code hx-params="not a,b"}: sends every parameter but the named ones. Inherited.
     *
     * @see <a href="https://htmx.org/attributes/hx-params/">htmx: hx-params</a>
     */
    public static Attribute hxParamsExcept(String... names) {
        return attribute("hx-params", "not " + String.join(",", names));
    }

    /**
     * {@code hx-encoding="multipart/form-data"}, the only value the attribute takes: the body goes as
     * {@code multipart/form-data}, required to upload files. Inherited.
     *
     * @see <a href="https://htmx.org/attributes/hx-encoding/">htmx: hx-encoding</a>
     */
    public static Attribute hxEncodingMultipart() { return attribute("hx-encoding", "multipart/form-data"); }

    /**
     * Configures the request: {@code timeout:<ms>}, {@code credentials:true} (send cookies cross-origin),
     * {@code noHeaders:true} (omit the {@code HX-*} headers), comma-separated or as JSON. Inherited.
     *
     * @see <a href="https://htmx.org/attributes/hx-request/">htmx: hx-request</a>
     */
    public static Attribute hxRequest(String config) { return attribute("hx-request", config); }

    /**
     * {@code hx-validate="true"}: checks the element with the HTML constraint validation API before the request,
     * which is cancelled when invalid. Forms are validated anyway; this is for other elements. Not inherited.
     *
     * @see <a href="https://htmx.org/attributes/hx-validate/">htmx: hx-validate</a>
     */
    public static Attribute hxValidate() { return attribute("hx-validate", "true"); }

    // ---------- where the response goes

    /**
     * The element that receives the response: a CSS selector, or an extended one — {@code this},
     * {@code closest <selector>}, {@code find <selector>}, {@code next}, {@code next <selector>}, {@code previous},
     * {@code previous <selector>}. Default: the element that issued the request. Inherited.
     *
     * @see <a href="https://htmx.org/attributes/hx-target/">htmx: hx-target</a>
     */
    public static Attribute hxTarget(String selector) { return attribute("hx-target", selector); }

    /**
     * How the response goes into the target ({@link HxSwap}). Default: {@code innerHTML}
     * ({@code htmx.config.defaultSwapStyle}). Inherited.
     *
     * @see <a href="https://htmx.org/attributes/hx-swap/">htmx: hx-swap</a>
     */
    public static Attribute hxSwap(HxSwap style) { return attribute("hx-swap", style); }

    /**
     * How the response goes into the target, with modifiers ({@link SwapSpec}): {@code transition}, {@code swap} and
     * {@code settle} delays, {@code ignoreTitle}, {@code scroll}, {@code show}, {@code focus-scroll}. Inherited.
     *
     * @see <a href="https://htmx.org/attributes/hx-swap/">htmx: hx-swap</a>
     */
    public static Attribute hxSwap(SwapSpec spec) { return attribute("hx-swap", spec.token()); }

    /**
     * The part of the response to swap, by CSS selector; the rest is dropped. Inherited.
     *
     * @see <a href="https://htmx.org/attributes/hx-select/">htmx: hx-select</a>
     */
    public static Attribute hxSelect(String selector) { return attribute("hx-select", selector); }

    /**
     * Parts of the response swapped out of band, into the elements with the same {@code id}: a comma-separated list
     * of selectors, each optionally with a swap style ({@code #alert, #count:afterbegin}). Inherited.
     *
     * @see <a href="https://htmx.org/attributes/hx-select-oob/">htmx: hx-select-oob</a>
     */
    public static Attribute hxSelectOob(String selectors) { return attribute("hx-select-oob", selectors); }

    /**
     * On an element of a response: {@code hx-swap-oob="true"} swaps it out of band, replacing ({@code outerHTML}) the
     * element of the page with the same {@code id}, besides the main swap. The element needs an {@code id}.
     *
     * @see <a href="https://htmx.org/attributes/hx-swap-oob/">htmx: hx-swap-oob</a>
     */
    public static Attribute hxSwapOob() { return attribute("hx-swap-oob", "true"); }

    /**
     * On an element of a response: swaps it out of band with the style, into the element of the page with the same
     * {@code id}; with a style other than {@code outerHTML}, the element's content is what goes in.
     *
     * @see <a href="https://htmx.org/attributes/hx-swap-oob/">htmx: hx-swap-oob</a>
     */
    public static Attribute hxSwapOob(HxSwap style) { return attribute("hx-swap-oob", style); }

    /**
     * On an element of a response: swaps it out of band with the style into the elements matching the selector
     * ({@code beforeend:#log}), which then need no matching {@code id}.
     *
     * @see <a href="https://htmx.org/attributes/hx-swap-oob/">htmx: hx-swap-oob</a>
     */
    public static Attribute hxSwapOob(HxSwap style, String selector) {
        return attribute("hx-swap-oob", style.token() + ":" + selector);
    }

    /**
     * Keeps the element unchanged across swaps (a video playing, a focused widget): the element with the same
     * {@code id} in the response is ignored. Needs a stable {@code id}.
     *
     * @see <a href="https://htmx.org/attributes/hx-preserve/">htmx: hx-preserve</a>
     */
    public static Attribute hxPreserve() { return flag("hx-preserve"); }

    // ---------- history

    /**
     * {@code true} pushes the URL of the request into the browser history (with a snapshot for the back button);
     * {@code false} disables it where an ancestor enabled it. Inherited.
     *
     * @see <a href="https://htmx.org/attributes/hx-push-url/">htmx: hx-push-url</a>
     */
    public static Attribute hxPushUrl(boolean push) { return attribute("hx-push-url", String.valueOf(push)); }

    /**
     * Pushes this URL, not the request's, into the browser history. Inherited.
     *
     * @see <a href="https://htmx.org/attributes/hx-push-url/">htmx: hx-push-url</a>
     */
    public static Attribute hxPushUrl(String url) { return attribute("hx-push-url", url); }

    /**
     * {@code true} replaces the URL in the location bar with the request's, without a new history entry
     * ({@code history.replaceState}); {@code false} disables it. Inherited.
     *
     * @see <a href="https://htmx.org/attributes/hx-replace-url/">htmx: hx-replace-url</a>
     */
    public static Attribute hxReplaceUrl(boolean replace) { return attribute("hx-replace-url", String.valueOf(replace)); }

    /**
     * Replaces the URL in the location bar with this one, without a new history entry. Inherited.
     *
     * @see <a href="https://htmx.org/attributes/hx-replace-url/">htmx: hx-replace-url</a>
     */
    public static Attribute hxReplaceUrl(String url) { return attribute("hx-replace-url", url); }

    /**
     * {@code hx-history="false"}: keeps this page out of the history cache ({@code localStorage}); going back to it
     * fetches it from the server. For pages with sensitive data.
     *
     * @see <a href="https://htmx.org/attributes/hx-history/">htmx: hx-history</a>
     */
    public static Attribute hxHistoryOff() { return attribute("hx-history", "false"); }

    /**
     * The element snapshotted and restored by the history, instead of {@code body}. Must be on every page, since
     * history restoration swaps into it.
     *
     * @see <a href="https://htmx.org/attributes/hx-history-elt/">htmx: hx-history-elt</a>
     */
    public static Attribute hxHistoryElt() { return flag("hx-history-elt"); }

    // ---------- user interaction and feedback

    /**
     * Asks the person to confirm with {@code window.confirm()} before the request; refusing cancels it. The
     * {@code htmx:confirm} event allows a custom dialog. Inherited.
     *
     * @see <a href="https://htmx.org/attributes/hx-confirm/">htmx: hx-confirm</a>
     */
    public static Attribute hxConfirm(Text question) { return attribute("hx-confirm", question.value()); }

    /**
     * Asks the person for a value with {@code window.prompt()} before the request; it goes in the {@code HX-Prompt}
     * header ({@link HtmxRequest#prompt()}). Cancelling cancels the request. Inherited.
     *
     * @see <a href="https://htmx.org/attributes/hx-prompt/">htmx: hx-prompt</a>
     */
    public static Attribute hxPrompt(Text question) { return attribute("hx-prompt", question.value()); }

    /**
     * The elements that get the {@code htmx-request} class while the request is in flight (default: the element
     * itself); style them with the {@code htmx-indicator} class. A CSS selector or {@code closest <selector>}.
     * Inherited.
     *
     * @see <a href="https://htmx.org/attributes/hx-indicator/">htmx: hx-indicator</a>
     */
    public static Attribute hxIndicator(String selector) { return attribute("hx-indicator", selector); }

    /**
     * Elements that get {@code disabled} while the request is in flight (avoid double submits): a CSS selector or an
     * extended one — {@code this}, {@code closest}, {@code find}, {@code next}, {@code previous} — comma-separated.
     * Inherited.
     *
     * @see <a href="https://htmx.org/attributes/hx-disabled-elt/">htmx: hx-disabled-elt</a>
     */
    public static Attribute hxDisabledElt(String selector) { return attribute("hx-disabled-elt", selector); }

    /**
     * {@code true}: links and forms inside become htmx requests that swap the {@code body} and push the URL, keeping
     * the page alive; without JavaScript they still work as plain links and forms (progressive enhancement). A
     * boosted request sends {@code HX-Boosted}. Inherited.
     *
     * @see <a href="https://htmx.org/attributes/hx-boost/">htmx: hx-boost</a>
     */
    public static Attribute hxBoost(boolean boost) { return attribute("hx-boost", String.valueOf(boost)); }

    /**
     * Synchronizes the requests of this element with those of the element matching the selector, with a strategy
     * ({@link HxSync}): {@code drop} (the default), {@code abort}, {@code replace}, {@code queue first|last|all}.
     * Inherited.
     *
     * @see <a href="https://htmx.org/attributes/hx-sync/">htmx: hx-sync</a>
     */
    public static Attribute hxSync(String selector, HxSync strategy) {
        return attribute("hx-sync", selector + ":" + strategy.token());
    }

    // ---------- events, extensions, processing

    /**
     * An inline handler of a DOM event: {@code hx-on:<event>="script"}, where {@code this} is the element and
     * {@code event} the event. Event names in kebab-case: attributes do not keep case. Inline script needs a Content
     * Security Policy that allows it.
     *
     * @see <a href="https://htmx.org/attributes/hx-on/">htmx: hx-on</a>
     */
    public static Attribute hxOn(String event, String script) { return attribute("hx-on:" + event, script); }

    /**
     * An inline handler of an htmx event: {@code hx-on::<event>}, short for {@code hx-on:htmx:<event>}
     * ({@code after-request}, {@code before-swap}…), in kebab-case. Inline script needs a Content Security Policy
     * that allows it.
     *
     * @see <a href="https://htmx.org/attributes/hx-on/">htmx: hx-on</a>
     */
    public static Attribute hxOnHtmx(String event, String script) { return attribute("hx-on::" + event, script); }

    /**
     * Enables htmx extensions on the element and its children, by name, comma-separated; {@code ignore:<name>}
     * disables one enabled by an ancestor. Inherited.
     *
     * @see <a href="https://htmx.org/attributes/hx-ext/">htmx: hx-ext</a>
     */
    public static Attribute hxExt(String extensions) { return attribute("hx-ext", extensions); }

    /**
     * Disables htmx processing of the element and everything inside, which children cannot undo; for content that
     * must not trigger requests, such as text written by users.
     *
     * @see <a href="https://htmx.org/attributes/hx-disable/">htmx: hx-disable</a>
     */
    public static Attribute hxDisable() { return flag("hx-disable"); }

    /**
     * Attributes the children do not inherit from this element: {@code *} or a space-separated list.
     *
     * @see <a href="https://htmx.org/attributes/hx-disinherit/">htmx: hx-disinherit</a>
     */
    public static Attribute hxDisinherit(String attributes) { return attribute("hx-disinherit", attributes); }

    /**
     * With inheritance off ({@code htmx.config.disableInheritance}), the attributes the children do inherit:
     * {@code *} or a space-separated list.
     *
     * @see <a href="https://htmx.org/attributes/hx-inherit/">htmx: hx-inherit</a>
     */
    public static Attribute hxInherit(String attributes) { return attribute("hx-inherit", attributes); }

    // ---------- elements

    /**
     * {@code <hx-partial>}: a swap command in the response body, not a DOM element. Its content goes into its
     * {@code hx-target} (or into the element with its {@code id}) with its {@code hx-swap}; several partials update
     * several places from one response. Where custom tags are a problem, {@code <template hx type="partial">} is the
     * fallback form.
     *
     * @see <a href="https://htmx.org/attributes/hx-partial/">htmx: hx-partial</a>
     */
    public static Node.Element hxPartial(Content... content) { return Html.element("hx-partial", content); }

    private static Attribute attribute(String name, String value) {
        return new Attribute(name, value);
    }

    private static Attribute attribute(String name, Keyword value) {
        return new Attribute(name, value.token());
    }

    private static Attribute flag(String name) {
        return new Attribute(name, null);
    }
}
