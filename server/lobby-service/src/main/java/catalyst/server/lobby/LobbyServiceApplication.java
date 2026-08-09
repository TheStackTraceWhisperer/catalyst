package catalyst.server.lobby;

import catalyst.server.common.network.PacketHandler;
import catalyst.server.lobby.properties.ServerProperties;
import catalyst.server.lobby.transport.QuicServerTransport;
import io.micronaut.runtime.Micronaut;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

import catalyst.server.common.network.StatelessMessageDispatcher;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class LobbyServiceApplication {

    private final QuicServerTransport transport;
    private final List<PacketHandler<?>> packetHandlers;
    private final ServerProperties props;

    private StatelessMessageDispatcher dispatcher;

    public static void main(String[] args) {
        Micronaut.build(args)
            .classes(LobbyServiceApplication.class)
            .packages("catalyst")
            .run();
    }

    @EventListener
    public void onStartup(StartupEvent event) throws Exception {
        dispatcher = new StatelessMessageDispatcher(packetHandlers);
        transport.setDispatcher(dispatcher::dispatchAsync);
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
        if (dispatcher != null) {
            dispatcher.close();
        }
    }
}
