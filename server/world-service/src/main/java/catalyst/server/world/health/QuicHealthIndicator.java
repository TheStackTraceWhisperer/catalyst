package catalyst.server.world.health;

import catalyst.server.world.transport.QuicServerTransport;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.health.HealthStatus;

@Singleton
public class QuicHealthIndicator implements HealthIndicator {
    private final QuicServerTransport transport;

    public QuicHealthIndicator(QuicServerTransport transport) {
        this.transport = transport;
    }

    @Override
    public Publisher<HealthResult> getResult() {
        boolean active = transport.isBound();
        HealthResult.Builder builder = HealthResult.builder("quic-server");
        if (active) {
            return Publishers.just(builder.status(HealthStatus.UP).build());
        } else {
            return Publishers.just(builder.status(HealthStatus.DOWN).build());
        }
    }
}
