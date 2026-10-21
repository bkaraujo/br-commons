package br.bundled.money;

import br.Application;
import br.bundled.money.business._1_application.ApplicationModule;
import br.bundled.money.persistence.PersistenceModule;
import br.bundled.money.web.WebModule;
import br.commons.Logger;
import br.commons.cdi.Context;
import br.commons.logger.LogLevel;
import br.commons.module.Module;
import br.commons.persistence.Storage;
import br.commons.persistence.inmemory.InMemoryStorage;
import lombok.val;

import java.time.Clock;
import java.time.ZoneId;

/**
 * The persistence (in memory for now), the use cases over it and the web over the use cases, wired through the
 * {@link Context}: what each layer needs is registered before the layer is created.
 */
public class MoneyApp extends Application {

    static void main() {
        Logger.level(LogLevel.VERBOSE);
        new MoneyApp().run();
    }

    public MoneyApp() {
        Context.set(Storage.class, InMemoryStorage::new);
        Context.set(Clock.class, () -> Clock.system(ZoneId.of("America/Sao_Paulo")));
        Context.set(PersistenceModule.class);
        Context.set(ApplicationModule.class);
        val application = Module.of(Context.get(ApplicationModule.class));
        application.attach(Module.of(Context.get(PersistenceModule.class)));
        val web = Module.of(new WebModule());
        web.attach(application);
        attach(web);
    }
}
