package catalyst.ffxi.client;

import catalyst.ffxi.common.net.MessageFrame;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

final class RemoteGateway {
    private static final QuicGateway QUIC = new QuicGateway();

    private RemoteGateway() {
    }

    static MessageFrame login(String host, int port, String username, String password) throws IOException {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("username", username);
        fields.put("password", password);
        return QUIC.request(host, port, "LOGIN", fields);
    }

    static MessageFrame listCharacters(String host, int port, String authToken) throws IOException {
        return QUIC.request(host, port, "CHAR_LIST", Map.of("authToken", authToken));
    }

    static MessageFrame createCharacter(
        String host,
        int port,
        String authToken,
        String name,
        int race,
        int size,
        int face,
        int mainJob,
        String nation
    ) throws IOException {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("authToken", authToken);
        fields.put("name", name);
        fields.put("race", Integer.toString(race));
        fields.put("size", Integer.toString(size));
        fields.put("face", Integer.toString(face));
        fields.put("mainJob", Integer.toString(mainJob));
        fields.put("nation", nation);
        return QUIC.request(host, port, "CHAR_CREATE", fields);
    }

    static MessageFrame deleteCharacter(String host, int port, String authToken, String characterId) throws IOException {
        return QUIC.request(host, port, "CHAR_DELETE", Map.of("authToken", authToken, "characterId", characterId));
    }

    static MessageFrame selectCharacter(String host, int port, String authToken, String characterId) throws IOException {
        return QUIC.request(host, port, "CHAR_SELECT", Map.of("authToken", authToken, "characterId", characterId));
    }

    static MessageFrame play(String host, int port, String authToken, String characterId) throws IOException {
        return QUIC.request(host, port, "PLAY", Map.of("authToken", authToken, "characterId", characterId));
    }

    static MessageFrame ping(String host, int port, String sessionId) throws IOException {
        return QUIC.request(host, port, "PING", Map.of("sessionId", sessionId));
    }

    static MessageFrame logout(String host, int port, String sessionId) throws IOException {
        return QUIC.request(host, port, "LOGOUT", Map.of("sessionId", sessionId));
    }

    static void shutdown() {
        QUIC.close();
    }
}
