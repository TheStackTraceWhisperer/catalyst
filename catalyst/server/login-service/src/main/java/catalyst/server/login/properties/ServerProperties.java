package catalyst.server.login.properties;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("catalyst.server")
public interface ServerProperties {
    int getPort();
    long getSessionTimeoutSeconds();
    long getAuthTicketTimeoutSeconds();
    int getArgon2Iterations();
    int getArgon2MemoryKib();
    int getArgon2Parallelism();
    long getKeepaliveIntervalMs();
}
