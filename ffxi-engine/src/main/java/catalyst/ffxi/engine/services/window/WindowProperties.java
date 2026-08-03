package catalyst.ffxi.engine.services.window;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

@ConfigurationProperties("engine.window")
public interface WindowProperties {
    @Bindable(defaultValue = "1280") int getWidth();
    @Bindable(defaultValue = "720")  int getHeight();
    @Bindable(defaultValue = "FFXI Client") String getTitle();
}
