package catalyst.common.network;

/**
 * Standard protocol-level response status codes used by the DTO contracts
 * to classify operation results in a strongly-typed manner.
 */
public enum ResponseCode {
    /** Request completed successfully. */
    OK,
    
    /** Authentication failed (e.g. invalid username/password). */
    UNAUTHORIZED,
    
    /** Resource not found. */
    NOT_FOUND,
    
    /** The request could not be processed due to a business/validation rule conflict. */
    CONFLICT,
    
    /** Generic internal server or network process failure. */
    ERROR
}
