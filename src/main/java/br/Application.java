package br;

import br.commons.Logger;
import br.commons.Meta;
import br.commons.annotation.Lifecycle;
import br.commons.business.BusinessError;
import br.commons.chrono.Time;
import lombok.val;

import java.util.ArrayList;
import java.util.List;

public abstract class Application {

    private final List<Lifecycle> lifecycles = new ArrayList<>();

    protected abstract List<Lifecycle> modules();

    public void run() {
        val start = Time.millis();
        for (val lifecycle : modules()) {
            Logger.debug("Initializing {}", Meta.fqn(lifecycle));
            val result = lifecycle.initialize();
            result.ifFailure(BusinessError::toConsole);
            if (result.isSuccess()) lifecycles.add(lifecycle);
        }

        Logger.info("Application started in {} ms", Time.millis() - start);

        for (val lifecycle : lifecycles.reversed()) {
            Logger.debug("Terminating {}", Meta.fqn(lifecycle));
            lifecycle.terminate().ifFailure(BusinessError::toConsole);
        }
    }
}
