package br.bundled.tracker.htmx.ui;

import br.bundled.tracker.shared.Ids;
import br.bundled.tracker.shared.week.Hours;
import br.commons.lang.html.Html;
import br.commons.lang.html.core.Content;
import br.commons.lang.html.core.Node;
import br.commons.lang.html.form.Field;
import br.commons.lang.html.form.Violation;
import br.commons.lang.htmx.HtmxRequest;
import br.commons.lang.htmx.HxSwap;
import br.commons.lang.htmx.SwapSpec;
import br.commons.locale.i18n.Text;
import br.commons.protocol.http.HTTPStatus;
import br.commons.protocol.http.message.HTTPRequest;
import br.commons.protocol.http.message.HTTPResponse;
import br.commons.web.server.WebRequest;
import br.commons.web.WebResponse;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static br.commons.lang.Html5.*;
import static br.commons.lang.Htmx.*;

/**
 * What the screens share: htmx from the CDN, formats, the error next to a field and request helpers. Names avoid
 * the DSL ({@code pathId}, not {@code id}): a method declared here would hide the statically imported ones.
 */
@NullMarked
public final class Ui {

    /** htmx 2 from the CDN, as <a href="https://htmx.org/docs/#installing">the docs</a> recommend, with its integrity hash. */
    public static final String HTMX = "https://cdn.jsdelivr.net/npm/htmx.org@2.0.11/dist/htmx.min.js";
    public static final String HTMX_INTEGRITY = "sha384-2OatzQy1H+Zd/IIrjr1TcuDGqLXeHhbooAyJY1KdQMKnr4LZ22k31GBLdYKHmVjg";

    /** Removing a row waits for the {@code .htmx-swapping} fade of the CSS. */
    public static final SwapSpec FADE_OUT = SwapSpec.of(HxSwap.OUTER_HTML).swap(Duration.ofMillis(200));

    private Ui() {}

    public static Text message(String text) {
        return Text.message(text);
    }

    /** {@code 7h30}. */
    public static String hours(Duration duration) {
        return Hours.of(duration);
    }

    /** Hours as a decimal number, for a {@code meter}. */
    public static String decimal(Duration duration) {
        return Hours.decimal(duration);
    }

    /** The message of the field's violation next to it, or nothing. */
    public static Node errorOf(Field field, List<Violation> errors, Function<Violation, String> messages) {
        return errors.stream()
                .filter(violation -> violation.field().equals(field.name()))
                .findFirst()
                .map(violation -> (Node) small(cls("error"), role("alert"), text(message(messages.apply(violation)))))
                .orElseGet(Html::fragment);
    }

    /** Status, entry count and the edit, (de)activate and delete actions of a row; {@code error} explains a refused delete. */
    public static Node rowTail(String path, boolean active, long entries, Text confirm, @Nullable String error) {
        return fragment(
                td(Status.of(active)),
                td(cls("num"), text(Text.data(String.valueOf(entries)))),
                td(cls("actions"),
                        button(cls("link"), hxGet(path + "/edit"), hxTarget("closest tr"), hxSwap(HxSwap.OUTER_HTML),
                                text(message("Editar"))),
                        button(cls("link"), hxPost(path + "/active"), hxTarget("closest tr"), hxSwap(HxSwap.OUTER_HTML),
                                text(message(active ? "Desativar" : "Ativar"))),
                        button(cls("link danger"), hxDelete(path), hxTarget("closest tr"), hxSwap(FADE_OUT),
                                hxConfirm(confirm), text(message("Excluir"))),
                        when(error != null, () -> small(cls("error"), role("alert"), text(message(String.valueOf(error))))))
        );
    }

    /** {@link #rowTail} in edit mode; the save button sends the row's inputs with {@code hx-include="closest tr"}. */
    public static Node editTail(String path, boolean active, long entries) {
        return fragment(
                td(Status.of(active)),
                td(cls("num"), text(Text.data(String.valueOf(entries)))),
                td(cls("actions"),
                        button(hxPut(path), hxInclude("closest tr"), hxTarget("closest tr"), hxSwap(HxSwap.OUTER_HTML),
                                text(message("Salvar"))),
                        button(cls("link"), hxGet(path), hxTarget("closest tr"), hxSwap(HxSwap.OUTER_HTML),
                                text(message("Cancelar"))))
        );
    }

    public static Content[] join(Content[] extra, Content... more) {
        val all = new ArrayList<>(List.of(extra));
        all.addAll(List.of(more));
        return all.toArray(Content[]::new);
    }

    /**
     * Whether to answer with a fragment: htmx asked, and it is neither a boosted navigation (which swaps the
     * whole body) nor a history restore (which needs the whole page).
     */
    public static boolean partial(HTTPRequest request) {
        val htmx = HtmxRequest.of(request);
        return htmx.isHtmx() && !htmx.boosted() && !htmx.historyRestore();
    }

    public static HTTPResponse ok(String html) {
        return WebResponse.html(HTTPStatus.OK, html);
    }

    /**
     * The {@code {id}} of the route; {@code -1} (matches nothing) when it is not a number, so {@code /people/abc}
     * answers like a missing id instead of failing.
     */
    public static int pathId(WebRequest request) {
        val id = number(request.pathParam("id").getOrElse(""));
        return id == null ? -1 : id;
    }

    /** A positive number, or {@code null} for anything else (absent, empty, not a number). */
    public static @Nullable Integer number(@Nullable String value) {
        return Ids.number(value);
    }
}
