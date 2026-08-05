package catalyst.client.application.state;

import catalyst.client.application.config.ClientProperties;
import catalyst.client.network.QuicGatewayService;
import catalyst.client.application.ui.DebugLogPanel;
import catalyst.client.application.ui.LoginPanel;
import catalyst.common.network.ResponseCode;
import catalyst.common.dto.LoginResponse;
import catalyst.common.dto.ProtocolMapper;
import catalyst.client.engine.services.imgui.ImGuiService;
import catalyst.client.engine.services.state.ApplicationState;
import catalyst.client.engine.services.state.ApplicationStateService;
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
            LoginResponse resp = gateway.login(host, port, panel.getUsername(), panel.getPassword());
            if (resp.getCode() == ResponseCode.OK) {
                String authToken  = resp.getAuthToken();
                String accountId  = Long.toString(resp.getAccountId());
                debugLog.log("LOGIN_OK account=" + accountId);
                AuthenticatedState next = authenticatedProvider.get();
                next.init(host, port, authToken, accountId);
                stateService.changeState(() -> next);
            } else {
                debugLog.log("LOGIN_ERR " + resp.getCode() + " " + resp.getMessage());
            }
        } catch (Exception e) {
            debugLog.log("LOGIN_ERR " + e.getMessage());
        }
    }
}
