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
    
    private volatile String host;
    private volatile int port = -1;

    /**
     * Connects/registers the target gateway host and port.
     */
    public void connect(String host, int port) {
        this.host = host;
        this.port = port;
        log.info("QuicGatewayService connected to target gateway={}:{}", host, port);
    }

    /**
     * Sends an outbound DTO request to the gateway server and blocks until the typed response is received.
     */
    public <T> T request(Object requestPayload, Class<T> responseType) throws IOException {
        if (host == null || port == -1) {
            throw new IllegalStateException("Not connected to gateway. Call connect(host, port) first.");
        }
        return gateway.request(host, port, requestPayload, responseType);
    }

    /**
     * Sends an outbound DTO message asynchronously (non-blocking) and returns immediately.
     */
    public void sendAsync(Object requestPayload) {
        if (host == null || port == -1) {
            throw new IllegalStateException("Not connected to gateway. Call connect(host, port) first.");
        }
        gateway.sendAsync(host, port, requestPayload);
    }

    @Override
    @PreDestroy
    public void close() {
        gateway.close();
    }
}
