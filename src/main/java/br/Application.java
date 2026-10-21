package br;

import br.commons.Logger;
import br.commons.business.BusinessError;
import br.commons.chrono.Time;
import br.commons.module.Lifecycle;
import br.commons.module.Module;
import lombok.val;
import org.jspecify.annotations.NullMarked;

@NullMarked
public abstract class Application {

    private final Module module = new Module();

    public void attach(Lifecycle module) { this.module.attach(Module.of(module)); }
    public void attach(Module module) { this.module.attach(module); }
    public void detach(Module module) { this.module.detach(module); }

    public void run() {
        val start = Time.millis();
        module
                .initialize()
                .ifFailure(BusinessError::toConsole);

        Runtime.getRuntime().addShutdownHook(new Thread(this::terminate, "application-shutdown"));
        Logger.info("Application started in %d ms", Time.millis() - start);
    }

    private void terminate() {
        module
                .terminate()
                .ifFailure(BusinessError::toConsole);
    }
}
