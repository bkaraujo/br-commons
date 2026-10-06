package br.commons.examples.tracker.htmx.project;

import br.commons.examples.tracker.htmx.ui.Status;
import br.commons.examples.tracker.htmx.ui.Ui;
import br.commons.examples.tracker.shared.entry.EntryStore;
import br.commons.examples.tracker.shared.project.Project;
import br.commons.examples.tracker.shared.project.ProjectFields;
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

/** The projects screen: the new project form and the list, edited, (de)activated and deleted row by row. */
@NullMarked
final class ProjectViews {

    private ProjectViews() {}

    static Node screen(Registry<Project> registry, EntryStore entries, Map<String, String> form, List<Violation> errors) {
        val projects = registry.all();
        return section(
                id("projects"), cls("registry"),
                h2(text(message("Projetos"))),
                projectForm(form, errors),
                table(
                        cls("records"),
                        thead(
                                tr(th(text(message("Nome"))), th(text(message("Cliente"))), th(text(message("Situação"))),
                                th(cls("num"), text(message("Lançamentos"))), th(ariaLabel(message("Ações"))))
                        ),
                        tbody(
                                each(projects, project -> projectRow(project, entries.ofProject(project.id()), null)),
                                when(projects.isEmpty(), () -> tr(cls("empty"),
                                        td(colspan(5), text(message("Nenhum projeto.")))))
                        )
                )
        );
    }

    /** Saving answers with the whole screen (the list is sorted by name); errors come back into the form. */
    static Node projectForm(Map<String, String> values, List<Violation> errors) {
        return form(
                id("project-form"), cls("record-form"), method(FormMethod.POST), action("/projects"),
                hxPost("/projects"), hxTarget("#projects"), hxSwap(HxSwap.OUTER_HTML),
                labeled(ProjectFields.NAME, "Nome", values, errors),
                labeled(ProjectFields.CLIENT, "Cliente", values, errors),
                button(type(ButtonType.SUBMIT), text(message("Cadastrar")))
        );
    }

    private static Node labeled(Field field, String label, Map<String, String> values, List<Violation> errors) {
        val fieldId = "project-" + field.name();
        return div(
                cls("field"),
                label(htmlFor(fieldId), text(message(label))),
                input(DIALECT, field, values.getOrDefault(field.name(), ""), id(fieldId)),
                Ui.errorOf(field, errors, ProjectFields::message)
        );
    }

    /** A row; {@code error} explains a refused delete. */
    static Node projectRow(Project project, long entries, @Nullable String error) {
        val path = "/projects/" + project.id();
        return tr(
                id("project-" + project.id()), cls(project.active() ? "" : "inactive"),
                td(text(Text.data(project.name()))),
                td(text(Text.data(project.client()))),
                td(Status.of(project.active())),
                td(cls("num"), text(Text.data(String.valueOf(entries)))),
                td(cls("actions"),
                        button(cls("link"), hxGet(path + "/edit"), hxTarget("closest tr"), hxSwap(HxSwap.OUTER_HTML),
                                text(message("Editar"))),
                        button(cls("link"), hxPost(path + "/active"), hxTarget("closest tr"), hxSwap(HxSwap.OUTER_HTML),
                                text(message(project.active() ? "Desativar" : "Ativar"))),
                        button(cls("link danger"), hxDelete(path), hxTarget("closest tr"), hxSwap(Ui.FADE_OUT),
                                hxConfirm(message("Excluir o projeto \"" + project.name() + "\"?")),
                                text(message("Excluir"))),
                        when(error != null, () -> small(cls("error"), role("alert"), text(message(String.valueOf(error)))))
                )
        );
    }

    /** The row in edit mode; the save button sends its inputs with {@code hx-include="closest tr"}. */
    static Node editRow(Project project, long entries, Map<String, String> values, List<Violation> errors) {
        val path = "/projects/" + project.id();
        return tr(
                id("project-" + project.id()), cls("editing"),
                td(inline(ProjectFields.NAME, "Nome", values, errors)),
                td(inline(ProjectFields.CLIENT, "Cliente", values, errors)),
                td(Status.of(project.active())),
                td(cls("num"), text(Text.data(String.valueOf(entries)))),
                td(cls("actions"),
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
                Ui.errorOf(field, errors, ProjectFields::message)
        );
    }
}
