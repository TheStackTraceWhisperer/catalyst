package catalyst.client.network;

import catalyst.common.network.MessageFrame;
import catalyst.common.network.ResponseCode;
import catalyst.common.dto.*;
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
        MessageFrame reqFrame = ProtocolMapper.fromLoginRequest(new LoginRequest(username, password));
        MessageFrame respFrame = gateway.request(host, port, reqFrame);
        return ProtocolMapper.toLoginResponse(respFrame);
    }

    public CharListResponse listCharacters(String host, int port, String authToken) throws IOException {
        MessageFrame reqFrame = ProtocolMapper.fromCharListRequest(new CharListRequest(authToken));
        MessageFrame respFrame = gateway.request(host, port, reqFrame);
        return ProtocolMapper.toCharListResponse(respFrame);
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
        MessageFrame reqFrame = ProtocolMapper.fromCharCreateRequest(
            new CharCreateRequest(authToken, name, race, size, face, mainJob, nation));
        MessageFrame respFrame = gateway.request(host, port, reqFrame);
        return ProtocolMapper.toCharCreateResponse(respFrame);
    }

    public CharSelectResponse selectCharacter(String host, int port, String authToken, String characterId) throws IOException {
        long charId;
        try {
            charId = Long.parseLong(characterId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid characterId format: " + characterId, e);
        }
        MessageFrame reqFrame = ProtocolMapper.fromCharSelectRequest(new CharSelectRequest(authToken, charId));
        MessageFrame respFrame = gateway.request(host, port, reqFrame);
        return ProtocolMapper.toCharSelectResponse(respFrame);
    }

    public CharDeleteResponse deleteCharacter(String host, int port, String authToken, String characterId) throws IOException {
        long charId;
        try {
            charId = Long.parseLong(characterId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid characterId format: " + characterId, e);
        }
        MessageFrame reqFrame = ProtocolMapper.fromCharDeleteRequest(new CharDeleteRequest(authToken, charId));
        MessageFrame respFrame = gateway.request(host, port, reqFrame);
        return ProtocolMapper.toCharDeleteResponse(respFrame);
    }

    public PlayResponse play(String host, int port, String authToken, String characterId) throws IOException {
        long charId;
        try {
            charId = Long.parseLong(characterId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid characterId format: " + characterId, e);
        }
        MessageFrame reqFrame = ProtocolMapper.fromPlayRequest(new PlayRequest(authToken, charId));
        MessageFrame respFrame = gateway.request(host, port, reqFrame);
        return ProtocolMapper.toPlayResponse(respFrame);
    }

    public PingResponse ping(String host, int port, String sessionId) throws IOException {
        MessageFrame reqFrame = ProtocolMapper.fromPingRequest(new PingRequest(sessionId));
        MessageFrame respFrame = gateway.request(host, port, reqFrame);
        return ProtocolMapper.toPingResponse(respFrame);
    }

    public LogoutResponse logout(String host, int port, String sessionId) throws IOException {
        MessageFrame reqFrame = ProtocolMapper.fromLogoutRequest(new LogoutRequest(sessionId));
        MessageFrame respFrame = gateway.request(host, port, reqFrame);
        return ProtocolMapper.toLogoutResponse(respFrame);
    }

    @Override
    @PreDestroy
    public void close() {
        gateway.close();
    }
}
