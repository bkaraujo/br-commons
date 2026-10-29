package br.bundled.judas;

import br.Application;
import br.bundled.judas._1_application.ApplicationModule;
import br.bundled.judas._2_infrastructure.persistence.PersistenceModule;
import br.bundled.judas._2_infrastructure.web.WebModule;
import br.commons.cdi.Context;
import org.jspecify.annotations.NullMarked;

import java.time.Clock;
import java.time.ZoneId;

/** The composition of br.judas: the shared infrastructure in the {@link Context} and the modules, in startup order (ARCH-03). */
@NullMarked
public final class Judas extends Application {

    static void main() { new Judas().run(); }

    public Judas() {
        Context.set(Clock.class, () -> Clock.system(ZoneId.of("America/Sao_Paulo")));

        attach(new PersistenceModule());
        attach(new ApplicationModule());
        attach(new WebModule());
    }
}
