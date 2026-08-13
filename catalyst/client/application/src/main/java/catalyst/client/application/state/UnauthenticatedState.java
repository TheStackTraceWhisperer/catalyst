package catalyst.client.application.state;

import catalyst.client.application.ClientState;
import catalyst.client.application.properties.ClientProperties;
import catalyst.client.application.ui.DebugLogPanel;
import catalyst.client.application.ui.LoginPanel;
import catalyst.client.engine.services.state.ApplicationState;
import catalyst.client.engine.services.state.ApplicationStateService;
import catalyst.client.network.ClientTransportService;
import catalyst.common.dto.login.LoginRequest;
import catalyst.common.dto.login.LoginResponse;
import catalyst.common.network.DecodedPacket;
import catalyst.common.network.PacketType;
import catalyst.common.network.ResponseCode;
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
    private final ClientTransportService gateway;
    private final ApplicationStateService stateService;
    private final ClientState clientState;
    private final BeanProvider<AuthenticatedState> authenticatedProvider;
    private final ClientProperties props;

    private String host;
    private int port;

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

        // Render UI with ClientState
        panel.render(clientState);
        debugLog.render();

        if (panel.isLoginRequested()) {
            doLogin();
        }
        panel.clearIntents();
    }

    @Override
    public void onExit() {}

    private void doLogin() {
        try {
            gateway.connect(host, port);

            LoginRequest requestPayload = new LoginRequest(panel.getUsername(), panel.getPassword());
            DecodedPacket requestPacket = new DecodedPacket(PacketType.LOGIN_REQUEST, requestPayload);

            // Execute request
            LoginResponse resp = gateway.request(requestPacket, LoginResponse.class);

            if (resp.code() == ResponseCode.OK) {
                long accountId = resp.accountId();
                debugLog.log("LOGIN_OK account=" + accountId);

                clientState.onAuthenticated(accountId);

                AuthenticatedState next = authenticatedProvider.get();
                next.init(accountId);
                stateService.changeState(() -> next);
            } else {
                clientState.onAuthenticationFailed(resp.code(), resp.errorMessage());
                debugLog.log("LOGIN_ERR " + resp.code() + " " + resp.errorMessage());
            }
        } catch (Exception e) {
            clientState.onAuthenticationFailed(ResponseCode.ERROR, e.getMessage());
            debugLog.log("LOGIN_ERR " + e.getMessage());
        }
    }
}