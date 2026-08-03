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

    static MessageFrame login(String host, int port, String username, String password, String character) throws IOException {
        try (Socket socket = createSocket(host, port);
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("username", username);
            fields.put("password", password);
            fields.put("character", character);

            out.write(WireCodec.encode("LOGIN", fields));
            out.newLine();
            out.flush();

            String responseLine = in.readLine();
            if (responseLine == null || responseLine.isBlank()) {
                throw new IOException("Server closed connection without response");
            }

            return WireCodec.decode(responseLine);
        }
    }

    static MessageFrame ping(String host, int port, String sessionId) throws IOException {
        try (Socket socket = createSocket(host, port);
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("sessionId", sessionId);

            out.write(WireCodec.encode("PING", fields));
            out.newLine();
            out.flush();

            String responseLine = in.readLine();
            if (responseLine == null || responseLine.isBlank()) {
                throw new IOException("Server closed connection without response");
            }

            return WireCodec.decode(responseLine);
        }
    }

    static MessageFrame logout(String host, int port, String sessionId) throws IOException {
        try (Socket socket = createSocket(host, port);
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("sessionId", sessionId);

            out.write(WireCodec.encode("LOGOUT", fields));
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
