package catalyst.ffxi.client.network;

import catalyst.ffxi.client.config.ClientProperties;
import catalyst.ffxi.common.net.MessageFrame;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class KeepAliveService {

    private final QuicGatewayService gateway;
    private final ClientProperties props;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> task;

    @Getter private volatile String status = "idle";
    @Getter private volatile long lastRttMs = -1;
    @Getter private volatile Instant lastOkAt = null;

    private String host;
    private int port;
    private String sessionId;

    public void start(String host, int port, String sessionId) {
        this.host = host;
        this.port = port;
        this.sessionId = sessionId;
        this.status = "connected";
        long interval = props.getKeepaliveIntervalMs();
        task = scheduler.scheduleAtFixedRate(this::sendPing, interval, interval, TimeUnit.MILLISECONDS);
        log.info("KeepAlive started session={} interval={}ms", sessionId, interval);
    }

    public void stop() {
        if (task != null) { task.cancel(false); task = null; }
        status = "idle";
        log.info("KeepAlive stopped");
    }

    public void sendPing() {
        long t0 = System.currentTimeMillis();
        try {
            MessageFrame resp = gateway.ping(host, port, sessionId);
            long rtt = System.currentTimeMillis() - t0;
            if ("PONG".equals(resp.type())) {
                status = "ok";
                lastRttMs = rtt;
                lastOkAt = Instant.now();
                log.debug("PONG session={} rtt={}ms", sessionId, rtt);
            } else {
                status = resp.get("code");
                log.warn("PING_ERR code={}", resp.get("code"));
            }
        } catch (Exception e) {
            status = "failed";
            log.warn("PING_ERR {}", e.getMessage());
        }
    }

    public boolean isActive() { return task != null && !task.isDone(); }
}
