package br.commons.examples.cache.session;

import br.commons.cache.Cache;
import br.commons.Result;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.CompletableFuture;

/**
 * The session concept lives here, not in the cache: login loads the user's partition, logout unloads it (native
 * memory is not garbage collected, so a session that never logs out leaks).
 */
@NullMarked
public final class UserSessions {
    private final Cache<CartItem> carts;

    public UserSessions(Cache<CartItem> carts) {
        this.carts = carts;
    }

    public CompletableFuture<Result<Void, String>> login(String userId) {
        return carts.load(userId);
    }

    public CompletableFuture<Result<Void, String>> logout(String userId) {
        return carts.unload(userId);
    }
}
