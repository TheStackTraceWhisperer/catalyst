package catalyst.client.network.listener;

import catalyst.common.network.ResponseCode;

public interface SessionNetworkListener {
  void onAuthenticated(long accountId);
  void onAuthenticationFailed(ResponseCode code, String errorMessage);
  void onLoggedOut();
}