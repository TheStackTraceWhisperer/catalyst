package catalyst.gateway.transport;

import catalyst.common.network.GatewayFrame;
import catalyst.gateway.properties.GatewayProperties;
import catalyst.gateway.proxy.QuicGatewayClient;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.AttributeKey;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public final class RequestHandler extends ChannelInboundHandlerAdapter {
    public static final AttributeKey<ConnectionState> STATE_KEY = AttributeKey.valueOf("gateway.state");
    public static final AttributeKey<QuicGatewayClient> WORLD_CLIENT_KEY = AttributeKey.valueOf("gateway.worldClient");

    private final GatewayProperties props;
    private final QuicGatewayClient loginClient;
    private final QuicGatewayClient lobbyClient;
    private final QuicGatewayClient worldClient;
    private final Map<BackendAddress, QuicGatewayClient> dynamicWorldClients;


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof GatewayFrame requestFrame)) {
            log.warn("Expected GatewayFrame in RequestHandler, got {}", msg.getClass().getName());
            ctx.close();
            return;
        }

        // Get connection state from parent channel (since QuicStreamChannel's parent is the QuicChannel connection)
        Channel parentChannel = ctx.channel().parent();
        ConnectionState state = parentChannel.attr(STATE_KEY).get();
        if (state == null) {
            state = ConnectionState.UNAUTHENTICATED;
            parentChannel.attr(STATE_KEY).set(state);
        }

        QuicGatewayClient client = resolveTargetClient(parentChannel, state, requestFrame);
        if (client == null) {
            log.warn("No target client resolved for request flag={} in state={}", requestFrame.flag(), state);
            ctx.writeAndFlush(new GatewayFrame(GatewayFrame.FLAG_CONTROL, "error=unauthorized_route", new byte[0]))
                .addListener(f -> ((QuicStreamChannel) ctx.channel()).shutdownOutput());
            return;
        }

        try {
            GatewayFrame responseFrame = client.request(requestFrame);
            handleStateTransitions(parentChannel, responseFrame);
            
            ctx.writeAndFlush(responseFrame).addListener(f -> {
                if (!f.isSuccess()) {
                    log.warn("Failed to write gateway response", f.cause());
                }
                ((QuicStreamChannel) ctx.channel()).shutdownOutput();
            });
        } catch (Exception e) {
            log.error("Failed to forward request to backend", e);
            ctx.writeAndFlush(new GatewayFrame(GatewayFrame.FLAG_CONTROL, "error=backend_unavailable", new byte[0]))
                .addListener(f -> ((QuicStreamChannel) ctx.channel()).shutdownOutput());
        }
    }

    private QuicGatewayClient resolveTargetClient(Channel parentChannel, ConnectionState state, GatewayFrame requestFrame) {
        // Enforcement rules based on connection state
        if (state == ConnectionState.UNAUTHENTICATED) {
            // Force all traffic to login server when unauthenticated
            return loginClient;
        }

        // Authenticated or Playing state
        byte flag = requestFrame.flag();
        if (flag == GatewayFrame.FLAG_LOGIN) {
            return loginClient;
        } else if (flag == GatewayFrame.FLAG_LOBBY) {
            return lobbyClient;
        } else if (flag == GatewayFrame.FLAG_WORLD) {
            if (state == ConnectionState.PLAYING) {
                QuicGatewayClient world = parentChannel.attr(WORLD_CLIENT_KEY).get();
                return world != null ? world : worldClient;
            }
        }
        return null;
    }

    private void handleStateTransitions(Channel parentChannel, GatewayFrame responseFrame) {
        String metadata = responseFrame.metadata();
        String status = getMetadataValue(metadata, "status");
        if (status == null) {
            return;
        }

        switch (status) {
            case "auth_success" -> {
                log.info("Client authenticated, transitioning to AUTHENTICATED");
                parentChannel.attr(STATE_KEY).set(ConnectionState.AUTHENTICATED);
            }
            case "play_success" -> {
                String worldAddr = getMetadataValue(metadata, "worldAddress");
                String sessionId = getMetadataValue(metadata, "sessionId");
                log.info("Client play success, sessionId={}, transitioning to PLAYING", sessionId);

                parentChannel.attr(STATE_KEY).set(ConnectionState.PLAYING);

                BackendAddress address;
                if (worldAddr == null || "DEFAULT".equals(worldAddr) || worldAddr.isBlank()) {
                    address = new BackendAddress(props.getWorldhost(), props.getWorldport());
                } else {
                    String[] parts = worldAddr.split(":", 2);
                    if (parts.length == 2) {
                        try {
                            address = new BackendAddress(parts[0], Integer.parseInt(parts[1]));
                        } catch (NumberFormatException e) {
                            log.warn("Invalid worldAddress format '{}', using default", worldAddr);
                            address = new BackendAddress(props.getWorldhost(), props.getWorldport());
                        }
                    } else {
                        address = new BackendAddress(props.getWorldhost(), props.getWorldport());
                    }
                }

                // Resolve the specific world server client
                QuicGatewayClient targetClient;
                if (address.host().equals(props.getWorldhost()) && address.port() == props.getWorldport()) {
                    targetClient = worldClient;
                } else {
                    targetClient = dynamicWorldClients.computeIfAbsent(address, key -> {
                        log.info("Opening new persistent internal connection to dynamic world backend: {}", key);
                        return new QuicGatewayClient(key.host(), key.port());
                    });
                }
                parentChannel.attr(WORLD_CLIENT_KEY).set(targetClient);
            }
        }
    }

    private static String getMetadataValue(String metadata, String key) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        String[] parts = metadata.split(";");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].trim().equals(key)) {
                return kv[1].trim();
            }
        }
        return null;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("Gateway QUIC stream error", cause);
        ctx.close();
    }
}
