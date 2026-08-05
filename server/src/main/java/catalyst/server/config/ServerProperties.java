package catalyst.server.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

@ConfigurationProperties("catalyst.server")
public interface ServerProperties {
    @Bindable(defaultValue = "35555")  int    getPort();
    @Bindable(defaultValue = "30")     long   getSessionTimeoutSeconds();
    @Bindable(defaultValue = "300")    long   getAuthTicketTimeoutSeconds();
    @Bindable(defaultValue = "3")      int    getArgon2Iterations();
    @Bindable(defaultValue = "65536")  int    getArgon2MemoryKib();
    @Bindable(defaultValue = "1")      int    getArgon2Parallelism();
    @Bindable(defaultValue = "5000")   long   getKeepaliveIntervalMs();
    @Bindable(defaultValue = "jdbc:postgresql://localhost:5432/catalyst") String getDbUrl();
    @Bindable(defaultValue = "catalyst")   String getDbUser();
    @Bindable(defaultValue = "catalyst")   String getDbPassword();  // override via CATALYST_DB_PASSWORD in prod
    @Bindable(defaultValue = "8")      int    getDbPoolMaxSize();
    @Bindable(defaultValue = "1")      int    getDbPoolMinIdle();
    @Bindable(defaultValue = "10000")  long   getDbConnectionTimeoutMs();
}
