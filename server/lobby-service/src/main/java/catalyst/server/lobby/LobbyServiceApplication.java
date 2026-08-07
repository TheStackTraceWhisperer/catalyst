package catalyst.server.lobby;

import catalyst.common.network.MessageFrame;
import catalyst.server.lobby.properties.ServerProperties;
import catalyst.server.lobby.handler.LobbyHandler;
import catalyst.server.lobby.transport.QuicServerTransport;
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

    private final QuicServerTransport transport;
    private final LobbyHandler lobbyHandler;
    private final ServerProperties props;

    public static void main(String[] args) {
        Micronaut.run(LobbyServiceApplication.class, args);
    }

    @EventListener
    public void onStartup(StartupEvent event) throws Exception {
        transport.setDispatcher(this::dispatch);
        Thread.ofVirtual().start(() -> {
            try {
                transport.start();
                log.info("Lobby Service listening on UDP port {} (QUIC)", props.getPort());
                transport.awaitShutdown();
            } catch (Exception e) {
                log.error("Failed to start Lobby transport", e);
            }
        });
    }

    private MessageFrame dispatch(MessageFrame req) {
        return switch (req.type()) {
            case "CHAR_LIST"   -> lobbyHandler.handleList(req);
            case "CHAR_CREATE" -> lobbyHandler.handleCreate(req);
            case "CHAR_SELECT" -> lobbyHandler.handleSelect(req);
            case "CHAR_DELETE" -> lobbyHandler.handleDelete(req);
            case "PLAY"        -> lobbyHandler.handlePlay(req);
            default            -> MessageFrame.builder("ERROR")
                                    .put("code", "UNKNOWN_REQUEST")
                                    .put("message", "Lobby Service unsupported: " + req.type())
                                    .build();
        };
    }
}
