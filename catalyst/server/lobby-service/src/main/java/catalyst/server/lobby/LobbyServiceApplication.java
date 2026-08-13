package catalyst.server.lobby;

import catalyst.server.lobby.properties.ServerProperties;
import catalyst.server.lobby.network.ServerTransport;
import io.micronaut.runtime.Micronaut;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class LobbyServiceApplication {

    private final ServerTransport transport;
    private final ServerProperties props;

    public static void main(String[] args) {
        Micronaut.run(LobbyServiceApplication.class, args);
    }

    @EventListener
    public void onStartup(StartupEvent event) throws Exception {
        try {
            transport.start();
            log.info("Lobby Service listening on UDP port {} (QUIC)", props.getPort());
        } catch (Exception e) {
            log.error("Failed to start Lobby transport", e);
        }
    }

    @jakarta.annotation.PreDestroy
    public void onShutdown() {
        log.info("Stopping Lobby Service transport...");
        transport.stop();
    }
}