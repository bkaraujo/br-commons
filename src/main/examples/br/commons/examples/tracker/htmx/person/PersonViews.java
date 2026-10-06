package br.commons.examples.tracker.htmx.person;

import br.commons.examples.tracker.htmx.ui.Status;
import br.commons.examples.tracker.htmx.ui.Ui;
import br.commons.examples.tracker.shared.entry.EntryStore;
import br.commons.examples.tracker.shared.person.Person;
import br.commons.examples.tracker.shared.person.PersonFields;
import br.commons.examples.tracker.shared.registry.Registry;
import br.commons.html.ButtonType;
import br.commons.html.FormMethod;
import br.commons.html.core.Node;
import br.commons.html.form.Field;
import br.commons.html.form.Violation;
import br.commons.htmx.HxSwap;
import br.commons.locale.i18n.Text;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static br.commons.examples.tracker.htmx.ui.Ui.message;
import static br.commons.html.html5.Html5.*;
import static br.commons.htmx.Htmx.*;

/** The people screen: the new person form and the list, edited, (de)activated and deleted row by row. */
@NullMarked
final class PersonViews {

    private PersonViews() {}

    static Node screen(Registry<Person> registry, EntryStore entries, Map<String, String> form, List<Violation> errors) {
        val people = registry.all();
        return section(
                id("people"), cls("registry"),
                h2(text(message("Pessoas"))),
                personForm(form, errors),
                table(
                        cls("records"),
                        thead(tr(th(text(message("Nome"))), th(text(message("E-mail"))), th(text(message("Situação"))),
                                th(cls("num"), text(message("Lançamentos"))), th(ariaLabel(message("Ações"))))),
                        tbody(each(people, person -> personRow(person, entries.ofPerson(person.id()), null)),
                                when(people.isEmpty(), () -> tr(cls("empty"),
                                        td(colspan(5), text(message("Nenhuma pessoa."))))))
                )
        );
    }

    /** Saving answers with the whole screen (the list is sorted by name); errors come back into the form. */
    static Node personForm(Map<String, String> values, List<Violation> errors) {
        return form(
                id("person-form"), cls("record-form"), method(FormMethod.POST), action("/people"),
                hxPost("/people"), hxTarget("#people"), hxSwap(HxSwap.OUTER_HTML),
                labeled(PersonFields.NAME, "Nome", values, errors),
                labeled(PersonFields.EMAIL, "E-mail", values, errors),
                button(type(ButtonType.SUBMIT), text(message("Cadastrar")))
        );
    }

    private static Node labeled(Field field, String label, Map<String, String> values, List<Violation> errors) {
        val fieldId = "person-" + field.name();
        return div(
                cls("field"),
                label(htmlFor(fieldId), text(message(label))),
                input(DIALECT, field, values.getOrDefault(field.name(), ""), id(fieldId)),
                Ui.errorOf(field, errors, PersonFields::message)
        );
    }

    /** A row; {@code error} explains a refused delete. */
    static Node personRow(Person person, long entries, @Nullable String error) {
        val path = "/people/" + person.id();
        return tr(
                id("person-" + person.id()), cls(person.active() ? "" : "inactive"),
                td(text(Text.data(person.name()))),
                td(text(Text.data(person.email()))),
                td(Status.of(person.active())),
                td(cls("num"), text(Text.data(String.valueOf(entries)))),
                td(
                        cls("actions"),
                        button(cls("link"), hxGet(path + "/edit"), hxTarget("closest tr"), hxSwap(HxSwap.OUTER_HTML),
                                text(message("Editar"))),
                        button(cls("link"), hxPost(path + "/active"), hxTarget("closest tr"), hxSwap(HxSwap.OUTER_HTML),
                                text(message(person.active() ? "Desativar" : "Ativar"))),
                        button(cls("link danger"), hxDelete(path), hxTarget("closest tr"), hxSwap(Ui.FADE_OUT),
                                hxConfirm(message("Excluir \"" + person.name() + "\"?")),
                                text(message("Excluir"))),
                        when(error != null, () -> small(cls("error"), role("alert"), text(message(String.valueOf(error)))))
                )
        );
    }

    /** The row in edit mode; the save button sends its inputs with {@code hx-include="closest tr"}. */
    static Node editRow(Person person, long entries, Map<String, String> values, List<Violation> errors) {
        val path = "/people/" + person.id();
        return tr(
                id("person-" + person.id()), cls("editing"),
                td(inline(PersonFields.NAME, "Nome", values, errors)),
                td(inline(PersonFields.EMAIL, "E-mail", values, errors)),
                td(Status.of(person.active())),
                td(cls("num"), text(Text.data(String.valueOf(entries)))),
                td(
                        cls("actions"),
                        button(hxPut(path), hxInclude("closest tr"), hxTarget("closest tr"), hxSwap(HxSwap.OUTER_HTML),
                                text(message("Salvar"))),
                        button(cls("link"), hxGet(path), hxTarget("closest tr"), hxSwap(HxSwap.OUTER_HTML),
                                text(message("Cancelar")))
                )
        );
    }

    private static Node inline(Field field, String label, Map<String, String> values, List<Violation> errors) {
        return fragment(
                input(DIALECT, field, values.getOrDefault(field.name(), ""), ariaLabel(message(label))),
                Ui.errorOf(field, errors, PersonFields::message)
        );
    }
}
