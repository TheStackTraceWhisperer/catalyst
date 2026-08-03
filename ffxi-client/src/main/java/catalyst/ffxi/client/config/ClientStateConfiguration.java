package catalyst.ffxi.client.config;

import catalyst.ffxi.client.state.UnauthenticatedState;
import catalyst.ffxi.engine.services.state.ApplicationState;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Factory
@RequiredArgsConstructor
public class ClientStateConfiguration {

    private final BeanProvider<UnauthenticatedState> unauthProvider;

    @Singleton
    @Named("initial")
    ApplicationState initialState() {
        return unauthProvider.get();
    }
}
