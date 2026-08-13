package catalyst.common.dto.world;

public record PingRequest(long timestamp) {
  public PingRequest() {
    this(System.currentTimeMillis());
  }
}
