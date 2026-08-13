package catalyst.common.dto.world;

import catalyst.common.network.ResponseCode;

/**
 * Server confirmation of a session tear-down / logout request.
 *
 * @param code Operation status (OK on successful disconnect, ERROR/CONFLICT otherwise).
 * @param errorMessage Human-readable context if logout processing encountered an issue (null on success).
 */
public record LogoutResponse(ResponseCode code, String errorMessage) {

  /**
   * Compact constructor for successful logouts.
   */
  public LogoutResponse() {
    this(ResponseCode.OK, null);
  }

  /**
   * Compact constructor for failed logouts.
   */
  public LogoutResponse(ResponseCode code, String errorMessage) {
    this.code = code;
    this.errorMessage = errorMessage;
  }
}