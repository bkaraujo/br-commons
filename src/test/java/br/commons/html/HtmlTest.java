package br.commons.html;

import br.commons.html.core.Node;
import br.commons.html.html4.CellAlign;
import br.commons.html.html4.CellVerticalAlign;
import br.commons.html.html4.Html4;
import br.commons.html.html4.ParamValueType;
import br.commons.html.html4.TableFrame;
import br.commons.html.html4.TableRules;
import br.commons.html.html5.Autocapitalize;
import br.commons.html.html5.CrossOrigin;
import br.commons.html.html5.EnterKeyHint;
import br.commons.html.html5.FetchPriority;
import br.commons.html.html5.Html5;
import br.commons.html.html5.ImageDecoding;
import br.commons.html.html5.InputMode;
import br.commons.html.html5.LinkTarget;
import br.commons.html.html5.Loading;
import br.commons.html.html5.PopoverState;
import br.commons.html.html5.Preload;
import br.commons.html.html5.ReferrerPolicy;
import br.commons.html.html5.TextWrap;
import br.commons.html.html5.TrackKind;
import br.commons.i18n.Text;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.List;

import static br.commons.html.Html.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlTest {

    private static Text t(String value) {
        return Text.data(value);
    }

    @Test
    void escapesTextAndAttributesByConstruction() {
        val attack = "\"><script>alert('x')</script>&";
        val node = div(id(attack), text(t(attack)));
        assertEquals("<div id=\"&quot;&gt;&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;&amp;\">"
                + "&quot;&gt;&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;&amp;</div>", node.render());
        assertEquals("<p>Açaí €</p>", p(text(t("Açaí €"))).render());
    }

    @Test
    void voidElementsHaveNoEndTag() {
        for (val tag : List.of("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param",
                "source", "track", "wbr")) {
            assertEquals("<" + tag + ">", element(tag, text(t("ignored"))).render(), tag);
        }
        assertEquals("""
                <input type="text" name="cpf" disabled readonly>""",
                input(type("text"), name("cpf"), disabled(), readonly()).render());
    }

    @Test
    void eachVersionWritesItsDoctypeAndCharset() {
        assertEquals(Markup.flat("""
                <!DOCTYPE html>
                <html lang="pt-BR">
                  <head><meta charset="utf-8"><title>JUDAS</title></head>
                  <body><main><h1></h1></main></body>
                </html>
                """), Html5.document(html(lang("pt-BR"),
                head(Html5.metaCharset("utf-8"), title(text(t("JUDAS")))), body(Html5.main(h1())))));
        assertEquals(Markup.flat("""
                <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
                <html>
                  <head><meta http-equiv="Content-Type" content="text/html; charset=utf-8"></head>
                  <body></body>
                </html>
                """), Html4.document(html(head(Html4.metaCharset("utf-8")), body())));
        assertEquals(Html5.metaCharset("utf-8").render(), metaCharset(Html5.DIALECT, "utf-8").render());
        assertEquals(Html4.document(p()), document(Html4.DIALECT, p()));
    }

    @Test
    void composesFragmentsLoopsAndConditions() {
        assertEquals("<ul><li>a</li><li>b</li></ul>", ul(each(List.of("a", "b"), item -> li(text(t(item))))).render());
        assertEquals("", when(false, () -> p()).render());
        assertEquals("<p></p>", when(true, () -> p()).render());
        assertEquals("""
                <form data-msg-empty="x"></form>""",
                form(Html5.data("msg-empty", "x")).render());
        assertEquals("<b>ok</b><i>!</i>", fragment(raw("<b>ok</b>"), raw("<i>!</i>")).render());
    }

    @Test
    void commonElementsUseTheirTag() {
        val elements = List.<Node.Element>of(html(), head(), title(), base(), link(), meta(), style(), script(),
                noscript(), body(), address(), h1(), h2(), h3(), h4(), h5(), h6(), blockquote(), dd(), div(), dl(), dt(),
                hr(), li(), ol(), p(), pre(), ul(), a(), abbr(), b(), bdo(), br(), cite(), code(), dfn(), em(), i(),
                kbd(), q(), samp(), small(), span(), strong(), sub(), sup(), var(), del(), ins(), img(), object(), map(),
                area(), table(), caption(), colgroup(), col(), thead(), tbody(), tfoot(), tr(), th(), td(), form(),
                fieldset(), legend(), label(), input(), button(), select(), optgroup(), option(), textarea());
        assertUseTheirTag(elements);
    }

    @Test
    void versionElementsUseTheirTag() {
        assertUseTheirTag(List.of(Html5.article(), Html5.aside(), Html5.footer(), Html5.header(), Html5.hgroup(),
                Html5.main(), Html5.nav(), Html5.search(), Html5.section(), Html5.figcaption(), Html5.figure(),
                Html5.menu(), Html5.bdi(), Html5.data(), Html5.mark(), Html5.rp(), Html5.rt(), Html5.ruby(), Html5.s(),
                Html5.time(), Html5.u(), Html5.wbr(), Html5.audio(), Html5.video(), Html5.track(), Html5.source(),
                Html5.picture(), Html5.embed(), Html5.iframe(), Html5.canvas(), Html5.svg(), Html5.math(),
                Html5.datalist(), Html5.meter(), Html5.output(), Html5.progress(), Html5.selectedcontent(),
                Html5.details(), Html5.summary(), Html5.dialog(), Html5.geolocation(), Html5.slot(), Html5.template()));
        assertUseTheirTag(List.of(Html4.acronym(), Html4.big(), Html4.param(), Html4.tt()));
    }

    private static void assertUseTheirTag(List<Node.Element> elements) {
        for (val element : elements) {
            assertTrue(element.render().startsWith("<" + element.tag() + ">"), element.tag());
        }
    }

    @Test
    void commonAttributes() {
        assertEquals("""
                <a id="i" class="c" style="color:red" title="t" lang="pt" dir="rtl" accesskey="k" tabindex="1" \
                href="/h" hreflang="en" rel="next" charset="utf-8"></a>""",
                a(id("i"), cls("c"), style("color:red"), title(t("t")), lang("pt"), dir(Direction.RTL),
                        accesskey("k"), tabindex(1), href("/h"), hreflang("en"), rel("next"), charset("utf-8")).render());
        assertEquals("""
                <form action="/a" method="post" enctype="multipart/form-data" accept-charset="UTF-8"></form>""",
                form(action("/a"), method(FormMethod.POST), enctype(FormEnctype.MULTIPART), acceptCharset("UTF-8"))
                        .render());
        assertEquals("""
                <th scope="col" abbr="a" colspan="2" rowspan="3" headers="h"></th>""",
                th(scope(CellScope.COL), abbr(t("a")), colspan(2), rowspan(3), headers("h")).render());
        assertEquals("""
                <area shape="circle" coords="1,2,3" alt="x" href="/">""",
                area(shape(AreaShape.CIRCLE), coords("1,2,3"), alt(t("x")), href("/")).render());
        assertEquals("""
                <img src="/i.png" width="10" height="20" usemap="#m" ismap>""",
                img(src("/i.png"), width(10), height(20), usemap("#m"), ismap()).render());
        assertEquals("""
                <button type="submit" value="v" name="n"></button>""",
                button(type(ButtonType.SUBMIT), value("v"), name("n")).render());
        assertEquals("""
                <option label="l" selected></option><input checked multiple maxlength="3" size="4" accept="image/*">""",
                fragment(option(label(t("l")), selected()), input(checked(), multiple(), maxlength(3), size(4),
                        accept("image/*"))).render());
        assertEquals("""
                <meta http-equiv="refresh" content="5"><label for="f"></label><textarea rows="3" cols="40"></textarea>\
                <ins cite="/c" datetime="2026-10-02"></ins><colgroup span="2"></colgroup><script defer></script>\
                <object data="/o"></object><link media="print">""",
                fragment(meta(httpEquiv("refresh"), content("5")), label(htmlFor("f")), textarea(rows(3), cols(40)),
                        ins(cite("/c"), datetime("2026-10-02")), colgroup(span(2)), script(defer()),
                        object(data("/o")), link(media("print"))).render());
    }

    @Test
    void html5Attributes() {
        assertEquals("""
                <div autocapitalize="words" autofocus contenteditable="true" dir="auto" draggable="false" \
                enterkeyhint="send" hidden hidden="until-found" inert inputmode="numeric" itemid="urn:x" itemprop="p" \
                itemref="r" itemscope itemtype="https://schema.org/Person" nonce="n" part="p" popover="manual" \
                role="dialog" slot="s" spellcheck="false" translate="no" aria-label="a" aria-labelledby="l" \
                aria-describedby="d" aria-hidden="true"></div>""",
                div(Html5.autocapitalize(Autocapitalize.WORDS), Html5.autofocus(), Html5.contenteditable(true),
                        Html5.dirAuto(), Html5.draggable(false), Html5.enterkeyhint(EnterKeyHint.SEND), Html5.hidden(),
                        Html5.hiddenUntilFound(), Html5.inert(), Html5.inputmode(InputMode.NUMERIC), Html5.itemid("urn:x"),
                        Html5.itemprop("p"), Html5.itemref("r"), Html5.itemscope(),
                        Html5.itemtype("https://schema.org/Person"), Html5.nonce("n"), Html5.part("p"),
                        Html5.popover(PopoverState.MANUAL), Html5.role("dialog"), Html5.slot("s"),
                        Html5.spellcheck(false), Html5.translate(false), Html5.ariaLabel(t("a")),
                        Html5.ariaLabelledby("l"), Html5.ariaDescribedby("d"), Html5.ariaHidden(true)).render());
        assertEquals("""
                <input form="f" formaction="/x" formenctype="text/plain" formmethod="get" formnovalidate \
                formtarget="_blank" list="l" min="1" max="9" step="2" minlength="1" pattern="\\d" placeholder="p" \
                required autocomplete="email" capture="user" dirname="d" popovertarget="p">""",
                input(Html5.form("f"), Html5.formaction("/x"), Html5.formenctype(FormEnctype.TEXT_PLAIN),
                        Html5.formmethod(FormMethod.GET), Html5.formnovalidate(), Html5.formtarget(LinkTarget.BLANK),
                        Html5.list("l"), Html5.min("1"), Html5.max("9"), Html5.step("2"), Html5.minlength(1),
                        Html5.pattern("\\d"), Html5.placeholder(t("p")), Html5.required(), Html5.autocomplete("email"),
                        Html5.capture("user"), Html5.dirname("d"), Html5.popovertarget("p")).render());
        assertEquals("""
                <video autoplay controls loop muted playsinline poster="/p.png" preload="metadata" \
                crossorigin="anonymous"></video><track kind="captions" srclang="pt" default>\
                <img loading="lazy" decoding="async" fetchpriority="high" srcset="a.png 2x" sizes="50vw" \
                referrerpolicy="no-referrer">""",
                fragment(Html5.video(Html5.autoplay(), Html5.controls(), Html5.loop(), Html5.muted(), Html5.playsinline(),
                                Html5.poster("/p.png"), Html5.preload(Preload.METADATA),
                                Html5.crossorigin(CrossOrigin.ANONYMOUS)),
                        Html5.track(Html5.kind(TrackKind.CAPTIONS), Html5.srclang("pt"), Html5.defaultTrack()),
                        img(Html5.loading(Loading.LAZY), Html5.decoding(ImageDecoding.ASYNC),
                                Html5.fetchpriority(FetchPriority.HIGH), Html5.srcset("a.png 2x"), Html5.sizes("50vw"),
                                Html5.referrerpolicy(ReferrerPolicy.NO_REFERRER))).render());
        assertEquals("""
                <form method="dialog" novalidate target="_self"></form><a target="frame" download ping="/p"></a>\
                <a download="f.txt"></a><script async integrity="sha384-x"></script><link as="style">\
                <iframe allow="fullscreen" sandbox="allow-forms" srcdoc="&lt;p&gt;"></iframe>\
                <meter low="1" high="9" optimum="5"></meter><ol reversed start="3"></ol>\
                <details open></details><textarea wrap="hard"></textarea>""",
                fragment(form(Html5.methodDialog(), Html5.novalidate(), Html5.target(LinkTarget.SELF)),
                        a(Html5.target("frame"), Html5.download(), Html5.ping("/p")), a(Html5.download("f.txt")),
                        script(Html5.async(), Html5.integrity("sha384-x")), link(Html5.as("style")),
                        Html5.iframe(Html5.allow("fullscreen"), Html5.sandbox("allow-forms"), Html5.srcdoc("<p>")),
                        Html5.meter(Html5.low("1"), Html5.high("9"), Html5.optimum("5")),
                        ol(Html5.reversed(), Html5.start(3)), Html5.details(Html5.open()),
                        textarea(Html5.wrap(TextWrap.HARD))).render());
    }

    @Test
    void html4Attributes() {
        assertEquals("""
                <table summary="s" border="1" cellpadding="2" cellspacing="0" frame="hsides" rules="groups" \
                width="50%"><tr align="char" char="," charoff="1" valign="top"><td axis="a" height="10%"></td></tr>\
                </table>""",
                table(Html4.summary(t("s")), Html4.border(1), Html4.cellpadding("2"), Html4.cellspacing("0"),
                        Html4.frame(TableFrame.HSIDES), Html4.rules(TableRules.GROUPS), Html4.width("50%"),
                        tr(Html4.align(CellAlign.CHAR), Html4.alignChar(","), Html4.charoff("1"),
                                Html4.valign(CellVerticalAlign.TOP),
                                td(Html4.axis("a"), Html4.height("10%")))).render());
        assertEquals("""
                <object classid="c" codebase="/b" codetype="application/java" declare standby="s">\
                <param valuetype="ref"></object><img longdesc="/d"><area nohref><link rev="made">\
                <meta scheme="ISBN">""",
                fragment(object(Html4.classid("c"), Html4.codebase("/b"), Html4.codetype("application/java"),
                                Html4.declare(), Html4.standby(t("s")), Html4.param(Html4.valuetype(ParamValueType.REF))),
                        img(Html4.longdesc("/d")), area(Html4.nohref()), link(Html4.rev("made")),
                        meta(Html4.scheme("ISBN"))).render());
    }

    /** Regression (H8): script/style content went through text(...) and got escaped. */
    @Test
    void scriptAndStyleTextAreNotEscaped() {
        assertEquals("<script>if (a < b && c) go(\"x\");</script>",
                script(scriptText("if (a < b && c) go(\"x\");")).render());
        assertEquals("<style>a > b { content: \"&\"; }</style>", style(styleText("a > b { content: \"&\"; }")).render());
        assertThrows(IllegalArgumentException.class, () -> scriptText("x</SCRIPT><img src=x>"));
        assertThrows(IllegalArgumentException.class, () -> styleText("</style>"));
    }

    /** Regression (S4): tag and attribute names were written raw, so a name from data injected markup. */
    @Test
    void invalidTagAndAttributeNamesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> element("x onload=alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> element("div><script"));
        assertThrows(IllegalArgumentException.class, () -> element(""));
        assertThrows(IllegalArgumentException.class, () -> new br.commons.html.core.Attribute("a\" onclick=\"x", "v"));
        assertThrows(IllegalArgumentException.class, () -> br.commons.htmx.Htmx.hxOn("click onmouseover", "x()"));
        assertThrows(IllegalArgumentException.class, () -> br.commons.htmx.Htmx.hxOn("click/x", "x()"));

        assertEquals("<my-widget data-id=\"1\" @click=\"go()\" x-on:click=\"go()\"></my-widget>",
                element("my-widget", new br.commons.html.core.Attribute("data-id", "1"),
                        new br.commons.html.core.Attribute("@click", "go()"),
                        new br.commons.html.core.Attribute("x-on:click", "go()")).render());
        assertEquals("hx-on::after-request", br.commons.htmx.Htmx.hxOnHtmx("after-request", "x()").name());
    }
}
