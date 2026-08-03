package catalyst.ffxi.common.net;

import java.util.LinkedHashMap;
import java.util.Map;

public record MessageFrame(String type, Map<String, String> fields) {

    public static final String VERSION_KEY     = "_v";
    public static final int    CURRENT_VERSION = 2;

    // ── Field accessors ─────────────────────────────────────────────────────

    public String get(String key) {
        return fields.get(key);
    }

    public int getInt(String key, int fallback) {
        String v = fields.get(key);
        if (v == null || v.isBlank()) return fallback;
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return fallback; }
    }

    public long getLong(String key, long fallback) {
        String v = fields.get(key);
        if (v == null || v.isBlank()) return fallback;
        try { return Long.parseLong(v); } catch (NumberFormatException e) { return fallback; }
    }

    public float getFloat(String key, float fallback) {
        String v = fields.get(key);
        if (v == null || v.isBlank()) return fallback;
        try { return Float.parseFloat(v); } catch (NumberFormatException e) { return fallback; }
    }

    public boolean getBoolean(String key, boolean fallback) {
        String v = fields.get(key);
        if (v == null || v.isBlank()) return fallback;
        return "true".equalsIgnoreCase(v) || "1".equals(v);
    }

    public int protocolVersion() {
        return getInt(VERSION_KEY, 1);
    }

    // ── Builder ──────────────────────────────────────────────────────────────

    public static Builder builder(String type) {
        return new Builder(type);
    }

    public static final class Builder {
        private final String type;
        private final Map<String, String> fields = new LinkedHashMap<>();

        private Builder(String type) {
            this.type = type;
            fields.put(VERSION_KEY, Integer.toString(CURRENT_VERSION));
        }

        public Builder put(String key, String value) {
            fields.put(key, value == null ? "" : value);
            return this;
        }

        public Builder put(String key, int value) {
            fields.put(key, Integer.toString(value));
            return this;
        }

        public Builder put(String key, long value) {
            fields.put(key, Long.toString(value));
            return this;
        }

        public Builder put(String key, float value) {
            fields.put(key, Float.toString(value));
            return this;
        }

        public Builder put(String key, boolean value) {
            fields.put(key, Boolean.toString(value));
            return this;
        }

        public MessageFrame build() {
            return new MessageFrame(type, Map.copyOf(fields));
        }
    }
}
