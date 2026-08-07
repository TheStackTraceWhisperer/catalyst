package catalyst.common.network;

import io.netty.util.AttributeKey;

/**
 * Channel attribute holder for routing context information.
 * Used by {@link ForyDecoder} and {@link ForyEncoder} to store and retrieve routing keys.
 */
public final class RoutingContext {
    
    /**
     * Channel attribute key for storing the routing key of the current message.
     * This allows handlers downstream to know which type of request was received.
     */
    public static final AttributeKey<String> ROUTING_KEY = AttributeKey.valueOf("routingKey");
    
    private RoutingContext() {}
}
