package catalyst.ffxi.client.network;

import catalyst.ffxi.common.net.MessageFrame;
import catalyst.ffxi.common.net.ResponseCode;
import catalyst.ffxi.common.net.dto.*;
import jakarta.inject.Singleton;
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
        LoginRequest req = LoginRequest.builder()
            .username(username)
            .password(password)
            .build();
        MessageFrame reqFrame = ProtocolMapper.fromLoginRequest(req);
        MessageFrame respFrame = gateway.request(host, port, reqFrame.type(), reqFrame.fields());
        return ProtocolMapper.toLoginResponse(respFrame);
    }

    public CharListResponse listCharacters(String host, int port, String authToken) throws IOException {
        CharListRequest req = CharListRequest.builder()
            .authToken(authToken)
            .build();
        MessageFrame reqFrame = ProtocolMapper.fromCharListRequest(req);
        MessageFrame respFrame = gateway.request(host, port, reqFrame.type(), reqFrame.fields());
        return ProtocolMapper.toCharListResponse(respFrame);
    }

    public List<CharacterSummary> listCharacterSummaries(String host, int port, String authToken) throws IOException {
        CharListResponse resp = listCharacters(host, port, authToken);
        if (resp.getCode() != ResponseCode.OK) {
            throw new IOException("CHAR_LIST_ERR " + resp.getCode());
        }
        List<CharacterSummary> rows = new ArrayList<>(resp.getCharacters().size());
        for (var c : resp.getCharacters()) {
            rows.add(new CharacterSummary(
                c.getId(),
                c.getName(),
                c.getRaceName(),
                c.getSize(),
                c.getFace(),
                c.getJobName(),
                c.getNation()
            ));
        }
        return rows;
    }

    public CharCreateResponse createCharacter(String host, int port, String authToken,
                                         String name, int race, int size, int face, int mainJob, String nation) throws IOException {
        CharCreateRequest req = CharCreateRequest.builder()
            .authToken(authToken)
            .name(name)
            .race(race)
            .size(size)
            .face(face)
            .mainJob(mainJob)
            .nation(nation)
            .build();
        MessageFrame reqFrame = ProtocolMapper.fromCharCreateRequest(req);
        MessageFrame respFrame = gateway.request(host, port, reqFrame.type(), reqFrame.fields());
        return ProtocolMapper.toCharCreateResponse(respFrame);
    }

    public CharSelectResponse selectCharacter(String host, int port, String authToken, String characterId) throws IOException {
        long charId;
        try {
            charId = Long.parseLong(characterId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid characterId format: " + characterId, e);
        }
        CharSelectRequest req = CharSelectRequest.builder()
            .authToken(authToken)
            .characterId(charId)
            .build();
        MessageFrame reqFrame = ProtocolMapper.fromCharSelectRequest(req);
        MessageFrame respFrame = gateway.request(host, port, reqFrame.type(), reqFrame.fields());
        return ProtocolMapper.toCharSelectResponse(respFrame);
    }

    public CharDeleteResponse deleteCharacter(String host, int port, String authToken, String characterId) throws IOException {
        long charId;
        try {
            charId = Long.parseLong(characterId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid characterId format: " + characterId, e);
        }
        CharDeleteRequest req = CharDeleteRequest.builder()
            .authToken(authToken)
            .characterId(charId)
            .build();
        MessageFrame reqFrame = ProtocolMapper.fromCharDeleteRequest(req);
        MessageFrame respFrame = gateway.request(host, port, reqFrame.type(), reqFrame.fields());
        return ProtocolMapper.toCharDeleteResponse(respFrame);
    }

    public PlayResponse play(String host, int port, String authToken, String characterId) throws IOException {
        long charId;
        try {
            charId = Long.parseLong(characterId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid characterId format: " + characterId, e);
        }
        PlayRequest req = PlayRequest.builder()
            .authToken(authToken)
            .characterId(charId)
            .build();
        MessageFrame reqFrame = ProtocolMapper.fromPlayRequest(req);
        MessageFrame respFrame = gateway.request(host, port, reqFrame.type(), reqFrame.fields());
        return ProtocolMapper.toPlayResponse(respFrame);
    }

    public PingResponse ping(String host, int port, String sessionId) throws IOException {
        PingRequest req = PingRequest.builder()
            .sessionId(sessionId)
            .build();
        MessageFrame reqFrame = ProtocolMapper.fromPingRequest(req);
        MessageFrame respFrame = gateway.request(host, port, reqFrame.type(), reqFrame.fields());
        return ProtocolMapper.toPingResponse(respFrame);
    }

    public LogoutResponse logout(String host, int port, String sessionId) throws IOException {
        LogoutRequest req = LogoutRequest.builder()
            .sessionId(sessionId)
            .build();
        MessageFrame reqFrame = ProtocolMapper.fromLogoutRequest(req);
        MessageFrame respFrame = gateway.request(host, port, reqFrame.type(), reqFrame.fields());
        return ProtocolMapper.toLogoutResponse(respFrame);
    }

    @Override
    public void close() {
        gateway.close();
    }
}
