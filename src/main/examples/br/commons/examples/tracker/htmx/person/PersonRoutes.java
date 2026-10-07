package br.commons.examples.tracker.htmx.person;

import br.commons.Result;
import br.commons.examples.tracker.htmx.ui.Layout;
import br.commons.examples.tracker.htmx.ui.Screen;
import br.commons.examples.tracker.htmx.ui.Ui;
import br.commons.examples.tracker.shared.entry.EntryStore;
import br.commons.examples.tracker.shared.person.Person;
import br.commons.examples.tracker.shared.person.PersonFields;
import br.commons.examples.tracker.shared.person.PersonInput;
import br.commons.examples.tracker.shared.registry.Registry;
import br.commons.lang.html.form.Violation;
import br.commons.lang.htmx.HtmxRequest;
import br.commons.lang.htmx.HtmxResponse;
import br.commons.lang.htmx.HxSwap;
import br.commons.protocol.http.MediaType;
import br.commons.protocol.http.message.HTTPResponse;
import br.commons.web.router.Router;
import br.commons.web.server.Form;
import br.commons.web.server.WebRequest;
import br.commons.web.server.WebResource;
import br.commons.web.WebResponse;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static br.commons.examples.tracker.htmx.ui.Ui.ok;
import static br.commons.examples.tracker.htmx.ui.Ui.pathId;

/** Routes of the people register. A person with entries cannot be deleted: the row says so, deactivate them. */
@NullMarked
public final class PersonRoutes implements WebResource {

    private final Registry<Person> people;
    private final EntryStore entries;

    public PersonRoutes(Registry<Person> people, EntryStore entries) {
        this.people = people;
        this.entries = entries;
    }

    @Override
    public void routes(Router router) {
        router.get("/people", MediaType.TEXT_HTML, String.class, this::list).getOrThrow();
        router.post("/people", MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, this::create).getOrThrow();
        router.get("/people/{id}", MediaType.TEXT_HTML, String.class, this::show).getOrThrow();
        router.get("/people/{id}/edit", MediaType.TEXT_HTML, String.class, this::edit).getOrThrow();
        router.put("/people/{id}", MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, this::update).getOrThrow();
        router.post("/people/{id}/active", MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, this::toggle).getOrThrow();
        router.delete("/people/{id}", MediaType.TEXT_HTML, String.class, this::delete).getOrThrow();
    }

    private HTTPResponse list(WebRequest request) {
        return Ui.partial(request) ? ok(PersonViews.screen(people, entries, Map.of(), List.of()).render())
                : page(request, Map.of(), List.of());
    }

    /** Valid: the whole screen with the new person in its place. Invalid: the form with its errors. */
    private HTTPResponse create(WebRequest request) {
        val form = request.body(Form.class).getOrThrow();
        val htmx = HtmxRequest.of(request).isHtmx();
        return switch (PersonFields.read(form::value, people, 0)) {
            case Result.Success<PersonInput, List<Violation>>(var input) -> {
                people.add(id -> Person.of(id, input));
                yield htmx ? ok(PersonViews.screen(people, entries, Map.of(), List.of()).render())
                        : WebResponse.redirect("/people");
            }
            case Result.Failure<PersonInput, List<Violation>>(var errors) -> htmx
                    ? HtmxResponse.of(ok(PersonViews.personForm(PersonFields.values(form::value), errors).render()))
                            .retarget("#person-form").reswap(HxSwap.OUTER_HTML).response()
                    : page(request, PersonFields.values(form::value), errors);
        };
    }

    private HTTPResponse show(WebRequest request) {
        return ok(people.get(pathId(request)).map(person -> row(person, null)).orElse(""));
    }

    private HTTPResponse edit(WebRequest request) {
        return ok(people.get(pathId(request))
                .map(person -> PersonViews.editRow(person, uses(person), PersonFields.values(person), List.of())
                        .render())
                .orElse(""));
    }

    private HTTPResponse update(WebRequest request) {
        val id = pathId(request);
        val form = request.body(Form.class).getOrThrow();
        return switch (PersonFields.read(form::value, people, id)) {
            case Result.Success<PersonInput, List<Violation>>(var input) ->
                    ok(people.update(id, person -> person.with(input)).map(person -> row(person, null))
                            .orElse(""));
            case Result.Failure<PersonInput, List<Violation>>(var errors) ->
                    ok(people.get(id).map(person -> PersonViews.editRow(person, uses(person),
                            PersonFields.values(form::value), errors).render()).orElse(""));
        };
    }

    private HTTPResponse toggle(WebRequest request) {
        return ok(people.update(pathId(request), Person::toggled).map(person -> row(person, null))
                .orElse(""));
    }

    /** Deletes a person without entries; one with entries stays, with the reason, swapped in at once. */
    private HTTPResponse delete(WebRequest request) {
        val person = people.get(pathId(request));
        if (person.isPresent() && uses(person.get()) > 0) {
            val reason = "Em uso por " + uses(person.get()) + " lançamento(s): desative-a.";
            return HtmxResponse.of(ok(row(person.get(), reason))).reswap(HxSwap.OUTER_HTML).response();
        }

        person.ifPresent(found -> people.remove(found.id()));
        return ok("");
    }

    private String row(Person person, @Nullable String error) {
        return PersonViews.personRow(person, uses(person), error).render();
    }

    private long uses(Person person) {
        return entries.ofPerson(person.id());
    }

    private HTTPResponse page(WebRequest request, Map<String, String> form, List<Violation> errors) {
        return ok(Layout.page(Screen.PEOPLE, PersonViews.screen(people, entries, form, errors)));
    }
}
