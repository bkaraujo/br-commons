package br.bundled.money;

import br.Application;
import br.commons.Logger;
import br.commons.logger.LogLevel;
import br.commons.module.Module;
import br.bundled.money.web.WebModule;

public class MoneyApp extends Application {

    static void main() {
        Logger.level(LogLevel.VERBOSE);
        new MoneyApp().run();
    }

    public MoneyApp() {
        attach(Module.of(new WebModule()));
    }
}
