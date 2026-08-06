package catalyst.gateway;

import catalyst.gateway.config.GatewayProperties;
import catalyst.gateway.transport.GatewayServer;
import io.micronaut.runtime.Micronaut;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class GatewayApplication {

    private final GatewayServer server;
    private final GatewayProperties props;

    public static void main(String[] args) {
        Micronaut.run(GatewayApplication.class, args);
    }

    @EventListener
    public void onStartup(StartupEvent event) throws Exception {
        log.info("GATEWAY_PROPS: loginhost={}, loginport={}", props.getLoginhost(), props.getLoginport());
        log.info("GATEWAY_PROPS: lobbyhost={}, lobbyport={}", props.getLobbyhost(), props.getLobbyport());
        log.info("GATEWAY_PROPS: worldhost={}, worldport={}", props.getWorldhost(), props.getWorldport());
        server.start();
        log.info("Gateway Service started on port {}", props.getPort());
        server.awaitShutdown();
    }
}
