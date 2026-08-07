package catalyst.client.network;

import catalyst.common.dto.*;
import catalyst.common.network.ResponseCode;
import jakarta.inject.Singleton;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @Singleton QUIC gateway service. Wraps QuicGateway (connection lifecycle) and exposes
 * typed network operations. All methods block until response or timeout.
 */
@Slf4j
@Singleton
public class QuicGatewayService implements AutoCloseable {

    private final QuicGateway gateway = new QuicGateway();

    public record CharacterSummary(
        String id,
        String name,
        String raceName,
        int size,
        int face,
        String jobName,
        int nation
    ) {
        public String nationName() {
            return switch (nation) {
                case 0 -> "Sandy";
                case 1 -> "Bastok";
                default -> "Windurst";
            };
        }
    }

    public LoginResponse login(String host, int port, String username, String password) throws IOException {
        return request(host, port, new LoginRequest(username, password), LoginResponse.class);
    }

    public CharListResponse listCharacters(String host, int port, String authToken) throws IOException {
        return request(host, port, new CharListRequest(authToken), CharListResponse.class);
    }

    public List<CharacterSummary> listCharacterSummaries(String host, int port, String authToken) throws IOException {
        CharListResponse resp = listCharacters(host, port, authToken);
        if (resp.code() != ResponseCode.OK) {
            throw new IOException("CHAR_LIST_ERR " + resp.code());
        }
        List<CharacterSummary> rows = new ArrayList<>(resp.characters().size());
        for (var c : resp.characters()) {
            rows.add(new CharacterSummary(
                c.id(),
                c.name(),
                c.raceName(),
                c.size(),
                c.face(),
                c.jobName(),
                c.nation()
            ));
        }
        return rows;
    }

    public CharCreateResponse createCharacter(String host, int port, String authToken,
                                         String name, int race, int size, int face, int mainJob, String nation) throws IOException {
        return request(host, port,
            new CharCreateRequest(authToken, name, race, size, face, mainJob, nation),
            CharCreateResponse.class);
    }

    public CharSelectResponse selectCharacter(String host, int port, String authToken, String characterId) throws IOException {
        long charId;
        try {
            charId = Long.parseLong(characterId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid characterId format: " + characterId, e);
        }
        return request(host, port, new CharSelectRequest(authToken, charId), CharSelectResponse.class);
    }

    public CharDeleteResponse deleteCharacter(String host, int port, String authToken, String characterId) throws IOException {
        long charId;
        try {
            charId = Long.parseLong(characterId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid characterId format: " + characterId, e);
        }
        return request(host, port, new CharDeleteRequest(authToken, charId), CharDeleteResponse.class);
    }

    public PlayResponse play(String host, int port, String authToken, String characterId) throws IOException {
        long charId;
        try {
            charId = Long.parseLong(characterId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid characterId format: " + characterId, e);
        }
        return request(host, port, new PlayRequest(authToken, charId), PlayResponse.class);
    }

    public PingResponse ping(String host, int port, String sessionId) throws IOException {
        return request(host, port, new PingRequest(sessionId), PingResponse.class);
    }

    public LogoutResponse logout(String host, int port, String sessionId) throws IOException {
        return request(host, port, new LogoutRequest(sessionId), LogoutResponse.class);
    }

    private <T> T request(String host, int port, Object dto, Class<T> responseType) throws IOException {
        return gateway.request(host, port, dto, responseType);
    }

    @Override
    @PreDestroy
    public void close() {
        gateway.close();
    }
}
