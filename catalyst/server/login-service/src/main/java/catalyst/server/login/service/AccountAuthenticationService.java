package catalyst.server.login.service;

import catalyst.server.login.repository.AccountRepository;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class AccountAuthenticationService {

  private final AccountRepository accountRepository;

  public record AuthResult(boolean success, long accountId) {
    public boolean isSuccess() { return success; }
  }

  public CompletableFuture<AuthResult> authenticateAsync(String username, String password) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        Optional<AccountRepository.AccountRow> rowOpt = accountRepository.findByUsername(username);
        if (rowOpt.isEmpty()) {
          return new AuthResult(false, -1L);
        }

        AccountRepository.AccountRow row = rowOpt.get();
        Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
        char[] passwordChars = password.toCharArray();

        boolean verified;
        try {
          verified = argon2.verify(row.passwordHash(), passwordChars);
        } finally {
          argon2.wipeArray(passwordChars);
        }

        if (verified && "active".equalsIgnoreCase(row.status())) {
          return new AuthResult(true, row.id());
        }
      } catch (Exception e) {
        log.error("Database error during account authentication for user: {}", username, e);
      }
      return new AuthResult(false, -1L);
    });
  }

  public void bootstrapDevAccount(String username, String rawPassword) {
    try {
      if (!accountRepository.existsByUsername(username)) {
        Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
        String hash = argon2.hash(4, 65536, 1, rawPassword.toCharArray());
        accountRepository.insert(username, hash, "active");
        log.info("Bootstrapped default developer account: {}", username);
      }
    } catch (Exception e) {
      log.error("Failed to bootstrap dev account", e);
    }
  }
}