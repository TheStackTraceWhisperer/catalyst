package catalyst.server.world.dispatch;

import catalyst.common.network.MessageFrame;
import catalyst.server.world.handler.WorldHandler;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class MessageDispatcher {

    private final WorldHandler world;

    public MessageFrame dispatch(MessageFrame req) {
        log.debug("DISPATCH type={} version={}", req.type(), req.protocolVersion());
        if (req.protocolVersion() < MessageFrame.CURRENT_VERSION) {
            log.debug("PROTOCOL_VERSION_MISMATCH client_v={} server_v={} type={}",
                req.protocolVersion(), MessageFrame.CURRENT_VERSION, req.type());
        }
        return switch (req.type()) {
            case "PLAY"        -> world.handlePlay(req);
            case "PING"        -> world.handlePing(req);
            case "LOGOUT"      -> world.handleLogout(req);
            default            -> MessageFrame.builder("ERROR")
                                    .put("code", "UNKNOWN_REQUEST")
                                    .put("message", "Unsupported by World Service: " + req.type())
                                    .build();
        };
    }
}
