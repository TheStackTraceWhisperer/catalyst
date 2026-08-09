package catalyst.server.world.properties;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("catalyst.server")
public interface ServerProperties {
    int getPort();
    long getSessionTimeoutSeconds();
    long getAuthTicketTimeoutSeconds();
    long getKeepaliveIntervalMs();
}
