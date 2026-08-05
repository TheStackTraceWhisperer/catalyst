package catalyst.common.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LoginRequest {
    String username;
    String password;
}
