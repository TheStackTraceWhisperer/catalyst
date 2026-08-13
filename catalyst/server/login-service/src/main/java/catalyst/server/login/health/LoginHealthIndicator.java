package catalyst.server.login.health;

import catalyst.server.login.network.ServerTransport;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.health.HealthStatus;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Singleton
public class LoginHealthIndicator implements HealthIndicator {
    private final ServerTransport transport;

    @Override
    public Publisher<HealthResult> getResult() {
        return Publishers.just(
            HealthResult.builder("login-quic-server")
                .status(transport.isBound() ? HealthStatus.UP : HealthStatus.DOWN)
                .build()
        );
    }
}
