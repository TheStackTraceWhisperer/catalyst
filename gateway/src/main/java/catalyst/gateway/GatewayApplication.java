package catalyst.gateway;

import catalyst.common.network.ServiceType;
import catalyst.gateway.properties.GatewayProperties;
import catalyst.gateway.transport.GatewayServer;
import io.micronaut.runtime.Micronaut;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;

@Slf4j
@Singleton
public class GatewayApplication {

    private final GatewayServer server;
    private final GatewayProperties props;

    public GatewayApplication(GatewayServer server, GatewayProperties props) {
        this.server = server;
        this.props = props;
    }

    public static void main(String[] args) {
        Micronaut.build(args)
            .classes(GatewayApplication.class)
            .packages("catalyst")
            .run();
    }

    @EventListener
    public void onStartup(StartupEvent event) throws Exception {
        log.info("Gateway starting on port {}", props.getPort());
        for (Map.Entry<ServiceType, GatewayProperties.BackendConfig> entry : props.getBackends().entrySet()) {
            ServiceType type = entry.getKey();
            GatewayProperties.BackendConfig config = entry.getValue();
            log.info("Configured backend service: '{}' -> flag={}, policy={}, destination={}:{}", 
                type, type.flag(), config.policy(), config.host(), config.port());
        }
        
        Thread.ofVirtual().start(() -> {
            try {
                server.start();
                log.info("Gateway Service started on port {}", props.getPort());
                server.awaitShutdown();
            } catch (Exception e) {
                log.error("Failed to start Gateway server", e);
            }
        });
    }
}
