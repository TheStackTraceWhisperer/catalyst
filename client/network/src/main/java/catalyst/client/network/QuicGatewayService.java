package catalyst.client.network;

import jakarta.inject.Singleton;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * Generic QUIC gateway service. Wraps QuicGateway (connection lifecycle) and exposes
 * generic, type-safe network request/response operations.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class QuicGatewayService implements AutoCloseable {

    private final QuicGateway gateway;

    /**
     * Sends an outbound DTO request to the gateway server and blocks until the typed response is received.
     */
    public <T> T request(String host, int port, Object requestPayload, Class<T> responseType) throws IOException {
        return gateway.request(host, port, requestPayload, responseType);
    }

    /**
     * Sends an outbound DTO message asynchronously (non-blocking) and returns immediately.
     */
    public void sendAsync(String host, int port, Object requestPayload) {
        gateway.sendAsync(host, port, requestPayload);
    }

    @Override
    @PreDestroy
    public void close() {
        gateway.close();
    }
}
