package catalyst.server.lobby;

import catalyst.common.network.ObjectDispatcher;
import catalyst.common.network.PacketHandler;
import catalyst.server.lobby.properties.ServerProperties;
import catalyst.server.lobby.transport.QuicServerTransport;
import io.micronaut.runtime.Micronaut;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class LobbyServiceApplication {

    private final QuicServerTransport transport;
    private final List<PacketHandler<?>> packetHandlers;
    private final ServerProperties props;

    public static void main(String[] args) {
        Micronaut.run(LobbyServiceApplication.class, args);
    }

    @EventListener
    public void onStartup(StartupEvent event) throws Exception {
        ObjectDispatcher dispatcher = new ObjectDispatcher();
        dispatcher.registerAll(packetHandlers);

        transport.setDispatcher(dispatcher::dispatch);
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
