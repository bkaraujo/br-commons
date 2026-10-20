package br.bundled.tracker.htmx.week;

import br.bundled.tracker.htmx.ui.Ui;
import br.bundled.tracker.shared.entry.Entry;
import br.bundled.tracker.shared.registry.Registered;
import br.bundled.tracker.shared.registry.Registry;
import br.bundled.tracker.shared.week.*;
import br.commons.lang.html.ButtonType;
import br.commons.lang.html.FormMethod;
import br.commons.lang.html.core.Content;
import br.commons.lang.html.core.Node;
import br.commons.lang.html.form.Field;
import br.commons.lang.html.form.Option;
import br.commons.lang.html.form.Violation;
import br.commons.lang.htmx.HxSwap;
import br.commons.locale.i18n.Text;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static br.bundled.tracker.htmx.ui.Ui.hours;
import static br.bundled.tracker.htmx.ui.Ui.message;
import static br.commons.lang.Html5.*;
import static br.commons.lang.Htmx.*;

/**
 * The week screen: navigation, the person filter, the entry form, the entries and the totals. Everything that
 * changes with the filter is inside {@code #week}, which navigation and saving swap.
 */
@NullMarked
final class WeekViews {


    /** The hidden field with the person of the filter, which saving and deleting send to keep the view. */
    static final String VIEW_PERSON = "view-person";

    private WeekViews() {}

    static Node screen(WeekState state, WeekFilter filter, Map<String, String> form, List<Violation> errors) {
        return section(id("week"), weekContent(state, filter, form, errors));
    }

    /** What {@code #week} receives on navigation and after saving. */
    static Node weekContent(WeekState state, WeekFilter filter, Map<String, String> form, List<Violation> errors) {
        val entries = state.entries().matching(filter::accepts);
        return fragment(
                div(cls("week-bar"), weekNav(filter, state.today()), personFilter(filter, state)),
                entryForm(form, errors, state, filter),
                div(cls("layout"),
                        entriesTable(entries, state),
                        totalsPanel(WeekTotals.of(filter.week(), entries, state), false)
                )
        );
    }

    // ---------- navigation and filter

    /** Previous and next week load into {@code #week} and push their URL; plain links without htmx. */
    private static Node weekNav(WeekFilter filter, LocalDate today) {
        val week = filter.week();
        return nav(
                cls("week-nav"),
                weekLink(filter.previous(), "←", "Semana anterior"),
                h2(text(Text.data(week.label()))),
                weekLink(filter.next(), "→", "Próxima semana"),
                when(!week.contains(today), () -> weekLink(new WeekFilter(Week.of(today), filter.person()), "Hoje", "Semana atual"))
        );
    }

    private static Node weekLink(WeekFilter target, String content, String label) {
        return a(
                href(target.url()), hxGet(target.url()), hxTarget("#week"), hxPushUrl(true), ariaLabel(message(label)),
                text(Text.data(content))
        );
    }

    /** Changing the person reloads {@code #week} with the URL pushed; the button submits it without htmx. */
    private static Node personFilter(WeekFilter filter, WeekState state) {
        val options = new ArrayList<Node>();
        options.add(option(value(""), text(message("Todas as pessoas"))));
        state.people().all().forEach(person -> options.add(String.valueOf(person.id()).equals(filter.personToken())
                ? option(value(String.valueOf(person.id())), selected(), text(Text.data(person.name())))
                : option(value(String.valueOf(person.id())), text(Text.data(person.name())))));
        return form(cls("person-filter"), method(FormMethod.GET), action("/"),
                input(type("hidden"), name("week"), value(filter.week().token())),
                label(htmlFor("filter-person"), cls("sr-only"), text(message("Pessoa"))),
                select(join(options, id("filter-person"), name("person"), hxGet("/"), hxTarget("#week"), hxPushUrl(true),
                        hxInclude("closest form"))),
                noscript(button(type(ButtonType.SUBMIT), text(message("Filtrar")))));
    }

    // ---------- new entry

    /** The new entry form; errors come back into it, next to each field. */
    static Node entryForm(Map<String, String> values, List<Violation> errors, WeekState state, WeekFilter filter) {
        return form(
                id("entry-form"), cls("entry-form"), method(FormMethod.POST), action("/entries"),
                hxPost("/entries"), hxTarget("#week"), hxSwap(HxSwap.INNER_HTML),
                input(type("hidden"), id(VIEW_PERSON), name(VIEW_PERSON), value(filter.personToken())),
                labeled(EntryFields.DATE, "Data", values, errors, null),
                labeled(EntryFields.PERSON, "Pessoa", values, errors, choices(state.people(), null)),
                labeled(EntryFields.PROJECT, "Projeto", values, errors,
                        choices(state.projects(), null)),
                labeled(EntryFields.DESCRIPTION, "Descrição", values, errors, null),
                labeled(EntryFields.START, "Início", values, errors, null),
                labeled(EntryFields.END, "Fim", values, errors, null),
                button(type(ButtonType.SUBMIT), text(message("Lançar")))
        );
    }

    /** A labeled control: a select when there are {@code options}, an input otherwise. */
    private static Node labeled(Field field, String label, Map<String, String> values, List<Violation> errors,
                                @Nullable List<Option> options) {
        val fieldId = "new-" + field.name();
        return div(
                cls("field"),
                label(htmlFor(fieldId), text(message(label))),
                control(field, values, options, id(fieldId)),
                Ui.errorOf(field, errors, EntryFields::message)
        );
    }

    private static Node control(Field field, Map<String, String> values, @Nullable List<Option> options,
                                Content... extra) {
        val current = values.getOrDefault(field.name(), "");
        return options == null
                ? input(DIALECT, field, current, extra)
                : select(DIALECT, field, options, Set.of(current), extra);
    }

    /**
     * The options of a register: a prompt, then the active items, plus the one the entry has when it was
     * deactivated after (only when editing: {@code kept}).
     */
    private static List<Option> choices(Registry<? extends Registered> registry, @Nullable Integer kept) {
        val options = new ArrayList<Option>();
        options.add(new Option("", message("Escolha…")));
        registry.all().stream()
                .filter(item -> item.active() || kept != null && item.id() == kept)
                .forEach(item -> options.add(new Option(String.valueOf(item.id()), Text.data(item.name()))));
        return options;
    }

    // ---------- entries

    private static Node entriesTable(List<Entry> entries, WeekState state) {
        return table(
                cls("entries"),
                thead(tr(th(text(message("Dia"))), th(text(message("Pessoa"))), th(text(message("Projeto"))),
                        th(text(message("Descrição"))), th(text(message("Início"))), th(text(message("Fim"))),
                        th(cls("num"), text(message("Horas"))), th(ariaLabel(message("Ações"))))
                ),
                tbody(
                        id("entries"),
                        each(entries, entry -> entryRow(entry, state)),
                        when(entries.isEmpty(), () -> tr(cls("empty"), td(colspan(8), text(message("Nenhum lançamento nesta semana.")))))
                )
        );
    }

    /** A row; editing swaps it for {@link #editRow}, deleting fades it out. */
    static Node entryRow(Entry entry, WeekState state) {
        val path = "/entries/" + entry.id();
        return tr(
                id("entry-" + entry.id()),
                td(time(datetime(entry.date().toString()), text(Text.data(Hours.day(entry.date()))))),
                td(text(Text.data(state.people().nameOf(entry.personId())))),
                td(text(Text.data(state.projects().nameOf(entry.projectId())))),
                td(cls("description"), text(Text.data(entry.description()))),
                td(time(datetime(entry.start().toString()), text(Text.data(entry.start().toString())))),
                td(time(datetime(entry.end().toString()), text(Text.data(entry.end().toString())))),
                td(cls("num"), time(datetime(entry.duration().toString()), text(Text.data(hours(entry.duration()))))),
                td(cls("actions"),
                        button(cls("link"), hxGet(path + "/edit"), hxTarget("closest tr"), hxSwap(HxSwap.OUTER_HTML),
                                text(message("Editar"))),
                        button(cls("link danger"), hxDelete(path), hxTarget("closest tr"), hxSwap(Ui.FADE_OUT),
                                hxInclude("#" + VIEW_PERSON), hxConfirm(message("Excluir este lançamento?")),
                                text(message("Excluir")))
                )
        );
    }

    /**
     * The row in edit mode. Inputs in a table row cannot sit in a form, so the save button sends them with
     * {@code hx-include="closest tr"} (and the filter, to answer with the same view).
     */
    static Node editRow(Entry entry, Map<String, String> values, List<Violation> errors, WeekState state) {
        val path = "/entries/" + entry.id();
        return tr(
                id("entry-" + entry.id()), cls("editing"),
                td(inline(EntryFields.DATE, "Data", values, errors, null)),
                td(inline(EntryFields.PERSON, "Pessoa", values, errors,
                        choices(state.people(), entry.personId()))),
                td(inline(EntryFields.PROJECT, "Projeto", values, errors,
                        choices(state.projects(), entry.projectId()))),
                td(inline(EntryFields.DESCRIPTION, "Descrição", values, errors, null)),
                td(inline(EntryFields.START, "Início", values, errors, null)),
                td(inline(EntryFields.END, "Fim", values, errors, null)),
                td(cls("num"), text(Text.data("—"))),
                td(cls("actions"),
                        button(hxPut(path), hxInclude("closest tr, #" + VIEW_PERSON), hxTarget("closest tr"),
                                hxSwap(HxSwap.OUTER_HTML), text(message("Salvar"))),
                        button(cls("link"), hxGet(path), hxTarget("closest tr"), hxSwap(HxSwap.OUTER_HTML),
                                text(message("Cancelar"))))
        );
    }

    private static Node inline(Field field, String label, Map<String, String> values, List<Violation> errors,
                               @Nullable List<Option> options) {
        return fragment(control(field, values, options, ariaLabel(message(label))),
                Ui.errorOf(field, errors, EntryFields::message));
    }

    // ---------- totals

    /** Hours per day (with a meter against the workday), per project, per person and in all. */
    static Node totalsPanel(WeekTotals totals, boolean outOfBand) {
        val content = new ArrayList<Content>(List.of(id("totals"), cls("totals")));
        if (outOfBand) {
            content.add(hxSwapOob());
        }
        content.addAll(List.of(
                h2(text(message("Totais"))),
                p(cls("week-total"), text(message("Semana ")),
                        strong(time(datetime(totals.total().toString()), text(Text.data(hours(totals.total())))))),
                h3(text(message("Por dia"))),
                ul(cls("days"), each(totals.byDay().entrySet(), day -> li(
                        span(cls("day"), text(Text.data(Hours.day(day.getKey())))),
                        meter(min("0"), max("10"), low("4"), high("9"), optimum("8"),
                                value(Ui.decimal(day.getValue())), title(Text.data(hours(day.getValue())))),
                        span(cls("num"), text(Text.data(hours(day.getValue()))))))),
                h3(text(message("Por projeto"))),
                shares(totals, totals.byProject()),
                h3(text(message("Por pessoa"))),
                shares(totals, totals.byPerson())));
        return aside(content.toArray(Content[]::new));
    }

    /** Hours and share of the total, by name. */
    private static Node shares(WeekTotals totals, Map<String, Duration> byName) {
        return byName.isEmpty()
                ? p(cls("empty"), text(message("Sem horas no período.")))
                : ul(cls("shares"), each(byName.entrySet(), item -> li(
                        span(text(Text.data(item.getKey()))),
                        span(cls("num"), text(Text.data(hours(item.getValue()) + " · "
                                + totals.percent(item.getValue()) + "%"))))));
    }

    private static Content[] join(List<Node> nodes, Content... attributes) {
        val all = new ArrayList<>(List.of(attributes));
        all.addAll(nodes);
        return all.toArray(Content[]::new);
    }
}
