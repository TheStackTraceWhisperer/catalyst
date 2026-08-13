package catalyst.client.network;

import catalyst.common.network.DecodedPacket;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ClientTransportService implements AutoCloseable {

    private final ClientTransport gateway;

    private volatile String host;
    private volatile int port = -1;

    public void connect(String host, int port) {
        this.host = host;
        this.port = port;
        log.info("QuicGatewayService connected to target gateway={}:{}", host, port);
    }

    /**
     * Synchronous RPC request.
     */
    public <T> T request(DecodedPacket requestPacket, Class<T> responseType) throws IOException {
        ensureConnected();
        return gateway.request(host, port, requestPacket, responseType);
    }

    /**
     * Asynchronous fire-and-forget message.
     */
    public void sendAsync(DecodedPacket packet) {
        ensureConnected();
        gateway.sendAsync(host, port, packet);
    }

    private void ensureConnected() {
        if (host == null || port == -1) {
            throw new IllegalStateException("Not connected to gateway. Call connect(host, port) first.");
        }
    }

    @Override
    @PreDestroy
    public void close() {
        gateway.close();
    }
}