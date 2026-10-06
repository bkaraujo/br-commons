package br.commons.examples.tracker.htmx.week;

import br.commons.Result;
import br.commons.examples.tracker.htmx.ui.Layout;
import br.commons.examples.tracker.htmx.ui.Screen;
import br.commons.examples.tracker.htmx.ui.Ui;
import br.commons.examples.tracker.shared.entry.EntryInput;
import br.commons.examples.tracker.shared.week.EntryFields;
import br.commons.examples.tracker.shared.week.Week;
import br.commons.examples.tracker.shared.week.WeekState;
import br.commons.examples.tracker.shared.week.WeekTotals;
import br.commons.html.form.Violation;
import br.commons.htmx.HtmxRequest;
import br.commons.htmx.HtmxResponse;
import br.commons.htmx.HxSwap;
import br.commons.http.MediaType;
import br.commons.http.message.HTTPResponse;
import br.commons.web.router.Router;
import br.commons.web.server.Form;
import br.commons.web.server.WebRequest;
import br.commons.web.server.WebResource;
import br.commons.web.server.WebResponse;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;

import static br.commons.examples.tracker.htmx.ui.Ui.ok;
import static br.commons.examples.tracker.htmx.ui.Ui.pathId;

/**
 * Routes of the week screen and its entries. {@code GET /?week=2026-W40&person=2} renders the whole page, or only
 * {@code #week} when htmx asks for it. Saving answers with the week of the entry, keeping the person filter, and
 * pushes its URL ({@code HX-Push-Url}); a delete answers with an empty row and the totals out of band.
 */
@NullMarked
public final class WeekRoutes implements WebResource {

    private final WeekState state;

    public WeekRoutes(WeekState state) {
        this.state = state;
    }

    @Override
    public void routes(Router router) {
        router.get("/", MediaType.TEXT_HTML, String.class, this::home).getOrThrow();
        router.post("/entries", MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, this::create).getOrThrow();
        router.get("/entries/{id}", MediaType.TEXT_HTML, String.class, this::show).getOrThrow();
        router.get("/entries/{id}/edit", MediaType.TEXT_HTML, String.class, this::edit).getOrThrow();
        router.put("/entries/{id}", MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, this::update).getOrThrow();
        router.delete("/entries/{id}", MediaType.TEXT_HTML, String.class, this::delete).getOrThrow();
    }

    private HTTPResponse home(WebRequest request) {
        val filter = WeekFilter.parse(request.parameter("week"), request.parameter("person"), state.today());
        val response = Ui.partial(request)
                ? ok(WeekViews.weekContent(state, filter, blank(filter), List.of()).render())
                : page(request, filter, blank(filter), List.of());
        return response.header("Vary", "HX-Request");
    }

    /**
     * Valid: the week of the new entry. Invalid: the form with its errors. Without htmx (a plain form post) it
     * falls back to post/redirect/get, or to the whole page with the errors.
     */
    private HTTPResponse create(WebRequest request) {
        val form = request.body(Form.class).getOrThrow();
        val htmx = HtmxRequest.of(request).isHtmx();
        val person = Ui.number(form.value(WeekViews.VIEW_PERSON));
        return switch (EntryFields.read(form::value, state, null)) {
            case Result.Success<EntryInput, List<Violation>>(var input) -> {
                val filter = new WeekFilter(Week.of(state.entries().add(input).date()), person);
                yield htmx ? saved(filter) : WebResponse.redirect(filter.url());
            }
            case Result.Failure<EntryInput, List<Violation>>(var errors) -> {
                val filter = new WeekFilter(Week.of(state.today()), person);
                yield htmx
                        ? HtmxResponse.of(ok(WeekViews.entryForm(EntryFields.values(form::value), errors, state, filter)
                                .render())).retarget("#entry-form").reswap(HxSwap.OUTER_HTML).response()
                        : page(request, filter, EntryFields.values(form::value), errors);
            }
        };
    }

    /** The row again (cancel of an edit). */
    private HTTPResponse show(WebRequest request) {
        return ok(state.entries().get(pathId(request)).map(entry -> WeekViews.entryRow(entry, state).render()).orElse(""));
    }

    private HTTPResponse edit(WebRequest request) {
        return ok(state.entries().get(pathId(request))
                .map(entry -> WeekViews.editRow(entry, EntryFields.values(entry), List.of(), state).render())
                .orElse(""));
    }

    /** Valid: the week of the entry, re-sorted, its URL pushed. Invalid: the row in edit mode with its errors. */
    private HTTPResponse update(WebRequest request) {
        val current = state.entries().get(pathId(request));
        if (current.isEmpty()) {
            return ok("");
        }
        val entry = current.get();
        val form = request.body(Form.class).getOrThrow();
        val person = Ui.number(form.value(WeekViews.VIEW_PERSON));
        return switch (EntryFields.read(form::value, state, entry)) {
            case Result.Success<EntryInput, List<Violation>>(var input) -> {
                val saved = state.entries().update(entry.id(), input).orElse(entry);
                yield HtmxResponse.of(saved(new WeekFilter(Week.of(saved.date()), person)))
                        .retarget("#week").reswap(HxSwap.INNER_HTML).response();
            }
            case Result.Failure<EntryInput, List<Violation>>(var errors) ->
                    ok(WeekViews.editRow(entry, EntryFields.values(form::value), errors, state).render());
        };
    }

    /** An empty row (200, not 204: htmx swaps nothing on 204) and the totals of the view, out of band. */
    private HTTPResponse delete(WebRequest request) {
        val person = Ui.number(request.parameter(WeekViews.VIEW_PERSON));
        val body = state.entries().remove(pathId(request))
                .map(entry -> new WeekFilter(Week.of(entry.date()), person))
                .map(filter -> WeekViews.totalsPanel(WeekTotals.of(filter.week(), state.entries().matching(filter::accepts), state),
                        true).render())
                .orElse("");
        return ok(body);
    }

    /** The week of a saved entry, with its URL pushed into the history. */
    private HTTPResponse saved(WeekFilter filter) {
        val body = WeekViews.weekContent(state, filter, blank(filter), List.of()).render();
        return HtmxResponse.of(ok(body)).pushUrl(filter.url()).response();
    }

    private HTTPResponse page(WebRequest request, WeekFilter filter, Map<String, String> form, List<Violation> errors) {
        return ok(Layout.page(Screen.WEEK,
                WeekViews.screen(state, filter, form, errors)));
    }

    /** A blank form dated today when today is in the week, or on its Monday. */
    private Map<String, String> blank(WeekFilter filter) {
        val today = state.today();
        return EntryFields.blank(filter.week().contains(today) ? today : filter.week().monday(), filter.personToken());
    }
}
