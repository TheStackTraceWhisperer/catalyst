package catalyst.ffxi.client.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

@ConfigurationProperties("ffxi.client")
public interface ClientProperties {
    @Bindable(defaultValue = "127.0.0.1") String getDefaultServerHost();
    @Bindable(defaultValue = "35555")     int    getDefaultServerPort();
    @Bindable(defaultValue = "5000")      long   getKeepaliveIntervalMs();
}
