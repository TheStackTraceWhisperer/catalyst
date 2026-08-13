package catalyst.server.world;

import catalyst.common.concurrency.TaskScheduler;
import catalyst.server.world.network.ServerTransport;
import catalyst.server.world.properties.ServerProperties;
import catalyst.server.world.repository.SessionRepository;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class WorldServiceApplication {

    private final ServerTransport transport;
    private final SessionRepository sessions;
    private final ServerProperties props;
    private final TaskScheduler scheduler;

    public static void main(String[] args) {
        Micronaut.run(WorldServiceApplication.class, args);
    }

    @EventListener
    public void onStartup(StartupEvent event) {
        schedulePeriodicPruning();

        try {
            transport.start();
            log.info("World Service listening on UDP port {} (QUIC)", props.getPort());
        } catch (Exception e) {
            log.error("Failed to start World transport", e);
        }
    }

    @jakarta.annotation.PreDestroy
    public void onShutdown() {
        log.info("Stopping World Service transport...");
        transport.stop();
    }

    private void schedulePeriodicPruning() {
        scheduler.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(10000);
                    cleanupSessions();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Periodic pruning iteration failed", e);
                }
            }
            return null;
        });
    }

    private void cleanupSessions() {
        try {
            int removed = sessions.deleteStale(props.getSessionTimeoutSeconds());
            if (removed > 0) log.info("SESSION_CLEANUP removed={}", removed);
        } catch (SQLException e) {
            log.error("SESSION_CLEANUP_ERR", e);
        }
    }
}