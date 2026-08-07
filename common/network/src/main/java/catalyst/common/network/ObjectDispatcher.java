package catalyst.common.network;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Generic message dispatcher that routes incoming domain objects to registered handlers
 * based on their class type.
 * 
 * <p>This dispatcher acts as a bridge between the Fory-based network layer and the
 * application handlers, allowing each handler to process specific request types.
 */
@Slf4j
public class ObjectDispatcher {
    
    private final Map<Class<?>, Function<Object, Object>> handlers = new HashMap<>();
    
    /**
     * Registers a handler for a specific request type.
     * 
     * @param requestClass the class of requests to handle
     * @param handler function that processes the request and returns a response
     * @param <REQ> the request type
     * @param <RESP> the response type
     */
    public <REQ, RESP> void register(Class<REQ> requestClass, Function<REQ, RESP> handler) {
        handlers.put(requestClass, (Function<Object, Object>) handler);
        log.info("Registered handler for {}", requestClass.getSimpleName());
    }
    
    /**
     * Dispatches an incoming request to the appropriate handler.
     * 
     * @param request the request object
     * @return the response object, or null if no handler is registered
     */
    public Object dispatch(Object request) {
        if (request == null) {
            log.warn("Received null request");
            return createError("Request is null");
        }
        
        Function<Object, Object> handler = handlers.get(request.getClass());
        if (handler == null) {
            log.warn("No handler registered for request type: {}", request.getClass().getSimpleName());
            return createError("Unsupported request type: " + request.getClass().getSimpleName());
        }
        
        try {
            return handler.apply(request);
        } catch (Exception e) {
            log.error("Handler threw exception for {}", request.getClass().getSimpleName(), e);
            return createError("Internal server error: " + e.getMessage());
        }
    }
    
    private Object createError(String message) {
        // Return a generic error response
        // For now, return null to close the connection
        // TODO: Define a generic error response DTO
        log.error("Error: {}", message);
        return null;
    }
}
