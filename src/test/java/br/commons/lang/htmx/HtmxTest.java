package br.commons.lang.htmx;

import br.commons.locale.i18n.Text;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static br.commons.lang.Html5.*;
import static br.commons.lang.Htmx.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The attributes of the htmx reference, used beside the HTML 5 DSL with both static imports. */
class HtmxTest {

    @Test
    void requests() {
        assertEquals("""
                <div hx-get="/g" hx-post="/p" hx-put="/u" hx-patch="/a" hx-delete="/d"></div>""",
                div(hxGet("/g"), hxPost("/p"), hxPut("/u"), hxPatch("/a"), hxDelete("/d")).render());
    }

    @Test
    void triggersAndWhatIsSent() {
        Map<String, Object> vals = new LinkedHashMap<>();
        vals.put("id", 7);
        vals.put("q", "a\"b");
        assertEquals("""
                <input hx-trigger="keyup changed delay:500ms" hx-vals="{&quot;id&quot;:7,&quot;q&quot;:&quot;a\\&quot;b&quot;}" \
                hx-headers="{&quot;X-A&quot;:&quot;1&quot;}" hx-include="closest form" hx-encoding="multipart/form-data" \
                hx-request="timeout:3000" hx-validate="true">""",
                input(hxTrigger("keyup changed delay:500ms"), hxVals(vals), hxHeaders(Map.of("X-A", "1")),
                        hxInclude("closest form"), hxEncodingMultipart(), hxRequest("timeout:3000"), hxValidate())
                        .render());
        assertEquals("""
                <a hx-params="*"></a><a hx-params="none"></a><a hx-params="a,b"></a><a hx-params="not a,b"></a>""",
                fragment(a(hxParamsAll()), a(hxParamsNone()), a(hxParams("a", "b")), a(hxParamsExcept("a", "b")))
                        .render());
    }

    @Test
    void whereTheResponseGoes() {
        assertEquals("""
                <div hx-target="#list" hx-swap="beforeend" hx-select="#rows" hx-select-oob="#alert" hx-preserve></div>""",
                div(hxTarget("#list"), hxSwap(HxSwap.BEFORE_END), hxSelect("#rows"), hxSelectOob("#alert"),
                        hxPreserve()).render());
        assertEquals("""
                <p hx-swap-oob="true"></p><p hx-swap-oob="innerHTML"></p><p hx-swap-oob="afterend:#x"></p>""",
                fragment(p(hxSwapOob()), p(hxSwapOob(HxSwap.INNER_HTML)), p(hxSwapOob(HxSwap.AFTER_END, "#x")))
                        .render());
        assertEquals("""
                <hx-partial hx-target="#alerts" hx-swap="beforeend">Salvo</hx-partial>""",
                hxPartial(hxTarget("#alerts"), hxSwap(HxSwap.BEFORE_END), text(Text.data("Salvo"))).render());
    }

    @Test
    void swapStylesAndModifiers() {
        assertEquals("innerHTML outerHTML textContent beforebegin afterbegin beforeend afterend delete none",
                String.join(" ", Arrays.stream(HxSwap.values()).map(HxSwap::token).toList()));
        val spec = SwapSpec.of(HxSwap.OUTER_HTML).transition().swap(Duration.ofSeconds(1))
                .settle(Duration.ofMillis(100)).ignoreTitle().scroll(ScrollEdge.TOP).scroll("#log", ScrollEdge.BOTTOM)
                .show(ScrollEdge.BOTTOM).show("window", ScrollEdge.TOP).focusScroll(false);
        assertEquals("outerHTML transition:true swap:1000ms settle:100ms ignoreTitle:true scroll:top "
                + "scroll:#log:bottom show:bottom show:window:top focus-scroll:false", spec.token());
        assertEquals(spec.token(), spec.toString());
        assertEquals("innerHTML", SwapSpec.of(HxSwap.INNER_HTML).token());
        assertEquals("""
                <div hx-swap="delete swap:1000ms"></div>""",
                div(hxSwap(SwapSpec.of(HxSwap.DELETE).swap(Duration.ofSeconds(1)))).render());
    }

    @Test
    void historyFeedbackAndProcessing() {
        assertEquals("""
                <a hx-push-url="true" hx-push-url="/p" hx-replace-url="false" hx-replace-url="/r" hx-history="false" \
                hx-history-elt></a>""",
                a(hxPushUrl(true), hxPushUrl("/p"), hxReplaceUrl(false), hxReplaceUrl("/r"), hxHistoryOff(),
                        hxHistoryElt()).render());
        assertEquals("""
                <button hx-confirm="Excluir &quot;A&quot;?" hx-prompt="Motivo" hx-indicator="#spin" \
                hx-disabled-elt="this" hx-boost="true" hx-sync="closest form:queue last"></button>""",
                button(hxConfirm(Text.data("Excluir \"A\"?")), hxPrompt(Text.data("Motivo")), hxIndicator("#spin"),
                        hxDisabledElt("this"), hxBoost(true), hxSync("closest form", HxSync.QUEUE_LAST)).render());
        assertEquals("""
                <div hx-on:click="go()" hx-on::after-request="done()" hx-ext="json-enc" hx-disable \
                hx-disinherit="*" hx-inherit="hx-target"></div>""",
                div(hxOn("click", "go()"), hxOnHtmx("after-request", "done()"), hxExt("json-enc"), hxDisable(),
                        hxDisinherit("*"), hxInherit("hx-target")).render());
        assertEquals("drop abort replace queue first queue last queue all",
                String.join(" ", Arrays.stream(HxSync.values()).map(HxSync::token).toList()));
    }

    /** Example of use: an active search, as in the htmx examples, written with both DSLs. */
    @Test
    void activeSearchExample() {
        val search = fragment(
                input(type("search"), name("q"), placeholder(Text.data("Buscar")),
                        hxPost("/search"), hxTrigger("input changed delay:500ms, search"), hxTarget("#results"),
                        hxIndicator(".htmx-indicator")),
                table(tbody(id("results"))));
        assertEquals("""
                <input type="search" name="q" placeholder="Buscar" hx-post="/search" \
                hx-trigger="input changed delay:500ms, search" hx-target="#results" hx-indicator=".htmx-indicator">\
                <table><tbody id="results"></tbody></table>""", search.render());
    }
}
