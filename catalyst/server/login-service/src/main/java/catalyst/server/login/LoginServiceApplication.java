package catalyst.server.login;

import catalyst.server.login.properties.ServerProperties;
import catalyst.server.login.service.AccountAuthenticationService;
import catalyst.server.login.network.ServerTransport;
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

    private final ServerTransport transport;
    private final AccountAuthenticationService authService;
    private final ServerProperties props;
    private final DataSource dataSource;

    public static void main(String[] args) {
        Micronaut.run(LoginServiceApplication.class, args);
    }

    @EventListener
    public void onStartup(StartupEvent event) throws Exception {
        applyRuntimeDdl();

        // Bootstrap default test user
        authService.bootstrapDevAccount("dev", "dev");

        try {
            transport.start();
            log.info("Login Service listening on UDP port {} (QUIC)", props.getPort());
        } catch (Exception e) {
            log.error("Failed to start Login transport", e);
        }
    }

    @jakarta.annotation.PreDestroy
    public void onShutdown() {
        log.info("Stopping Login Service transport...");
        transport.stop();
    }

    private void applyRuntimeDdl() {
        String[] stmts = {
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