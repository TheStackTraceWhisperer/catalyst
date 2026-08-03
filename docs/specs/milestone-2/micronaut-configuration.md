# Spec: Micronaut Configuration

## Purpose

Replace all hardcoded constants with Micronaut-managed configuration backed by `application.yml`. Establish consistent DI patterns across `ffxi-engine`, `ffxi-client`, and `ffxi-server`.

## Micronaut Version

`4.5.x` (align with `reference/october`). Annotation processor (`micronaut-inject-java`) must be added to each module's Maven build.

## Lombok + Micronaut

Micronaut's annotation processor generates constructor injection metadata at compile time. Combined with Lombok's `@RequiredArgsConstructor`, the result is zero-boilerplate injection:

```java
@Singleton
@RequiredArgsConstructor
@Slf4j
public class LoginHandler {
    private final AccountRepository accounts;
    private final AuthTicketStore tickets;
    // no @Inject needed — Micronaut reads the Lombok-generated constructor
}
```

`lombok.config` must set `lombok.addLombokGeneratedAnnotation = true` and Lombok must run before the Micronaut annotation processor in the build.

## Configuration Properties Pattern

Every tunable constant becomes a `@ConfigurationProperties` bean:

```java
@ConfigurationProperties("ffxi.server")
public interface ServerProperties {
    @Bindable(defaultValue = "35555") int getPort();
    @Bindable(defaultValue = "jdbc:postgresql://localhost:5432/ffxi") String getDbUrl();
    @Bindable(defaultValue = "ffxi") String getDbUser();
    String getDbPassword();
    @Bindable(defaultValue = "30") int getSessionTimeoutSeconds();
    @Bindable(defaultValue = "300") int getAuthTicketTimeoutSeconds();
    @Bindable(defaultValue = "8") int getDbPoolMaxSize();
}
```

```java
@ConfigurationProperties("ffxi.client")
public interface ClientProperties {
    @Bindable(defaultValue = "127.0.0.1") String getDefaultServerHost();
    @Bindable(defaultValue = "35555") int getDefaultServerPort();
    @Bindable(defaultValue = "5000") long getKeepaliveIntervalMs();
}
```

## `application.yml` Structure

### `ffxi-server/src/main/resources/application.yml`

```yaml
micronaut:
  banner:
    enabled: false

ffxi:
  server:
    port: 35555
    session-timeout-seconds: 30
    auth-ticket-timeout-seconds: 300
    db-url: jdbc:postgresql://localhost:5432/ffxi
    db-user: ffxi
    db-password: ffxi
    db-pool-max-size: 8
```

### `ffxi-server/src/main/resources/application-dev.yml`

```yaml
ffxi:
  server:
    db-password: ffxi   # dev default; override with env var in prod
```

### `ffxi-server/src/main/resources/application-prod.yml`

```yaml
ffxi:
  server:
    db-url: ${FFXI_DB_URL}
    db-user: ${FFXI_DB_USER}
    db-password: ${FFXI_DB_PASSWORD}
    port: ${FFXI_SERVER_PORT:35555}
```

### `ffxi-client/src/main/resources/application.yml`

```yaml
micronaut:
  banner:
    enabled: false

engine:
  window:
    width: 1280
    height: 720
    title: FFXI Client

ffxi:
  client:
    default-server-host: 127.0.0.1
    default-server-port: 35555
    keepalive-interval-ms: 5000
```

## Environment Variable Override

Micronaut maps environment variables to config keys automatically: `FFXI_SERVER_PORT` → `ffxi.server.port`. The `application-prod.yml` profile can use `${ENV_VAR:default}` syntax.

Activate profiles via:
- JVM system property: `-Dmicronaut.environments=prod`
- Environment variable: `MICRONAUT_ENVIRONMENTS=prod`
- Scripts: set in `up-server.sh` for prod deploys

## Hardcodes to Eliminate

| Location | Constant | Config key |
|---|---|---|
| `ServerMain` | `SESSION_TIMEOUT_SECONDS = 30` | `ffxi.server.session-timeout-seconds` |
| `ServerMain` | `AUTH_TICKET_TIMEOUT_SECONDS = 300` | `ffxi.server.auth-ticket-timeout-seconds` |
| `ServerMain` | `ARGON2_ITERATIONS = 3` | `ffxi.server.argon2-iterations` |
| `ServerMain` | `ARGON2_MEMORY_KIB = 65536` | `ffxi.server.argon2-memory-kib` |
| `ServerMain` | JDBC URL/user/password | `ffxi.server.db-*` |
| `ServerMain` | port `35555` | `ffxi.server.port` |
| `ClientMain` | `KEEPALIVE_INTERVAL_MS = 5000` | `ffxi.client.keepalive-interval-ms` |
| `ClientMain` | default host `127.0.0.1` | `ffxi.client.default-server-host` |
| `ClientMain` | default port `35555` | `ffxi.client.default-server-port` |
| `ClientMain` | window size `1280×720` | `engine.window.width/height` |

## Milestone 2 Done Criteria

- [ ] Micronaut annotation processor configured in all module poms
- [ ] `application.yml` exists in server and client with all tunable values
- [ ] `dev` and `prod` profiles exist and load without error
- [ ] Environment variable overrides work for all production-sensitive values
- [ ] No hardcoded numeric constants remain in handler/service/state classes
- [ ] Lombok + Micronaut constructor injection works across all beans
