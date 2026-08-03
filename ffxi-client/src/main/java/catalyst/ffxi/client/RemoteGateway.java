package catalyst.ffxi.client;

import catalyst.ffxi.common.net.MessageFrame;
import catalyst.ffxi.common.net.WireCodec;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

final class RemoteGateway {
    private RemoteGateway() {
    }

    static MessageFrame login(String host, int port, String username, String password) throws IOException {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("username", username);
        fields.put("password", password);
        return request(host, port, "LOGIN", fields);
    }

    static MessageFrame listCharacters(String host, int port, String authToken) throws IOException {
        return request(host, port, "CHAR_LIST", Map.of("authToken", authToken));
    }

    static MessageFrame createCharacter(
        String host,
        int port,
        String authToken,
        String name,
        String race,
        String gender,
        int face,
        String city
    ) throws IOException {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("authToken", authToken);
        fields.put("name", name);
        fields.put("race", race);
        fields.put("gender", gender);
        fields.put("face", Integer.toString(face));
        fields.put("city", city);
        return request(host, port, "CHAR_CREATE", fields);
    }

    static MessageFrame deleteCharacter(String host, int port, String authToken, String characterId) throws IOException {
        return request(host, port, "CHAR_DELETE", Map.of("authToken", authToken, "characterId", characterId));
    }

    static MessageFrame selectCharacter(String host, int port, String authToken, String characterId) throws IOException {
        return request(host, port, "CHAR_SELECT", Map.of("authToken", authToken, "characterId", characterId));
    }

    static MessageFrame play(String host, int port, String authToken, String characterId) throws IOException {
        return request(host, port, "PLAY", Map.of("authToken", authToken, "characterId", characterId));
    }

    static MessageFrame ping(String host, int port, String sessionId) throws IOException {
        return request(host, port, "PING", Map.of("sessionId", sessionId));
    }

    static MessageFrame logout(String host, int port, String sessionId) throws IOException {
        return request(host, port, "LOGOUT", Map.of("sessionId", sessionId));
    }

    private static MessageFrame request(String host, int port, String type, Map<String, String> fields) throws IOException {
        try (Socket socket = createSocket(host, port);
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            out.write(WireCodec.encode(type, fields));
            out.newLine();
            out.flush();

            String responseLine = in.readLine();
            if (responseLine == null || responseLine.isBlank()) {
                throw new IOException("Server closed connection without response");
            }

            return WireCodec.decode(responseLine);
        }
    }

    private static Socket createSocket(String host, int port) throws IOException {
        Socket socket = new Socket(host, port);
        socket.setSoTimeout(2000);
        return socket;
    }
}
