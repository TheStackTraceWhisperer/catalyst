package catalyst.gateway.health;

import catalyst.gateway.transport.GatewayServer;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.health.HealthStatus;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Singleton
public class GatewayHealthIndicator implements HealthIndicator {
    private final GatewayServer server;

    @Override
    public Publisher<HealthResult> getResult() {
      return Publishers.just(HealthResult.builder("gateway-quic-server").status(
          server.isBound()
            ? HealthStatus.UP
            : HealthStatus.DOWN
        ).build());
    }
}
