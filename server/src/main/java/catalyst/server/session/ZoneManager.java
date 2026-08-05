package catalyst.server.session;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Singleton
public class ZoneManager {

    private final ConcurrentHashMap<String, Integer> sessionZones  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, AtomicInteger> population = new ConcurrentHashMap<>();

    /** Called on PLAY. Returns updated player count for the zone. */
    public int join(String sessionId, int zoneId) {
        sessionZones.put(sessionId, zoneId);
        int count = population.computeIfAbsent(zoneId, z -> new AtomicInteger(0)).incrementAndGet();
        log.info("ZONE_ENTER zone={} session={} playersInZone={}", zoneId, sessionId, count);
        return count;
    }

    /** Called on LOGOUT / timeout. */
    public void leave(String sessionId) {
        Integer zoneId = sessionZones.remove(sessionId);
        if (zoneId == null) return;
        AtomicInteger counter = population.get(zoneId);
        if (counter == null) return;
        int remaining = Math.max(0, counter.decrementAndGet());
        if (remaining == 0) population.remove(zoneId, counter);
        log.info("ZONE_LEAVE zone={} session={} playersInZone={}", zoneId, sessionId, remaining);
    }

    public int getPopulation(int zoneId) {
        AtomicInteger c = population.get(zoneId);
        return c == null ? 0 : c.get();
    }
}
