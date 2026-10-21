package br.bundled.money;

import br.Application;
import br.bundled.money.business._1_application.ApplicationModule;
import br.bundled.money.persistence.PersistenceModule;
import br.bundled.money.web.WebModule;
import br.commons.Logger;
import br.commons.cdi.Context;
import br.commons.logger.LogLevel;
import br.commons.persistence.Storage;
import br.commons.persistence.inmemory.InMemoryStorage;

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

        attach(Context.tryGet(PersistenceModule.class));
        attach(Context.tryGet(ApplicationModule.class));
        attach(Context.tryGet(WebModule.class));
    }
}
