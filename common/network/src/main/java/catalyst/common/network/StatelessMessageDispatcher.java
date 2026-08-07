package catalyst.common.network;

import lombok.extern.slf4j.Slf4j;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Asynchronously dispatches inbound requests onto virtual threads.
 * Prevents blocking database queries or heavy computations from stalling
 * Netty's EventLoop threads.
 */
@Slf4j
public class StatelessMessageDispatcher implements AutoCloseable {

    private final ObjectDispatcher syncDispatcher = new ObjectDispatcher();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public StatelessMessageDispatcher(Collection<PacketHandler<?>> handlers) {
        syncDispatcher.registerAll(handlers);
    }

    /**
     * Submits the request processing task to a virtual thread
     * and returns a CompletableFuture containing the response.
     */
    public CompletableFuture<Object> dispatchAsync(Object request) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        executor.submit(() -> {
            try {
                Object resp = syncDispatcher.dispatch(request);
                future.complete(resp);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    @Override
    public void close() {
        log.info("Shutting down StatelessMessageDispatcher virtual thread executor...");
        executor.shutdown();
    }
}
