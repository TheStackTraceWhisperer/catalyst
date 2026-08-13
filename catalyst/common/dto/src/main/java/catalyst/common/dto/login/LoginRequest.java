package catalyst.common.dto.login;

/**
 * Client authentication request.
 *
 * @param username Target user account username.
 * @param password Raw or hashed credentials supplied by the client.
 */
public record LoginRequest(String username, String password) {
  public LoginRequest {
    if (username != null) {
      username = username.trim();
    }
  }
}