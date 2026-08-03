package catalyst.ffxi.common.net;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

public final class WireCodec {
    private WireCodec() {
    }

    public static String encode(String type, Map<String, String> fields) {
        StringJoiner joiner = new StringJoiner("|");
        joiner.add(escape(type));

        for (Map.Entry<String, String> entry : fields.entrySet()) {
            joiner.add(escape(entry.getKey()) + "=" + escape(entry.getValue()));
        }

        return joiner.toString();
    }

    public static MessageFrame decode(String line) {
        String[] parts = line.split("\\|");
        if (parts.length == 0) {
            throw new IllegalArgumentException("Empty frame");
        }

        String type = unescape(parts[0]);
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            int idx = part.indexOf('=');
            if (idx < 0) {
                continue;
            }
            String key = unescape(part.substring(0, idx));
            String value = unescape(part.substring(idx + 1));
            fields.put(key, value);
        }

        return new MessageFrame(type, fields);
    }

    private static String escape(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String unescape(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
