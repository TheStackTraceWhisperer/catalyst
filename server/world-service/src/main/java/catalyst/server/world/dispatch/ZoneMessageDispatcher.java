package catalyst.server.world.dispatch;

import catalyst.common.network.PacketHandler;
import io.micronaut.context.BeanProvider;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Handles World state requests sequentially via a 10Hz (100ms) tick loop.
 * Ensures all entity coordinate updates, session validations, and interactions
 * execute single-threaded per zone, avoiding database and state race conditions
 * while keeping Netty's EventLoop completely non-blocking.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class ZoneMessageDispatcher implements AutoCloseable {

    private final BeanProvider<PacketHandler<?>> availableHandlers;
    private final Map<Class<?>, PacketHandler<?>> handlerRegistry = new HashMap<>();
    private final Queue<QueuedCommand> zoneQueue = new ConcurrentLinkedQueue<>();
    
    private static final int TICK_RATE_MS = 100;
    private Thread tickThread;
    private volatile boolean running = true;

    private record QueuedCommand(Object payload, CompletableFuture<Object> future) {}

    @PostConstruct
    public void initialize() {
        for (PacketHandler<?> handler : availableHandlers) {
            handlerRegistry.put(handler.getPacketType(), handler);
            log.info("Registered Zone Handler for: {}", handler.getPacketType().getSimpleName());
        }
        startZoneTickLoop();
    }

    /**
     * Enqueues the request payload and returns a CompletableFuture
     * to be completed during the next tick processing.
     */
    public CompletableFuture<Object> dispatchAsync(Object payload) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        zoneQueue.offer(new QueuedCommand(payload, future));
        return future;
    }

    private void startZoneTickLoop() {
        tickThread = Thread.ofVirtual().name("zone-tick-loop").start(() -> {
            while (running && !Thread.currentThread().isInterrupted()) {
                long tickStart = System.currentTimeMillis();

                try {
                    // 1. Process all pending network events sequentially
                    processNetworkQueue();

                    // 2. Process Game Loop tick simulation (AI, ticks, physics)
                    // updateGameState();
                } catch (Exception e) {
                    log.error("Unhandled error in Zone Tick Loop", e);
                }

                // 3. Sleep until next tick interval
                long elapsed = System.currentTimeMillis() - tickStart;
                long sleepTime = TICK_RATE_MS - elapsed;
                
                if (sleepTime > 0) {
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    log.warn("World server tick lag detected! Tick took {}ms", elapsed);
                }
            }
        });
    }

    private void processNetworkQueue() {
        QueuedCommand cmd;
        while ((cmd = zoneQueue.poll()) != null) {
            Object payload = cmd.payload();
            CompletableFuture<Object> future = cmd.future();
            PacketHandler<?> handler = handlerRegistry.get(payload.getClass());
            
            if (handler != null) {
                try {
                    Object response = invokeHandler(handler, payload);
                    future.complete(response);
                } catch (Throwable t) {
                    log.error("Error processing packet: {}", payload.getClass().getSimpleName(), t);
                    future.completeExceptionally(t);
                }
            } else {
                log.warn("Dropped packet! No handler registered for: {}", payload.getClass().getName());
                future.complete(null);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Object invokeHandler(PacketHandler<T> handler, Object payload) throws Exception {
        return handler.handle((T) payload);
    }

    @Override
    public void close() {
        log.info("Stopping ZoneMessageDispatcher tick loop...");
        running = false;
        if (tickThread != null) {
            tickThread.interrupt();
        }
    }
}
