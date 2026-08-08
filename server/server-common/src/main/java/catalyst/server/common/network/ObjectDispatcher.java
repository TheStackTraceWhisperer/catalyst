package catalyst.server.common.network;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Generic message dispatcher that routes incoming domain objects to registered handlers
 * based on their class type.
 * 
 * <p>This dispatcher acts as a bridge between the Fory-based network layer and the
 * application handlers, allowing each handler to process specific request types.
 */
@Slf4j
public class ObjectDispatcher {
    
    private final Map<Class<?>, BiFunction<Object, String, Object>> handlers = new HashMap<>();
    
    /** Registers all handlers in the collection. */
    public void registerAll(java.util.Collection<PacketHandler<?>> packetHandlers) {
        if (packetHandlers != null) {
            for (PacketHandler<?> ph : packetHandlers) {
                registerInternal(ph);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void registerInternal(PacketHandler<T> handler) {
        handlers.put(handler.getPacketType(), (req, sessionId) -> {
            try {
                return handler.handle((T) req, sessionId);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        log.info("Registered PacketHandler for {}", handler.getPacketType().getSimpleName());
    }

    /**
     * Registers a handler for a specific request type.
     */
    public <REQ, RESP> void register(Class<REQ> requestClass, BiFunction<REQ, String, RESP> handler) {
        handlers.put(requestClass, (req, sessionId) -> handler.apply((REQ) req, sessionId));
        log.info("Registered handler for {}", requestClass.getSimpleName());
    }
    
    /**
     * Dispatches an incoming request to the appropriate handler without a session ID.
     */
    public Object dispatch(Object request) {
        return dispatch(request, null);
    }

    /**
     * Dispatches an incoming request to the appropriate handler with a session ID.
     * 
     * @param request the request object
     * @param sessionId the session ID context
     * @return the response object, or null if no handler is registered
     */
    public Object dispatch(Object request, String sessionId) {
        if (request == null) {
            log.warn("Received null request");
            return createError("Request is null");
        }
        
        BiFunction<Object, String, Object> handler = handlers.get(request.getClass());
        if (handler == null) {
            log.warn("No handler registered for request type: {}", request.getClass().getSimpleName());
            return createError("Unsupported request type: " + request.getClass().getSimpleName());
        }
        
        try {
            return handler.apply(request, sessionId);
        } catch (Exception e) {
            log.error("Handler threw exception for {}", request.getClass().getSimpleName(), e);
            return createError("Internal server error: " + e.getMessage());
        }
    }
    
    private Object createError(String message) {
        log.error("Error: {}", message);
        return null;
    }
}
