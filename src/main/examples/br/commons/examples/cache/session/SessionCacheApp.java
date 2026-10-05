package br.commons.examples.cache.session;

import br.commons.Logger;
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
public final class SessionCacheApp {

    private SessionCacheApp() {}

    public static void main(String[] args) {
        val discontinued = new CartItem(UUID.randomUUID(), 1, 4_990);
        val database = Map.of(
                "ana", List.of(discontinued, new CartItem(UUID.randomUUID(), 2, 1_500)),
                "caio", List.of(discontinued));

        val carts = new CartCache(userId -> database.getOrDefault(userId, List.of()));
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
    }
}
