package br.commons.examples.tracker.htmx.project;

import br.commons.Result;
import br.commons.examples.tracker.htmx.ui.Layout;
import br.commons.examples.tracker.htmx.ui.Screen;
import br.commons.examples.tracker.htmx.ui.Ui;
import br.commons.examples.tracker.shared.entry.EntryStore;
import br.commons.examples.tracker.shared.project.Project;
import br.commons.examples.tracker.shared.project.ProjectFields;
import br.commons.examples.tracker.shared.project.ProjectInput;
import br.commons.examples.tracker.shared.registry.Registry;
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
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static br.commons.examples.tracker.htmx.ui.Ui.ok;
import static br.commons.examples.tracker.htmx.ui.Ui.pathId;

/** Routes of the projects register. A project with entries cannot be deleted: the row says so, deactivate it. */
@NullMarked
public final class ProjectRoutes implements WebResource {

    private final Registry<Project> projects;
    private final EntryStore entries;

    public ProjectRoutes(Registry<Project> projects, EntryStore entries) {
        this.projects = projects;
        this.entries = entries;
    }

    @Override
    public void routes(Router router) {
        router.get("/projects", MediaType.TEXT_HTML, String.class, this::list).getOrThrow();
        router.post("/projects", MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, this::create).getOrThrow();
        router.get("/projects/{id}", MediaType.TEXT_HTML, String.class, this::show).getOrThrow();
        router.get("/projects/{id}/edit", MediaType.TEXT_HTML, String.class, this::edit).getOrThrow();
        router.put("/projects/{id}", MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, this::update).getOrThrow();
        router.post("/projects/{id}/active", MediaType.URL_ENCODED, MediaType.TEXT_HTML, Form.class, String.class, this::toggle).getOrThrow();
        router.delete("/projects/{id}", MediaType.TEXT_HTML, String.class, this::delete).getOrThrow();
    }

    private HTTPResponse list(WebRequest request) {
        if (Ui.partial(request)) return ok(ProjectViews.screen(projects, entries, Map.of(), List.of()).render());
        return page(request, Map.of(), List.of());
    }

    /** Valid: the whole screen with the new project in its place. Invalid: the form with its errors. */
    private HTTPResponse create(WebRequest request) {
        val form = request.body(Form.class).getOrThrow();
        val htmx = HtmxRequest.of(request).isHtmx();
        return switch (ProjectFields.read(form::value, projects, 0)) {
            case Result.Success<ProjectInput, List<Violation>>(var input) -> {
                projects.add(id -> Project.of(id, input));
                yield htmx ? ok(ProjectViews.screen(projects, entries, Map.of(), List.of()).render())
                        : WebResponse.redirect("/projects");
            }
            case Result.Failure<ProjectInput, List<Violation>>(var errors) -> htmx
                    ? HtmxResponse.of(ok(ProjectViews.projectForm(ProjectFields.values(form::value), errors).render()))
                            .retarget("#project-form").reswap(HxSwap.OUTER_HTML).response()
                    : page(request, ProjectFields.values(form::value), errors);
        };
    }

    private HTTPResponse show(WebRequest request) {
        return ok(projects.get(pathId(request)).map(project -> row(project, null)).orElse(""));
    }

    private HTTPResponse edit(WebRequest request) {
        return ok(projects.get(pathId(request))
                .map(project -> ProjectViews.editRow(project, uses(project), ProjectFields.values(project), List.of())
                        .render())
                .orElse(""));
    }

    private HTTPResponse update(WebRequest request) {
        val id = pathId(request);
        val form = request.body(Form.class).getOrThrow();
        return switch (ProjectFields.read(form::value, projects, id)) {
            case Result.Success<ProjectInput, List<Violation>>(var input) ->
                    ok(projects.update(id, project -> project.with(input)).map(project -> row(project, null))
                            .orElse(""));
            case Result.Failure<ProjectInput, List<Violation>>(var errors) ->
                    ok(projects.get(id).map(project -> ProjectViews.editRow(project, uses(project),
                            ProjectFields.values(form::value), errors).render()).orElse(""));
        };
    }

    private HTTPResponse toggle(WebRequest request) {
        return ok(projects.update(pathId(request), Project::toggled).map(project -> row(project, null))
                .orElse(""));
    }

    /** Deletes a project without entries; one with entries stays, with the reason, swapped in at once. */
    private HTTPResponse delete(WebRequest request) {
        val project = projects.get(pathId(request));
        if (project.isPresent() && uses(project.get()) > 0) {
            val reason = "Em uso por " + uses(project.get()) + " lançamento(s): desative-o.";
            return HtmxResponse.of(ok(row(project.get(), reason))).reswap(HxSwap.OUTER_HTML).response();
        }
        project.ifPresent(found -> projects.remove(found.id()));
        return ok("");
    }

    private String row(Project project, @Nullable String error) {
        return ProjectViews.projectRow(project, uses(project), error).render();
    }

    private long uses(Project project) {
        return entries.ofProject(project.id());
    }

    private HTTPResponse page(WebRequest request, Map<String, String> form, List<Violation> errors) {
        return ok(Layout.page(Screen.PROJECTS,
                ProjectViews.screen(projects, entries, form, errors)));
    }
}
