package catalyst.ffxi.client.state;

import catalyst.ffxi.client.config.ClientProperties;
import catalyst.ffxi.client.network.QuicGatewayService;
import catalyst.ffxi.client.ui.DebugLogPanel;
import catalyst.ffxi.client.ui.LoginPanel;
import catalyst.ffxi.common.net.MessageFrame;
import catalyst.ffxi.common.net.dto.LoginResponse;
import catalyst.ffxi.common.net.dto.ProtocolMapper;
import catalyst.ffxi.engine.services.imgui.ImGuiService;
import catalyst.ffxi.engine.services.state.ApplicationState;
import catalyst.ffxi.engine.services.state.ApplicationStateService;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.opengl.GL11;

@Slf4j
@Prototype
@Named("initial")
@RequiredArgsConstructor
public class UnauthenticatedState implements ApplicationState {

    private final LoginPanel panel;
    private final DebugLogPanel debugLog;
    private final QuicGatewayService gateway;
    private final ApplicationStateService stateService;
    private final BeanProvider<AuthenticatedState> authenticatedProvider;
    private final BeanProvider<LocalZoneState> localProvider;
    private final ClientProperties props;

    private String host;
    private int    port;

    @Override
    public void onEnter() {
        host = props.getDefaultServerHost();
        port = props.getDefaultServerPort();
        debugLog.log("Ready to connect — " + host + ":" + port);
    }

    @Override
    public void onUpdate(float dt) {
        GL11.glClearColor(0.07f, 0.07f, 0.09f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        panel.render();
        debugLog.render();
        if (panel.isLoginRequested())     doLogin();
        if (panel.isLocalModeRequested()) stateService.changeState(localProvider::get);
        panel.clearIntents();
    }

    @Override
    public void onExit() {}

    private void doLogin() {
        try {
            MessageFrame respFrame = gateway.login(host, port, panel.getUsername(), panel.getPassword());
            if ("LOGIN_OK".equals(respFrame.type())) {
                LoginResponse resp = ProtocolMapper.toLoginResponse(respFrame);
                String authToken  = resp.getAuthToken();
                String accountId  = Long.toString(resp.getAccountId());
                debugLog.log("LOGIN_OK account=" + accountId);
                AuthenticatedState next = authenticatedProvider.get();
                next.init(host, port, authToken, accountId);
                stateService.changeState(() -> next);
            } else {
                debugLog.log("LOGIN_ERR " + respFrame.get("code") + " " + respFrame.get("message"));
            }
        } catch (Exception e) {
            debugLog.log("LOGIN_ERR " + e.getMessage());
        }
    }
}
