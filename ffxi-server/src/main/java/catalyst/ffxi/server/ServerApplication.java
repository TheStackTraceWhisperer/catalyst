package catalyst.ffxi.server;

import catalyst.ffxi.server.config.ServerProperties;
import catalyst.ffxi.server.dispatch.MessageDispatcher;
import catalyst.ffxi.server.handler.LoginHandler;
import catalyst.ffxi.server.repository.SessionRepository;
import catalyst.ffxi.server.transport.QuicServerTransport;
import io.micronaut.runtime.Micronaut;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ServerApplication {

    private final QuicServerTransport transport;
    private final MessageDispatcher dispatcher;
    private final LoginHandler loginHandler;
    private final SessionRepository sessions;
    private final ServerProperties props;
    private final DataSource dataSource;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static void main(String[] args) {
        Micronaut.run(ServerApplication.class, args);
    }

    @EventListener
    public void onStartup(StartupEvent event) throws Exception {
        applyRuntimeDdl();
        loginHandler.bootstrapDevAccount();

        scheduler.scheduleAtFixedRate(this::cleanupSessions,    10, 10, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(loginHandler::cleanupTickets, 10, 10, TimeUnit.SECONDS);

        transport.setDispatcher(dispatcher::dispatch);
        transport.start();
        log.info("FFXI server listening on UDP port {} (QUIC)", props.getPort());
        transport.awaitShutdown();
        scheduler.shutdownNow();
    }

    /** Idempotent runtime DDL for column additions not in Docker init SQL */
    private void applyRuntimeDdl() {
        String[] stmts = {
            "ALTER TABLE characters ADD COLUMN IF NOT EXISTS size SMALLINT NOT NULL DEFAULT 1",
            "ALTER TABLE characters ADD COLUMN IF NOT EXISTS main_job SMALLINT NOT NULL DEFAULT 1",
            "ALTER TABLE characters ADD COLUMN IF NOT EXISTS nation SMALLINT NOT NULL DEFAULT 0",
            "ALTER TABLE accounts_sessions ADD COLUMN IF NOT EXISTS zone_id INT NOT NULL DEFAULT 0"
        };
        try (Connection c = dataSource.getConnection()) {
            for (String ddl : stmts) {
                try (var s = c.prepareStatement(ddl)) {
                    s.execute();
                } catch (SQLException e) {
                    log.debug("DDL skipped ({}): {}", e.getSQLState(), ddl);
                }
            }
            log.info("Runtime DDL applied");
        } catch (SQLException e) {
            log.warn("Runtime DDL failed — schema may be incomplete: {}", e.getMessage());
        }
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
