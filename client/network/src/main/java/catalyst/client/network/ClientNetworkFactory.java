package catalyst.client.network;

import catalyst.client.network.dispatch.ClientDispatcher;
import catalyst.common.network.TlsProperties;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

/**
 * Micronaut Factory to expose ClientDispatcher and QuicGateway as singleton beans.
 * Keeps ClientDispatcher and QuicGateway dependency-free POJOs.
 */
@Factory
public class ClientNetworkFactory {

    @Singleton
    public ClientDispatcher clientDispatcher() {
        return new ClientDispatcher();
    }

    @Singleton
    public QuicGateway quicGateway(ClientDispatcher clientDispatcher, TlsProperties tlsProps) {
        return new QuicGateway(clientDispatcher, tlsProps);
    }
}
