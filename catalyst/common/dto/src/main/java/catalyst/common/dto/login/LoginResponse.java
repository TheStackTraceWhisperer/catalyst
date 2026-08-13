package catalyst.common.dto.login;

import catalyst.common.network.ResponseCode;

public record LoginResponse(
    ResponseCode code,
    Long accountId,
    String errorMessage
) {
  public LoginResponse(LoginResponse accountId) {
    this(ResponseCode.OK, accountId.accountId, null);
  }
  public LoginResponse(ResponseCode code, String errorMessage) {
    this(code, null, errorMessage);
  }
}
