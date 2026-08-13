package catalyst.common.dto.world;

import catalyst.common.network.ResponseCode;

public record PingResponse(
  ResponseCode code,
  long timestamp
) {
}
