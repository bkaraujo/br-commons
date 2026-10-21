package br.bundled.tracker.jquery.api;

import br.bundled.tracker.shared.entry.EntryStore;
import br.bundled.tracker.shared.person.Person;
import br.bundled.tracker.shared.person.PersonFields;
import br.bundled.tracker.shared.person.PersonInput;
import br.bundled.tracker.shared.project.Project;
import br.bundled.tracker.shared.project.ProjectFields;
import br.bundled.tracker.shared.project.ProjectInput;
import br.bundled.tracker.shared.registry.Registered;
import br.bundled.tracker.shared.registry.Registry;
import br.commons.Result;
import br.commons.lang.html.form.Violation;
import br.commons.protocol.http.MediaType;
import br.commons.protocol.http.message.HTTPResponse;
import br.commons.web.WebResponse;
import br.commons.web.router.Router;
import br.commons.web.server.WebRequest;
import br.commons.web.server.WebResource;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;

/**
 * The JSON API of a register (projects, people), the same for both:
 * <ul>
 *   <li>{@code GET /api/projects} — every item, by name, with how many entries use it;</li>
 *   <li>{@code POST /api/projects} {@code {"name": "…", "client": "…"}} — 201 with the item, or 422 with the errors;</li>
 *   <li>{@code PUT /api/projects/{id}} — the changed item, 422 or 404;</li>
 *   <li>{@code PATCH /api/projects/{id}} {@code {"active": false}} — (de)activates it, or 404;</li>
 *   <li>{@code DELETE /api/projects/{id}} — 204; 409 when entries use it (deactivate it instead); 404.</li>
 * </ul>
 */
@NullMarked
public final class RegisterApi<T extends Registered, I> implements WebResource {

    /** The checked values of the body, or the violations; {@code self} is the item being edited (0 for a new one). */
    @FunctionalInterface
    interface Reader<I> {
        Result<I, List<Violation>> read(Function<String, String> values, int self);
    }

    private final String path;
    private final Registry<T> registry;
    private final Reader<I> reader;
    private final BiFunction<Integer, I, T> create;
    private final BiFunction<T, I, T> change;
    private final UnaryOperator<T> toggle;
    private final ToLongFunction<T> uses;
    private final Function<Violation, String> messages;
    private final Function<T, Map<String, Object>> fields;

    @SuppressWarnings("ParameterNumber")
    private RegisterApi(String path, Registry<T> registry, Reader<I> reader, BiFunction<Integer, I, T> create,
                        BiFunction<T, I, T> change, UnaryOperator<T> toggle, ToLongFunction<T> uses,
                        Function<Violation, String> messages, Function<T, Map<String, Object>> fields) {
        this.path = path;
        this.registry = registry;
        this.reader = reader;
        this.create = create;
        this.change = change;
        this.toggle = toggle;
        this.uses = uses;
        this.messages = messages;
        this.fields = fields;
    }

    public static RegisterApi<Project, ProjectInput> projects(Registry<Project> projects, EntryStore entries) {
        return new RegisterApi<>("/api/projects", projects,
                (values, self) -> ProjectFields.read(values, projects, self), Project::of, Project::with,
                Project::toggled, project -> entries.ofProject(project.id()), ProjectFields::message,
                project -> Map.of("name", project.name(), "client", project.client()));
    }

    public static RegisterApi<Person, PersonInput> people(Registry<Person> people, EntryStore entries) {
        return new RegisterApi<>("/api/people", people,
                (values, self) -> PersonFields.read(values, people, self), Person::of, Person::with,
                Person::toggled, person -> entries.ofPerson(person.id()), PersonFields::message,
                person -> Map.of("name", person.name(), "email", person.email()));
    }

    @Override
    public void routes(Router router) {
        router.get(path, MediaType.APPLICATION_JSON, List.class,
                _ -> WebResponse.ok(registry.all().stream().map(this::view).toList())).getOrThrow();
        router.post(path, MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, Map.class, Object.class,
                this::create).getOrThrow();
        router.put(path + "/{id}", MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, Map.class, Object.class,
                this::update).getOrThrow();
        router.patch(path + "/{id}", MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, Map.class, Object.class,
                this::activate).getOrThrow();
        router.delete(path + "/{id}", MediaType.APPLICATION_JSON, Void.class, this::delete).getOrThrow();
    }

    private HTTPResponse create(WebRequest request) {
        return Json.withValues(request, values -> switch (reader.read(values, 0)) {
            case Result.Success<I, List<Violation>>(var input) ->
                    WebResponse.created(view(registry.add(id -> create.apply(id, input))));
            case Result.Failure<I, List<Violation>>(var errors) -> Json.rejected(errors, messages);
        });
    }

    private HTTPResponse update(WebRequest request) {
        val id = Json.id(request);
        if (registry.get(id).isEmpty()) return Json.notFound();
        return Json.withValues(request, values -> switch (reader.read(values, id)) {
            case Result.Success<I, List<Violation>>(var input) -> registry.update(id, item -> change.apply(item, input))
                    .map(item -> WebResponse.ok(view(item))).orElseGet(Json::notFound);
            case Result.Failure<I, List<Violation>>(var errors) -> Json.rejected(errors, messages);
        });
    }

    /** Sets the state the body asks for; already in it, nothing changes. */
    private HTTPResponse activate(WebRequest request) {
        return Json.withValues(request, values -> {
            val active = Boolean.parseBoolean(values.apply("active"));
            return registry.update(Json.id(request), item -> item.active() == active ? item : toggle.apply(item))
                    .map(item -> WebResponse.ok(view(item))).orElseGet(Json::notFound);
        });
    }

    private HTTPResponse delete(WebRequest request) {
        val item = registry.get(Json.id(request));
        if (item.isEmpty()) return Json.notFound();
        val count = uses.applyAsLong(item.get());
        if (count > 0) return Json.conflict("Em uso por " + count + " lançamento(s): desative-o.");
        registry.remove(item.get().id());
        return WebResponse.noContent();
    }

    /** {@code {"id": 1, "name": "…", …, "active": true, "entries": 3}}. */
    private Map<String, Object> view(T item) {
        val view = new LinkedHashMap<String, Object>();
        view.put("id", item.id());
        view.putAll(fields.apply(item));
        view.put("active", item.active());
        view.put("entries", uses.applyAsLong(item));
        return view;
    }
}
