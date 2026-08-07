package catalyst.server.lobby;

import catalyst.common.network.ObjectDispatcher;
import catalyst.common.dto.*;
import catalyst.server.lobby.properties.ServerProperties;
import catalyst.server.lobby.handler.LobbyHandler;
import catalyst.server.lobby.transport.QuicServerTransport;
import io.micronaut.runtime.Micronaut;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import catalyst.common.network.GatewayControlMessage;
import catalyst.common.network.ResponseCode;

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
        ObjectDispatcher dispatcher = new ObjectDispatcher();
        dispatcher.register(CharListRequest.class, lobbyHandler::handleList);
        dispatcher.register(CharCreateRequest.class, lobbyHandler::handleCreate);
        dispatcher.register(CharSelectRequest.class, lobbyHandler::handleSelect);
        dispatcher.register(CharDeleteRequest.class, lobbyHandler::handleDelete);
        dispatcher.register(PlayRequest.class, req -> {
            PlayResponse resp = lobbyHandler.handlePlay(req);
            if (resp.code() == ResponseCode.OK) {
                return new Object[] {
                    new GatewayControlMessage("play_success", resp.sessionId(), "DEFAULT"),
                    resp
                };
            }
            return resp;
        });

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
