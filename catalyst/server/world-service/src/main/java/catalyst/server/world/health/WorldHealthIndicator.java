package catalyst.server.world.health;

import catalyst.server.world.transport.QuicServerTransport;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.health.HealthStatus;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Singleton
public class WorldHealthIndicator implements HealthIndicator {
    private final QuicServerTransport transport;

    @Override
    public Publisher<HealthResult> getResult() {
        return Publishers.just(
            HealthResult.builder("world-quic-server")
                .status(transport.isBound() ? HealthStatus.UP : HealthStatus.DOWN)
                .build()
        );
    }
}
