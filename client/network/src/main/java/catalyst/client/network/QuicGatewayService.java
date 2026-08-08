package catalyst.client.network;

import catalyst.common.network.ClientDispatcher;
import jakarta.inject.Singleton;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * Generic QUIC gateway service. Wraps QuicGateway (connection lifecycle) and exposes
 * generic, type-safe network request/response operations.
 */
@Slf4j
@Singleton
public class QuicGatewayService implements AutoCloseable {

    private final QuicGateway gateway;



    public QuicGatewayService(ClientDispatcher clientDispatcher) {
        this.gateway = new QuicGateway(clientDispatcher);
    }

    /**
     * Sends an outbound DTO request to the gateway server and blocks until the typed response is received.
     */
    public <T> T request(String host, int port, Object requestPayload, Class<T> responseType) throws IOException {
        return gateway.request(host, port, requestPayload, responseType);
    }

    @Override
    @PreDestroy
    public void close() {
        gateway.close();
    }
}
