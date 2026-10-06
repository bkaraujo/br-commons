package br;

import br.commons.Logger;
import br.commons.Meta;
import br.commons.annotation.Lifecycle;
import br.commons.business.BusinessError;
import br.commons.chrono.Time;
import lombok.val;

import java.util.ArrayList;
import java.util.List;

/**
 * Initializes the {@link #modules()} in order and terminates the ones that started, in reverse order, when the
 * process ends: right after {@link #run()} when nothing keeps it alive, or on a signal (Ctrl+C) when a module runs a
 * thread of its own (e.g. {@code WebServer}).
 */
public abstract class Application {

    private final List<Lifecycle> lifecycles = new ArrayList<>();

    protected abstract List<Lifecycle> modules();

    public void run() {
        val start = Time.millis();
        for (val lifecycle : modules()) {
            Logger.debug("Initializing %s", Meta.fqn(lifecycle));
            val result = lifecycle.initialize();
            result.ifFailure(BusinessError::toConsole);
            if (result.isSuccess()) lifecycles.add(lifecycle);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(this::terminate, "application-shutdown"));
        Logger.info("Application started in %d ms", Time.millis() - start);
    }

    private void terminate() {
        for (val lifecycle : lifecycles.reversed()) {
            Logger.debug("Terminating %s", Meta.fqn(lifecycle));
            lifecycle.terminate().ifFailure(BusinessError::toConsole);
        }
    }
}
