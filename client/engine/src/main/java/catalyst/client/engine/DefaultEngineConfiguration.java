package catalyst.client.engine;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Factory
public class DefaultEngineConfiguration {

    @Singleton
    @Requires(missingBeans = ApplicationLoopPolicy.class)
    ApplicationLoopPolicy loopPolicy() {
        log.debug("Using default loop policy (standard window-close)");
        return ApplicationLoopPolicy.standard();
    }
}
