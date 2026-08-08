package catalyst.client.network;

import catalyst.common.network.ClientDispatcher;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

/**
 * Micronaut Factory to expose ClientDispatcher as a singleton bean.
 * Keeps ClientDispatcher a dependency-free POJO in common-network.
 */
@Factory
public class ClientNetworkFactory {

    @Singleton
    public ClientDispatcher clientDispatcher() {
        return new ClientDispatcher();
    }
}
