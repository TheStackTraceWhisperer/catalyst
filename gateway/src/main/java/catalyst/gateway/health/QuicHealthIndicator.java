package catalyst.gateway.health;

import catalyst.gateway.transport.GatewayServer;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import io.micronaut.core.async.publisher.Publishers;

import io.micronaut.health.HealthStatus;

@Singleton
public class QuicHealthIndicator implements HealthIndicator {
    private final GatewayServer server;

    public QuicHealthIndicator(GatewayServer server) {
        this.server = server;
    }

    @Override
    public Publisher<HealthResult> getResult() {
        boolean active = server.isBound();
        HealthResult.Builder builder = HealthResult.builder("quic-server");
        if (active) {
            return Publishers.just(builder.status(HealthStatus.UP).build());
        } else {
            return Publishers.just(builder.status(HealthStatus.DOWN).build());
        }
    }
}
