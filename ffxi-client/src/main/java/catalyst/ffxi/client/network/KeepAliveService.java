package catalyst.ffxi.client.network;

import catalyst.ffxi.common.net.MessageFrame;
import catalyst.ffxi.common.concurrency.TaskHandle;
import catalyst.ffxi.common.concurrency.TaskScheduler;
import catalyst.ffxi.common.concurrency.TaskStatus;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class KeepAliveService {

    private final QuicGatewayService gateway;
    private final TaskScheduler taskScheduler;

    private TaskHandle<?> task;

    @Getter private volatile String status = "idle";
    @Getter private volatile long lastRttMs = -1;
    @Getter private volatile Instant lastOkAt = null;

    private String host;
    private int port;
    private String sessionId;

    public void start(String host, int port, String sessionId, long intervalMs) {
        this.host = host;
        this.port = port;
        this.sessionId = sessionId;
        this.status = "connected";
        long interval = Math.max(250L, intervalMs);
        
        // Schedule keep alive loop using TaskScheduler
        schedulePingLoop(interval);
        log.info("KeepAlive started session={} interval={}ms", sessionId, interval);
    }

    private void schedulePingLoop(long intervalMs) {
        task = taskScheduler.submit(() -> {
            sendPing();
            return null;
        }, (res) -> {
            // After successful ping processing (success callback runs on main thread), schedule the next ping
            if (isActive()) {
                taskScheduler.submit(() -> {
                    Thread.sleep(intervalMs);
                    return null;
                }, (r) -> {
                    if (isActive()) {
                        schedulePingLoop(intervalMs);
                    }
                }, (err) -> {
                    log.warn("Error in keepalive delay: {}", err.getMessage());
                });
            }
        }, (err) -> {
            log.warn("Error running ping: {}", err.getMessage());
            if (isActive()) {
                taskScheduler.submit(() -> {
                    Thread.sleep(intervalMs);
                    return null;
                }, (r) -> {
                    if (isActive()) {
                        schedulePingLoop(intervalMs);
                    }
                }, (e) -> {});
            }
        });
    }

    public void stop() {
        if (task != null) { 
            task.cancel(true); 
            task = null; 
        }
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

    public boolean isActive() { return task != null && task.getStatus() != TaskStatus.CANCELLED; }
}
