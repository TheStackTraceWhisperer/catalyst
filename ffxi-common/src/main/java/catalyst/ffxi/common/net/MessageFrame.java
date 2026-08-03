package catalyst.ffxi.common.net;

import java.util.Map;

public record MessageFrame(String type, Map<String, String> fields) {
    public String get(String key) {
        return fields.get(key);
    }
}
