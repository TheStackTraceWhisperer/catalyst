package catalyst.gateway.transport;

import catalyst.server.common.network.ClientSession;
import catalyst.common.network.DecodedPacket;
import catalyst.common.network.ForySerializer;
import catalyst.server.common.network.GatewayControlMessage;
import catalyst.server.common.network.GatewayFrame;
import catalyst.server.common.network.NetworkAttributes;
import catalyst.common.network.ServiceType;
import catalyst.common.network.TlsProperties;
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

    private final GatewayProperties props;
    private final Map<ServiceType, BackendClient> clients;
    private final Map<String, BackendClient> dynamicWorldClients;
    private final TlsProperties tlsProps;

    public RequestHandler(
      GatewayProperties props,
      Map<ServiceType, BackendClient> clients,
      Map<String, BackendClient> dynamicWorldClients,
      TlsProperties tlsProps
    ) {
        this.props = props;
        this.clients = clients;
        this.dynamicWorldClients = dynamicWorldClients;
        this.tlsProps = tlsProps;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // Intercept DecodedPacket from public client
        if (!(msg instanceof DecodedPacket clientPacket)) {
            log.warn("Expected DecodedPacket in RequestHandler, got {}", msg.getClass().getName());
            ctx.close();
            return;
        }

        Channel parentChannel = ctx.channel().parent();
        SecurityState state = parentChannel.attr(STATE_KEY).get();
        if (state == null) {
            state = SecurityState.UNAUTHENTICATED;
            parentChannel.attr(STATE_KEY).set(state);
        }

        // Replaces the leaky switch statement entirely
        ServiceType targetService = clientPacket.type().getTargetService();
        BackendClient backend = resolveTargetClient(parentChannel, state, targetService);

        if (backend == null) {
            log.warn("Access denied or no backend client for PacketType={} (Service={}) in state={}",
              clientPacket.type(), targetService, state);
            ctx.close();
            return;
        }

        // Serialize client payload DTO to bytes for GatewayFrame transport
        byte[] payloadBytes;
        try {
            payloadBytes = ForySerializer.serialize(clientPacket.payload());
        } catch (Exception e) {
            log.error("Failed to serialize client payload for packet type={}", clientPacket.type(), e);
            ctx.close();
            return;
        }

        // Extract session ID from ClientSession attribute
        ClientSession session = parentChannel.attr(NetworkAttributes.SESSION_KEY).get();
        String sessionId = session != null ? String.valueOf(session.gatewaySessionId()) : "";

        GatewayFrame frameToSend = new GatewayFrame(targetService, sessionId, payloadBytes);

        // Forward to backend microservice via internal QUIC stream
        backend.requestAsync(frameToSend, controlMsg -> handleStateTransitions(parentChannel, controlMsg))
          .thenAccept(responseFrame -> {
              if (responseFrame == null) {
                  ctx.close();
                  return;
              }

              try {
                  // Deserialize backend payload byte[] -> DTO, wrap in DecodedPacket
                  Object responseObj = ForySerializer.deserialize(responseFrame.payload());
                  DecodedPacket responsePacket = new DecodedPacket(clientPacket.type(), responseObj);

                  ctx.writeAndFlush(responsePacket).addListener(f -> {
                      if (!f.isSuccess()) {
                          log.warn("Failed to write gateway response to client", f.cause());
                      }
                      ((QuicStreamChannel) ctx.channel()).shutdownOutput();
                  });
              } catch (Exception e) {
                  log.error("Failed to deserialize backend response payload", e);
                  ctx.close();
              }
          })
          .exceptionally(throwable -> {
              log.error("Failed to forward request asynchronously to backend", throwable);
              ctx.close();
              return null;
          });
    }

    private BackendClient resolveTargetClient(Channel parentChannel, SecurityState state, ServiceType serviceType) {
        GatewayProperties.BackendConfig config = props.getBackends().get(serviceType);

        SecurityState requiredState = SecurityState.UNAUTHENTICATED;
        if (config != null && config.policy() != null) {
            try {
                requiredState = SecurityState.valueOf(config.policy());
            } catch (IllegalArgumentException e) {
                log.error("Invalid security policy '{}' configured for service {}", config.policy(), serviceType);
            }
        }

        if (state.level() < requiredState.level()) {
            log.warn("Access denied to service {}: required state >= {}, client state is {}", serviceType, requiredState, state);
            return null;
        }

        if (requiredState == SecurityState.SESSION_BOUND) {
            return parentChannel.attr(WORLD_CLIENT_KEY).get();
        }

        return clients.get(serviceType);
    }

    private void handleStateTransitions(Channel parentChannel, GatewayControlMessage gcm) {
        if (gcm == null || gcm.command() == null) {
            return;
        }

        switch (gcm.command()) {
            case "auth_success" -> {
                log.info("Client authenticated, transitioning SecurityState to AUTHENTICATED");
                parentChannel.attr(STATE_KEY).set(SecurityState.AUTHENTICATED);
            }
            case "play_success" -> {
                String worldAddr = gcm.worldAddress();
                String sessionId = gcm.sessionId();
                log.info("Client play success, sessionId={}, transitioning to SESSION_BOUND", sessionId);

                parentChannel.attr(STATE_KEY).set(SecurityState.SESSION_BOUND);

                if (worldAddr == null || worldAddr.isBlank()) {
                    log.error("play_success control message missing valid worldAddress!");
                    parentChannel.close();
                    return;
                }

                BackendClient targetClient = dynamicWorldClients.computeIfAbsent(worldAddr, key -> {
                    String[] parts = key.split(":", 2);
                    log.info("Opening new connection to dynamic world backend: {}:{}", parts[0], parts[1]);
                    return new BackendClient(parts[0], Integer.parseInt(parts[1]), tlsProps);
                });
                parentChannel.attr(WORLD_CLIENT_KEY).set(targetClient);
            }
            case "logout_success" -> {
                log.info("Client logged out, resetting SecurityState to AUTHENTICATED");
                parentChannel.attr(STATE_KEY).set(SecurityState.AUTHENTICATED);
                parentChannel.attr(WORLD_CLIENT_KEY).set(null);
                parentChannel.attr(NetworkAttributes.SESSION_KEY).set(null);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("Gateway QUIC stream error", cause);
        ctx.close();
    }
}