package catalyst.ffxi.client.network;

import catalyst.ffxi.common.net.MessageFrame;
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

    public MessageFrame login(String host, int port, String username, String password) throws IOException {
        LoginRequest req = LoginRequest.builder()
            .username(username)
            .password(password)
            .build();
        MessageFrame reqFrame = ProtocolMapper.fromLoginRequest(req);
        return gateway.request(host, port, reqFrame.type(), reqFrame.fields());
    }

    public MessageFrame listCharacters(String host, int port, String authToken) throws IOException {
        CharListRequest req = CharListRequest.builder()
            .authToken(authToken)
            .build();
        MessageFrame reqFrame = ProtocolMapper.fromCharListRequest(req);
        return gateway.request(host, port, reqFrame.type(), reqFrame.fields());
    }

    public List<CharacterSummary> listCharacterSummaries(String host, int port, String authToken) throws IOException {
        MessageFrame respFrame = listCharacters(host, port, authToken);
        if (!"CHAR_LIST_OK".equals(respFrame.type())) {
            throw new IOException("CHAR_LIST_ERR " + respFrame.get("code"));
        }
        CharListResponse resp = ProtocolMapper.toCharListResponse(respFrame);
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

    public MessageFrame createCharacter(String host, int port, String authToken,
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
        return gateway.request(host, port, reqFrame.type(), reqFrame.fields());
    }

    public MessageFrame selectCharacter(String host, int port, String authToken, String characterId) throws IOException {
        long charId = -1;
        try { charId = Long.parseLong(characterId); } catch (NumberFormatException ignored) {}
        CharSelectRequest req = CharSelectRequest.builder()
            .authToken(authToken)
            .characterId(charId)
            .build();
        MessageFrame reqFrame = ProtocolMapper.fromCharSelectRequest(req);
        return gateway.request(host, port, reqFrame.type(), reqFrame.fields());
    }

    public MessageFrame deleteCharacter(String host, int port, String authToken, String characterId) throws IOException {
        long charId = -1;
        try { charId = Long.parseLong(characterId); } catch (NumberFormatException ignored) {}
        CharDeleteRequest req = CharDeleteRequest.builder()
            .authToken(authToken)
            .characterId(charId)
            .build();
        MessageFrame reqFrame = ProtocolMapper.fromCharDeleteRequest(req);
        return gateway.request(host, port, reqFrame.type(), reqFrame.fields());
    }

    public MessageFrame play(String host, int port, String authToken, String characterId) throws IOException {
        long charId = -1;
        try { charId = Long.parseLong(characterId); } catch (NumberFormatException ignored) {}
        PlayRequest req = PlayRequest.builder()
            .authToken(authToken)
            .characterId(charId)
            .build();
        MessageFrame reqFrame = ProtocolMapper.fromPlayRequest(req);
        return gateway.request(host, port, reqFrame.type(), reqFrame.fields());
    }

    public MessageFrame ping(String host, int port, String sessionId) throws IOException {
        PingRequest req = PingRequest.builder()
            .sessionId(sessionId)
            .build();
        MessageFrame reqFrame = ProtocolMapper.fromPingRequest(req);
        return gateway.request(host, port, reqFrame.type(), reqFrame.fields());
    }

    public MessageFrame logout(String host, int port, String sessionId) throws IOException {
        LogoutRequest req = LogoutRequest.builder()
            .sessionId(sessionId)
            .build();
        MessageFrame reqFrame = ProtocolMapper.fromLogoutRequest(req);
        return gateway.request(host, port, reqFrame.type(), reqFrame.fields());
    }

    @Override
    public void close() {
        gateway.close();
    }
}
