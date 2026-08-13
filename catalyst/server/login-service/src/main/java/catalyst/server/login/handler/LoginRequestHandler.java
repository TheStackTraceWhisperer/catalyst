package catalyst.server.login.handler;

import catalyst.common.dto.login.LoginRequest;
import catalyst.common.dto.login.LoginResponse;
import catalyst.server.common.network.GatewayControlMessage;
import catalyst.server.common.network.GatewayFrame;
import catalyst.common.network.PacketHandler;
import catalyst.common.network.ResponseCode;
import catalyst.common.network.ServiceType;
import catalyst.common.network.ForySerializer;
import catalyst.server.login.service.AccountAuthenticationService;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class LoginRequestHandler implements PacketHandler<LoginRequest> {

    private final AccountAuthenticationService authService;

    @Override
    public void handle(LoginRequest payload, ChannelHandlerContext ctx) {
        log.info("Processing login request for user: {}", payload.username());

        authService.authenticateAsync(payload.username(), payload.password())
          .thenAccept(authResult -> {
              if (authResult.isSuccess()) {
                  long accountId = authResult.accountId();
                  log.info("Authentication SUCCESS for user: {} (accountId={})", payload.username(), accountId);

                  // 1. Send GatewayControlMessage("auth_success") so Gateway advances SecurityState to AUTHENTICATED
                  GatewayControlMessage controlSignal = new GatewayControlMessage("auth_success", null, String.valueOf(accountId));
                  writeControlFrame(ctx, controlSignal);

                  // 2. Write LoginResponse DTO back to client
                  LoginResponse response = new LoginResponse(ResponseCode.OK, accountId, null);
                  ctx.writeAndFlush(response).addListener(f -> {
                      if (!f.isSuccess()) {
                          log.warn("Failed to flush LoginResponse", f.cause());
                      }
                  });
              } else {
                  log.warn("Authentication FAILED for user: {}", payload.username());
                  LoginResponse response = new LoginResponse(ResponseCode.UNAUTHORIZED, null, "Invalid username or password");
                  ctx.writeAndFlush(response);
              }
          })
          .exceptionally(err -> {
              log.error("Internal error during authentication for user: {}", payload.username(), err);
              LoginResponse response = new LoginResponse(ResponseCode.ERROR, null, "Internal server error");
              ctx.writeAndFlush(response);
              return null;
          });
    }

    private void writeControlFrame(ChannelHandlerContext ctx, GatewayControlMessage controlMsg) {
        try {
            byte[] controlBytes = ForySerializer.serialize(controlMsg);
            GatewayFrame controlFrame = new GatewayFrame(ServiceType.CONTROL, "", controlBytes);
            ctx.write(controlFrame); // Write without flush so it bundles with the upcoming response frame
        } catch (Exception e) {
            log.error("Failed to serialize GatewayControlMessage", e);
        }
    }
}