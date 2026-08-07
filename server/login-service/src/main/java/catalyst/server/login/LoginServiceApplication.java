package catalyst.server.login;

import catalyst.common.concurrency.TaskScheduler;
import catalyst.common.network.ObjectDispatcher;
import catalyst.common.dto.LoginRequest;
import catalyst.server.login.properties.ServerProperties;
import catalyst.server.login.handler.LoginHandler;
import catalyst.server.login.transport.QuicServerTransport;
import io.micronaut.runtime.Micronaut;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class LoginServiceApplication {

    private final QuicServerTransport transport;
    private final LoginHandler loginHandler;
    private final ServerProperties props;
    private final DataSource dataSource;
    private final TaskScheduler scheduler;

    public static void main(String[] args) {
        Micronaut.run(LoginServiceApplication.class, args);
    }

    @EventListener
    public void onStartup(StartupEvent event) throws Exception {
        applyRuntimeDdl();
        loginHandler.bootstrapDevAccount();
        schedulePeriodicPruning();

        ObjectDispatcher dispatcher = new ObjectDispatcher();
        dispatcher.register(LoginRequest.class, loginHandler::handle);

        transport.setDispatcher(dispatcher::dispatch);
        Thread.ofVirtual().start(() -> {
            try {
                transport.start();
                log.info("Login Service listening on UDP port {} (QUIC)", props.getPort());
                transport.awaitShutdown();
            } catch (Exception e) {
                log.error("Failed to start Login transport", e);
            }
        });
    }

    private void schedulePeriodicPruning() {
        scheduler.submit(() -> {
            while (true) {
                try {
                    Thread.sleep(10000);
                    loginHandler.cleanupTickets();
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
            log.warn("Runtime DDL failed: {}", e.getMessage());
        }
    }
}
