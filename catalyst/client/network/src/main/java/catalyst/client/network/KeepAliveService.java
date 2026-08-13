package catalyst.client.network;

import catalyst.common.concurrency.TaskHandle;
import catalyst.common.concurrency.TaskScheduler;
import catalyst.common.concurrency.TaskStatus;
import catalyst.common.dto.world.PingRequest;
import catalyst.common.dto.world.PingResponse;
import catalyst.common.network.DecodedPacket;
import catalyst.common.network.PacketType;
import catalyst.common.network.ResponseCode;
import io.micronaut.context.BeanProvider;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class KeepAliveService {

    // Wrap QuicGatewayService in BeanProvider for lazy initialization
    private final BeanProvider<ClientTransportService> gatewayProvider;
    private final TaskScheduler taskScheduler;

    private TaskHandle<?> task;

    @Getter private volatile String status = "idle";
    @Getter private volatile long lastRttMs = -1;
    @Getter private volatile Instant lastOkAt = null;

    private String sessionId;
    private volatile long lastPingSentTimeMs;

    public void start(String sessionId, long intervalMs) {
        this.sessionId = sessionId;
        this.status = "connected";
        long interval = Math.max(250L, intervalMs);

        schedulePingLoop(interval);
        log.info("KeepAlive started session={} interval={}ms", sessionId, interval);
    }

    private void schedulePingLoop(long intervalMs) {
        task = taskScheduler.submit(() -> {
            sendPing();
            return null;
        }, (res) -> {
            if (isActive()) {
                taskScheduler.submit(() -> {
                    Thread.sleep(intervalMs);
                    return null;
                }, (r) -> {
                    if (isActive()) {
                        schedulePingLoop(intervalMs);
                    }
                }, (err) -> log.warn("Error in keepalive delay: {}", err.getMessage()));
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
        lastPingSentTimeMs = System.currentTimeMillis();
        try {
            PingRequest request = new PingRequest(lastPingSentTimeMs);
            DecodedPacket packet = new DecodedPacket(PacketType.PING_REQUEST, request);

            // Fetch gateway via provider when needed
            gatewayProvider.get().sendAsync(packet);
        } catch (Exception e) {
            status = "failed";
            log.warn("PING_ERR {}", e.getMessage());
        }
    }

    public void handlePong(PingResponse resp) {
        long rtt = System.currentTimeMillis() - lastPingSentTimeMs;
        if (resp.code() == ResponseCode.OK) {
            status = "ok";
            lastRttMs = rtt;
            lastOkAt = Instant.now();
            log.debug("PONG session={} rtt={}ms", sessionId, rtt);
        } else {
            status = resp.code() != null ? resp.code().name() : "error";
            log.warn("PING_ERR code={}", resp.code());
        }
    }

    public boolean isActive() {
        return task != null && task.getStatus() != TaskStatus.CANCELLED;
    }
}