package catalyst.gateway.transport;

import catalyst.common.network.GatewayControlMessage;
import catalyst.common.network.GatewayFrame;
import catalyst.common.network.ServiceType;
import catalyst.gateway.properties.GatewayProperties;
import catalyst.gateway.proxy.BackendClient;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.AttributeKey;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class RequestHandler extends ChannelInboundHandlerAdapter {
    public static final AttributeKey<SecurityState> STATE_KEY = AttributeKey.valueOf("gateway.state");
    public static final AttributeKey<BackendClient> WORLD_CLIENT_KEY = AttributeKey.valueOf("gateway.worldClient");
    public static final AttributeKey<String> SESSION_ID_KEY = AttributeKey.valueOf("gateway.sessionId");

    private final GatewayProperties props;
    private final Map<ServiceType, BackendClient> clients;
    private final Map<BackendAddress, BackendClient> dynamicWorldClients;

    public RequestHandler(GatewayProperties props, Map<ServiceType, BackendClient> clients, Map<BackendAddress, BackendClient> dynamicWorldClients) {
        this.props = props;
        this.clients = clients;
        this.dynamicWorldClients = dynamicWorldClients;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof GatewayFrame requestFrame)) {
            log.warn("Expected GatewayFrame in RequestHandler, got {}", msg.getClass().getName());
            ctx.close();
            return;
        }

        // Get connection state from parent channel (since QuicStreamChannel's parent is the QuicChannel connection)
        Channel parentChannel = ctx.channel().parent();
        SecurityState state = parentChannel.attr(STATE_KEY).get();
        if (state == null) {
            state = SecurityState.UNAUTHENTICATED;
            parentChannel.attr(STATE_KEY).set(state);
        }

        BackendClient client = resolveTargetClient(parentChannel, state, requestFrame);
        if (client == null) {
            log.warn("No target client resolved for request flag={} in state={}", requestFrame.flag(), state);
            ctx.writeAndFlush(new GatewayFrame(ServiceType.CONTROL, "", new byte[0]))
                .addListener(f -> ((QuicStreamChannel) ctx.channel()).shutdownOutput());
            return;
        }

        // Inject verified sessionId if routing to a SESSION_BOUND policy endpoint
        GatewayFrame frameToSend = requestFrame;
        GatewayProperties.BackendConfig config = props.getBackendByFlag(requestFrame.flag().flag());
        if (config != null && "SESSION_BOUND".equals(config.policy())) {
            String sessionId = parentChannel.attr(SESSION_ID_KEY).get();
            if (sessionId != null) {
                frameToSend = new GatewayFrame(requestFrame.flag(), sessionId, requestFrame.payload());
            }
        }

        // Forward request asynchronously to backend client (Zero Blocking on EventLoop)
        client.requestAsync(frameToSend, controlMsg -> {
            handleStateTransitions(parentChannel, controlMsg);
        })
            .thenAccept(responseFrame -> {
                if (responseFrame == null) {
                    sendBackendUnavailable(ctx);
                    return;
                }
                ctx.writeAndFlush(responseFrame).addListener(f -> {
                    if (!f.isSuccess()) {
                        log.warn("Failed to write gateway response", f.cause());
                    }
                    ((QuicStreamChannel) ctx.channel()).shutdownOutput();
                });
            })
            .exceptionally(throwable -> {
                log.error("Failed to forward request asynchronously to backend", throwable);
                sendBackendUnavailable(ctx);
                return null;
            });
    }

    private void sendBackendUnavailable(ChannelHandlerContext ctx) {
        ctx.writeAndFlush(new GatewayFrame(ServiceType.CONTROL, "", new byte[0]))
            .addListener(f -> ((QuicStreamChannel) ctx.channel()).shutdownOutput());
    }

    private BackendClient resolveTargetClient(Channel parentChannel, SecurityState state, GatewayFrame requestFrame) {
        ServiceType type = requestFrame.flag();
        if (type == null) {
            log.warn("No ServiceType found for flag: {}", requestFrame.flag());
            return null;
        }

        GatewayProperties.BackendConfig config = props.getBackendByFlag(type.flag());
        if (config == null) {
            log.warn("No backend configured for service: {}", type);
            return null;
        }

        // Parse policy state requirement
        SecurityState requiredState;
        try {
            requiredState = SecurityState.valueOf(config.policy());
        } catch (IllegalArgumentException e) {
            log.error("Invalid security policy '{}' configured for service {}", config.policy(), type);
            return null;
        }

        // Enforce the 1:1 security state comparison
        if (state.level() < requiredState.level()) {
            log.warn("Access denied: request to service {} requires state >= {}, but client is in state {}", 
                type, requiredState, state);
            return null;
        }

        if (requiredState == SecurityState.SESSION_BOUND) {
            BackendClient world = parentChannel.attr(WORLD_CLIENT_KEY).get();
            return world;
        }

        return clients.get(type);
    }

    private void handleStateTransitions(Channel parentChannel, GatewayControlMessage gcm) {
        String command = gcm.command();
        if (command == null) {
            return;
        }

        switch (command) {
            case "auth_success" -> {
                log.info("Client authenticated, transitioning to AUTHENTICATED");
                parentChannel.attr(STATE_KEY).set(SecurityState.AUTHENTICATED);
            }
            case "play_success" -> {
                String worldAddr = gcm.worldAddress();
                String sessionId = gcm.sessionId();
                log.info("Client play success, sessionId={}, transitioning to SESSION_BOUND", sessionId);

                parentChannel.attr(STATE_KEY).set(SecurityState.SESSION_BOUND);
                parentChannel.attr(SESSION_ID_KEY).set(sessionId);

                if (worldAddr == null || worldAddr.isBlank() || "DEFAULT".equals(worldAddr)) {
                    log.error("play_success control message has no valid worldAddress specified!");
                    parentChannel.close();
                    return;
                }

                String[] parts = worldAddr.split(":", 2);
                if (parts.length != 2) {
                    log.error("Invalid worldAddress format specified: '{}'", worldAddr);
                    parentChannel.close();
                    return;
                }

                BackendAddress address;
                try {
                    address = new BackendAddress(parts[0], Integer.parseInt(parts[1]));
                } catch (NumberFormatException e) {
                    log.error("Invalid port in worldAddress '{}'", worldAddr);
                    parentChannel.close();
                    return;
                }

                // Resolve the specific world server client dynamically
                BackendClient targetClient = dynamicWorldClients.computeIfAbsent(address, key -> {
                    log.info("Opening new persistent internal connection to dynamic world backend: {}", key);
                    return new BackendClient(key.host(), key.port());
                });
                parentChannel.attr(WORLD_CLIENT_KEY).set(targetClient);
            }
            case "logout_success" -> {
                log.info("Client logged out, transitioning to AUTHENTICATED");
                parentChannel.attr(STATE_KEY).set(SecurityState.AUTHENTICATED);
                parentChannel.attr(WORLD_CLIENT_KEY).set(null);
                parentChannel.attr(SESSION_ID_KEY).set(null);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("Gateway QUIC stream error", cause);
        ctx.close();
    }
}
