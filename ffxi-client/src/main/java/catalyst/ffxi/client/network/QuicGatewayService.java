package catalyst.ffxi.client.network;

import catalyst.ffxi.common.net.MessageFrame;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        var fields = new java.util.LinkedHashMap<String, String>();
        fields.put("username", username);
        fields.put("password", password);
        return gateway.request(host, port, "LOGIN", fields);
    }

    public MessageFrame listCharacters(String host, int port, String authToken) throws IOException {
        return gateway.request(host, port, "CHAR_LIST", Map.of("authToken", authToken));
    }

    public List<CharacterSummary> listCharacterSummaries(String host, int port, String authToken) throws IOException {
        MessageFrame resp = listCharacters(host, port, authToken);
        if (!"CHAR_LIST_OK".equals(resp.type())) {
            throw new IOException("CHAR_LIST_ERR " + resp.get("code"));
        }
        int count = resp.getInt("count", 0);
        List<CharacterSummary> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new CharacterSummary(
                resp.get("char" + i + "_id"),
                resp.get("char" + i + "_name"),
                resp.get("char" + i + "_raceName"),
                resp.getInt("char" + i + "_size", 1),
                resp.getInt("char" + i + "_face", 0),
                resp.get("char" + i + "_jobName"),
                resp.getInt("char" + i + "_nation", 0)
            ));
        }
        return rows;
    }

    public MessageFrame createCharacter(String host, int port, String authToken,
                                         String name, int race, int size, int face, int mainJob, String nation) throws IOException {
        var fields = new java.util.LinkedHashMap<String, String>();
        fields.put("authToken", authToken);
        fields.put("name", name);
        fields.put("race", Integer.toString(race));
        fields.put("size", Integer.toString(size));
        fields.put("face", Integer.toString(face));
        fields.put("mainJob", Integer.toString(mainJob));
        fields.put("nation", nation);
        return gateway.request(host, port, "CHAR_CREATE", fields);
    }

    public MessageFrame selectCharacter(String host, int port, String authToken, String characterId) throws IOException {
        return gateway.request(host, port, "CHAR_SELECT", Map.of("authToken", authToken, "characterId", characterId));
    }

    public MessageFrame deleteCharacter(String host, int port, String authToken, String characterId) throws IOException {
        return gateway.request(host, port, "CHAR_DELETE", Map.of("authToken", authToken, "characterId", characterId));
    }

    public MessageFrame play(String host, int port, String authToken, String characterId) throws IOException {
        return gateway.request(host, port, "PLAY", Map.of("authToken", authToken, "characterId", characterId));
    }

    public MessageFrame ping(String host, int port, String sessionId) throws IOException {
        return gateway.request(host, port, "PING", Map.of("sessionId", sessionId));
    }

    public MessageFrame logout(String host, int port, String sessionId) throws IOException {
        return gateway.request(host, port, "LOGOUT", Map.of("sessionId", sessionId));
    }

    @Override
    public void close() {
        gateway.close();
    }
}
