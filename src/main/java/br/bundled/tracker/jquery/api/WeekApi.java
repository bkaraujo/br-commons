package br.bundled.tracker.jquery.api;

import br.bundled.tracker.shared.Ids;
import br.bundled.tracker.shared.entry.Entry;
import br.bundled.tracker.shared.entry.EntryInput;
import br.bundled.tracker.shared.registry.Registered;
import br.bundled.tracker.shared.registry.Registry;
import br.bundled.tracker.shared.week.*;
import br.commons.Result;
import br.commons.lang.html.form.Violation;
import br.commons.protocol.http.MediaType;
import br.commons.protocol.http.message.HTTPResponse;
import br.commons.web.WebResponse;
import br.commons.web.router.Route;
import br.commons.web.server.WebRequest;
import br.commons.web.server.WebResource;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * The JSON API of the week screen; the browser renders it all, so the answers carry ready-to-show text (hours,
 * days, names) next to the values:
 * <ul>
 *   <li>{@code GET /api/week?week=2026-W40&person=2} — the week (current one when absent or invalid), its entries,
 *       its totals and the choices of the entry form;</li>
 *   <li>{@code POST /api/entries} {@code {"date": "2026-10-05", "project": "1", "person": "2", "description": "…",
 *       "start": "09:00", "end": "12:00"}} — 201 with the entry (and its week), or 422 with the errors;</li>
 *   <li>{@code PUT /api/entries/{id}} — the changed entry, 422 or 404;</li>
 *   <li>{@code DELETE /api/entries/{id}} — 204, or 404.</li>
 * </ul>
 */
@NullMarked
public final class WeekApi implements WebResource {

    /** An entry as the table shows it; {@code week} is where it falls, to go there after saving. */
    record EntryView(int id, String date, String day, int person, String personName, int project,
                     String projectName, String description, String start, String end, String hours, String week) {}

    record DayTotal(String date, String day, String hours, String decimal) {}

    record Share(String name, String hours, long percent) {}

    record Totals(String total, List<DayTotal> days, List<Share> projects, List<Share> people) {}

    /** An item of a register as a choice of the form; an inactive one shows only when the entry already has it. */
    record Choice(int id, String name, boolean active) {}

    /**
     * {@code current} is the token of today's week; {@code date} is the date a new entry starts with (today in the
     * current week, Monday in any other).
     */
    record WeekView(String week, String label, String previous, String next, String current, String date,
                    @Nullable Integer person, List<EntryView> entries, Totals totals, List<Choice> people,
                    List<Choice> projects) {}

    private final WeekState state;

    public WeekApi(WeekState state) {
        this.state = state;
    }

    @Override
    public List<Result<Route, String>> routes() {
        return List.of(
                Route.get("/api/week", MediaType.APPLICATION_JSON, WeekView.class, true, this::week),
                Route.post("/api/entries", MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, Map.class, Object.class,
                        true, this::create),
                Route.put("/api/entries/{id}", MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, Map.class,
                        Object.class, true, this::update),
                Route.delete("/api/entries/{id}", MediaType.APPLICATION_JSON, Void.class, true, this::delete));
    }

    private HTTPResponse week(WebRequest request) {
        val today = state.today();
        val week = Week.parse(request.parameter("week"), today);
        val person = Ids.number(request.parameter("person"));
        val entries = state.entries().matching(entry -> week.contains(entry.date())
                && (person == null || person == entry.personId()));
        return WebResponse.ok(new WeekView(week.token(), week.label(), week.previous().token(), week.next().token(),
                Week.of(today).token(), (week.contains(today) ? today : week.monday()).toString(), person,
                entries.stream().map(this::view).toList(), totals(WeekTotals.of(week, entries, state)),
                choices(state.people()), choices(state.projects())));
    }

    private HTTPResponse create(WebRequest request) {
        return Json.withValues(request, values -> switch (EntryFields.read(values, state, null)) {
            case Result.Success<EntryInput, List<Violation>>(var input) ->
                    WebResponse.created(view(state.entries().add(input)));
            case Result.Failure<EntryInput, List<Violation>>(var errors) -> Json.rejected(errors, EntryFields::message);
        });
    }

    private HTTPResponse update(WebRequest request) {
        val current = state.entries().get(Json.id(request));
        if (current.isEmpty()) return Json.notFound();
        val entry = current.get();
        return Json.withValues(request, values -> switch (EntryFields.read(values, state, entry)) {
            case Result.Success<EntryInput, List<Violation>>(var input) ->
                    WebResponse.ok(view(state.entries().update(entry.id(), input).orElse(entry)));
            case Result.Failure<EntryInput, List<Violation>>(var errors) -> Json.rejected(errors, EntryFields::message);
        });
    }

    private HTTPResponse delete(WebRequest request) {
        return state.entries().remove(Json.id(request)).isPresent() ? WebResponse.noContent() : Json.notFound();
    }

    private EntryView view(Entry entry) {
        return new EntryView(entry.id(), entry.date().toString(), Hours.day(entry.date()), entry.personId(),
                state.people().nameOf(entry.personId()), entry.projectId(), state.projects().nameOf(entry.projectId()),
                entry.description(), entry.start().toString(), entry.end().toString(), Hours.of(entry.duration()),
                Week.of(entry.date()).token());
    }

    private static Totals totals(WeekTotals totals) {
        return new Totals(Hours.of(totals.total()),
                totals.byDay().entrySet().stream().map(day -> new DayTotal(day.getKey().toString(),
                        Hours.day(day.getKey()), Hours.of(day.getValue()), Hours.decimal(day.getValue()))).toList(),
                shares(totals, totals.byProject()), shares(totals, totals.byPerson()));
    }

    private static List<Share> shares(WeekTotals totals, Map<String, Duration> byName) {
        return byName.entrySet().stream()
                .map(item -> new Share(item.getKey(), Hours.of(item.getValue()), totals.percent(item.getValue())))
                .toList();
    }

    private static List<Choice> choices(Registry<? extends Registered> registry) {
        return registry.all().stream().map(item -> new Choice(item.id(), item.name(), item.active())).toList();
    }
}
