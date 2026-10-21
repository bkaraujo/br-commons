package br.bundled.money;

import br.Application;
import br.bundled.money.business._1_application.ApplicationModule;
import br.bundled.money.persistence.PersistenceModule;
import br.bundled.money.web.WebModule;
import br.commons.module.Module;
import br.commons.persistence.inmemory.InMemoryStorage;
import lombok.val;

import java.time.Clock;
import java.time.ZoneId;

/**
 * The persistence (in memory for now), the use cases over it and the web. The web still runs on its own mock book
 * ({@code web/mock}) until it moves to the use cases.
 */
public class MoneyApp extends Application {

    static void main() {
        new MoneyApp().run();
    }

    public MoneyApp() {
        val persistence = new PersistenceModule(new InMemoryStorage());
        val application = Module.of(new ApplicationModule(persistence.accounts(), persistence.transactions(),
                persistence.balances(), persistence.books(), Clock.system(ZoneId.of("America/Sao_Paulo"))));
        application.attach(Module.of(persistence));
        attach(application);
        attach(Module.of(new WebModule()));
    }
}
