package br.commons.examples.tasks.htmx;

import br.commons.examples.tasks.shared.Task;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * What the list shows: a tab ({@code all}, {@code open}, {@code done}) and a search. Kept in the URL
 * ({@code /?filter=open&q=pão}), so a tab goes to the history and a reload shows the same list.
 */
@NullMarked
record TaskView(String filter, String query) {

    static final List<String> FILTERS = List.of("all", "open", "done");

    /** From the request; the tab {@code all} and no search when absent or invalid. */
    static TaskView of(Function<String, @Nullable String> parameter) {
        val filter = parameter.apply("filter");
        val query = parameter.apply("q");
        return new TaskView(filter != null && FILTERS.contains(filter) ? filter : "all", query == null ? "" : query.strip());
    }

    boolean shows(Task task) {
        val inTab = "all".equals(filter) || "done".equals(filter) == task.done();
        return inTab && task.title().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    /** {@code /?filter=open}: the search is not kept in the history, only the tab. */
    String url() {
        return url(filter);
    }

    static String url(String filter) {
        return "/?filter=" + filter;
    }
}
