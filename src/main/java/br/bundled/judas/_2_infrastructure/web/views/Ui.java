package br.bundled.judas._2_infrastructure.web.views;

import br.commons.lang.htmx.HtmxRequest;
import br.commons.locale.i18n.I18N;
import br.commons.locale.i18n.Text;
import br.commons.protocol.http.HTTPStatus;
import br.commons.protocol.http.message.HTTPRequest;
import br.commons.protocol.http.message.HTTPResponse;
import br.commons.web.WebResponse;
import org.jspecify.annotations.NullMarked;

/** The texts of the screens and the HTML answers every screen shares (ARCH-08, UI-02). */
@NullMarked
public final class Ui {

    /** The prefix of the interface keys. */
    public static final String PREFIX = "forms.";

    /** The bundle of the interface texts ({@code forms_pt_BR}, {@code forms_en_US}). */
    public static final String BUNDLE = "br/bundled/judas/forms";

    static { I18N.register(PREFIX, BUNDLE); }

    private Ui() {}

    /** The interface text of {@code key}, ready for the DSL. */
    public static Text message(String key, Object... args) { return Text.message(string(key, args)); }

    /** The interface text of {@code key} in the process locale. */
    public static String string(String key, Object... args) { return I18N.of(key, args); }

    /** Whether the request is a boosted navigation, answered with the content only (UI-21). */
    public static boolean boosted(HTTPRequest request) { return HtmxRequest.of(request).navigation(); }

    /** An HTML answer that varies by the htmx headers: the same URL serves the page and its parts (UI-21). */
    public static HTTPResponse html(HTTPStatus status, String html) {
        return WebResponse.html(status, html).header("Vary", HtmxRequest.VARY);
    }
}
