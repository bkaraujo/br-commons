package br.commons.examples.cache.session;

import br.Application;
import br.commons.Logger;
import br.commons.Result;
import br.commons.module.Lifecycle;
import br.commons.business.BusinessError;
import br.commons.cache.CacheServer;
import br.commons.module.Module;
import lombok.val;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Example: two users log in, each sees only their own cart, one adds an item, a product leaves the catalog for
 * everyone, both log out.
 *
 * <p>Run with {@code mvn -Pexamples compile exec:java -Dexamples.main=br.commons.examples.cache.session.SessionCacheApp}.
 */
@NullMarked
public final class SessionCacheApp extends Application implements Lifecycle {

    private final CacheServer cacheServer = new CacheServer();

    /** The scenario module depends on the cache server: initialized after it, terminated before it. */
    private SessionCacheApp() {
        val scenario = Module.of(this);
        scenario.attach(Module.of(cacheServer));
        attach(scenario);
    }

    /** The cache server thread keeps the process alive: once the scenario ran, exit, which terminates the modules. */
    public static void main(String[] args) {
        new SessionCacheApp().run();
        System.exit(0);
    }

    /** The whole scenario runs on start, after the cache server. */
    @Override
    public Result<Void, BusinessError> initialize() {
        val discontinued = new CartItem(UUID.randomUUID(), 1, 4_990);
        val database = Map.of(
                "ana", List.of(discontinued, new CartItem(UUID.randomUUID(), 2, 1_500)),
                "caio", List.of(discontinued));

        val carts = cacheServer.register(new CartRegion(userId -> database.getOrDefault(userId, List.of())));
        val sessions = new UserSessions(carts);

        sessions.login("ana").join();
        sessions.login("caio").join();
        Logger.info("ana: %s", carts.list("ana").get());
        Logger.info("caio: %s", carts.list("caio").get());

        // Write-through: the source of truth was updated elsewhere, the session cache follows.
        carts.upsert("caio", new CartItem(UUID.randomUUID(), 3, 990)).join();
        Logger.info("caio after add: %s", carts.list("caio").get());

        // A product removed from the catalog leaves every open session at once.
        carts.evictEverywhere(discontinued.id()).join();
        Logger.info("ana after discontinue: %s", carts.list("ana").get());
        Logger.info("caio after discontinue: %s", carts.list("caio").get());

        sessions.logout("ana").join();
        sessions.logout("caio").join();
        Logger.info("ana after logout: %s", carts.list("ana").get());
        return Result.success();
    }
}
