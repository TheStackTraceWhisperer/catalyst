package catalyst.server.lobby.service;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Singleton
public class WorldRegistryService {

  @Value("${catalyst.server.world.default-address:127.0.0.1:9003}")
  private String defaultWorldAddress;

  // Optional mapping for multi-instance zone routing (zoneId -> "host:port")
  private final Map<Integer, String> zoneServerMappings = new ConcurrentHashMap<>();

  public void registerZoneServer(int zoneId, String address) {
    zoneServerMappings.put(zoneId, address);
    log.info("Registered WorldServer instance address={} for zoneId={}", address, zoneId);
  }

  /**
   * Resolves the "host:port" world server address responsible for the target zone ID.
   */
  public String resolveWorldServerAddress(int zoneId) {
    return zoneServerMappings.getOrDefault(zoneId, defaultWorldAddress);
  }
}