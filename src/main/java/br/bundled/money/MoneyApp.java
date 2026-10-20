package br.bundled.money;

import br.Application;
import br.bundled.money.web.WebModule;
import br.commons.Logger;
import br.commons.logger.LogLevel;
import br.commons.module.Module;

public class MoneyApp extends Application {

    static void main() {
        Logger.level(LogLevel.VERBOSE);
        new MoneyApp().run();
    }

    public MoneyApp() {
        attach(Module.of(new WebModule()));
    }
}
