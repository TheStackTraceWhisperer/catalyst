package catalyst.common.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import lombok.extern.slf4j.Slf4j;
import org.apache.fory.Fory;
import org.apache.fory.ThreadSafeFory;
import org.apache.fory.config.Language;
import java.util.List;

/**
 * Netty encoder that serializes outbound domain objects using Apache Fory
 * and wraps them in a {@link GatewayFrame} with appropriate routing flags/metadata.
 */
@Slf4j
public final class ForyEncoder extends MessageToMessageEncoder<Object> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
        byte[] payloadBytes = ForySerializer.serialize(msg);
        
        byte flag = determineFlag(msg);
        String metadata = determineMetadata(msg);

        log.debug("Encoding message: class={} flag={} metadata={}", 
            msg.getClass().getSimpleName(), flag, metadata);

        out.add(new GatewayFrame(flag, metadata, payloadBytes));
    }

    private byte determineFlag(Object msg) {
        String name = msg.getClass().getSimpleName();
        return switch (name) {
            // Client Requests
            case "LoginRequest" -> GatewayFrame.FLAG_LOGIN;
            case "CharListRequest", "CharCreateRequest", "CharSelectRequest", "CharDeleteRequest", "PlayRequest" -> 
                GatewayFrame.FLAG_LOBBY;
            case "PingRequest", "LogoutRequest" -> GatewayFrame.FLAG_WORLD;

            // Server Responses
            case "LoginResponse" -> GatewayFrame.FLAG_LOGIN;
            case "CharListResponse", "CharCreateResponse", "CharSelectResponse", "CharDeleteResponse", "PlayResponse" -> 
                GatewayFrame.FLAG_LOBBY;
            case "PingResponse", "LogoutResponse" -> GatewayFrame.FLAG_WORLD;

            default -> GatewayFrame.FLAG_LOBBY;
        };
    }

    private String determineMetadata(Object msg) {
        try {
            // Check for successful authentication response to signal the gateway
            if (msg.getClass().getSimpleName().equals("LoginResponse")) {
                Object code = msg.getClass().getMethod("code").invoke(msg);
                if (code != null && "OK".equals(code.toString())) {
                    return "status=auth_success";
                }
            }

            // Check for successful play response to signal the gateway and pass world redirect info
            if (msg.getClass().getSimpleName().equals("PlayResponse")) {
                Object code = msg.getClass().getMethod("code").invoke(msg);
                if (code != null && "OK".equals(code.toString())) {
                    Object sessionId = msg.getClass().getMethod("sessionId").invoke(msg);
                    String sid = sessionId != null ? sessionId.toString() : "";
                    
                    // Retrieve worldAddress if present, else default to "DEFAULT"
                    String addr = "DEFAULT";
                    try {
                        Object worldAddress = msg.getClass().getMethod("worldAddress").invoke(msg);
                        if (worldAddress != null && !worldAddress.toString().isBlank()) {
                            addr = worldAddress.toString();
                        }
                    } catch (NoSuchMethodException ignored) {}

                    return "status=play_success;worldAddress=" + addr + ";sessionId=" + sid;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract metadata for gateway signal from {}", msg.getClass().getSimpleName(), e);
        }
        return "";
    }
}
