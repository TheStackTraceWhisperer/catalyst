package catalyst.server.common.network;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface WorldRequestDispatcher {
    CompletableFuture<Object> dispatch(Object payload, String sessionId);
}
