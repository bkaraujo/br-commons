package br.commons.module;

import br.commons.Logger;
import br.commons.Meta;
import br.commons.Result;
import br.commons.business.BusinessError;
import br.commons.chrono.Time;
import lombok.val;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Tree of {@link Lifecycle}s: dependencies initialize in order, then the own lifecycle; {@link #terminate()} undoes
 * only what initialized, in reverse order. The first initialize failure stops the rest; terminate goes on after a
 * failure and returns the first one.
 */
@NullMarked
public final class Module implements Lifecycle {

    private @Nullable Lifecycle lifecycle;
    private final List<Module> dependencies = new ArrayList<>();

    /** Dependencies whose initialize ran, even if failed: a module terminates only what it initialized itself */
    private final List<Module> initialized = new ArrayList<>();
    private boolean running;

    /** Module running {@code lifecycle} after its dependencies */
    public static Module of(Lifecycle lifecycle) {
        val module = new Module();
        module.lifecycle(lifecycle);
        return module;
    }

    public void attach(Module module) { dependencies.add(module); }
    public void detach(Module module) { dependencies.remove(module); }

    public void lifecycle(Lifecycle desired) { lifecycle = desired; }
    public @Nullable Lifecycle lifecycle() { return lifecycle; }

    /** Name of the wrapped lifecycle, or of the module itself when there is none */
    private String name() { return lifecycle != null ? Meta.fqn(lifecycle) : Meta.fqn(this); }

    @Override
    public Result<Void, BusinessError> initialize() {
        for (val module : dependencies) {
            val start = Time.millis();
            Logger.debug("Initializing %s", module.name());
            initialized.add(module);
            val result = module.initialize();
            if (result.isFailure()) { return result; }
            Logger.debug("Initialized in %s ms", Time.millis() - start);
        }

        if (lifecycle != null) {
            val result = lifecycle.initialize();
            if (result.isFailure()) { return result; }
            running = true;
        }
        return Result.success();
    }

    @Override
    public Result<Void, BusinessError> terminate() {
        Result<Void, BusinessError> first = Result.success();
        val current = lifecycle;
        if (running && current != null) {
            running = false;
            first = current.terminate();
        }

        for (val module : initialized.reversed()) {
            val start = Time.millis();
            Logger.debug("Terminating %s", module.name());
            val result = module.terminate();
            if (result.isFailure() && first.isSuccess()) { first = result; }
            Logger.debug("Terminated in %s ms", Time.millis() - start);
        }
        initialized.clear();

        return first;
    }

}
