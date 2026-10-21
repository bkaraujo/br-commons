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

/** The persistence (in memory for now), the use cases over it and the web over the use cases. */
public class MoneyApp extends Application {

    static void main() {
        new MoneyApp().run();
    }

    public MoneyApp() {
        val persistence = new PersistenceModule(new InMemoryStorage());
        val app = new ApplicationModule(persistence.accounts(), persistence.transactions(), persistence.balances(),
                persistence.books(), Clock.system(ZoneId.of("America/Sao_Paulo")));
        val application = Module.of(app);
        application.attach(Module.of(persistence));
        val web = Module.of(new WebModule(app));
        web.attach(application);
        attach(web);
    }
}
