package catalyst.server.lobby.service;

import catalyst.server.lobby.repository.CharacterRepository;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class CharacterService {

  private final CharacterRepository characterRepository;

  /**
   * Asynchronously fetches the current zone ID for a target character.
   * Offloaded to a Virtual Thread to avoid blocking Netty EventLoops during JDBC reads.
   */
  public CompletableFuture<Integer> getCharacterZoneIdAsync(long characterId) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        return characterRepository.findCurrentZoneId(characterId).orElse(null);
      } catch (Exception e) {
        log.error("Failed to fetch zone ID for characterId={}", characterId, e);
        return null;
      }
    });
  }
}