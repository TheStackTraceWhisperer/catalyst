package catalyst.server.common.network;

import io.netty.util.AttributeKey;

public final class NetworkAttributes {
  public static final AttributeKey<ClientSession> SESSION_KEY = AttributeKey.valueOf("CLIENT_SESSION");

  private NetworkAttributes() {}
}