package catalyst.ffxi.common.net.dto;

import catalyst.ffxi.common.net.MessageFrame;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProtocolMapper {

    private ProtocolMapper() {}

    // ── Login Mapping ───────────────────────────────────────────────────────

    public static LoginRequest toLoginRequest(MessageFrame frame) {
        validateRequired(frame, "username", "password");
        return LoginRequest.builder()
            .username(frame.get("username"))
            .password(frame.get("password"))
            .build();
    }

    public static MessageFrame fromLoginRequest(LoginRequest req) {
        return MessageFrame.builder("LOGIN")
            .put("username", req.getUsername())
            .put("password", req.getPassword())
            .build();
    }

    public static LoginResponse toLoginResponse(MessageFrame frame) {
        return LoginResponse.builder()
            .code(frame.get("code"))
            .message(frame.get("message"))
            .authToken(frame.get("authToken"))
            .accountId(frame.getLong("accountId", -1))
            .build();
    }

    public static MessageFrame fromLoginResponse(LoginResponse resp) {
        var builder = MessageFrame.builder("LOGIN_OK");
        if (resp.getCode() != null) builder.put("code", resp.getCode());
        if (resp.getMessage() != null) builder.put("message", resp.getMessage());
        if (resp.getAuthToken() != null) builder.put("authToken", resp.getAuthToken());
        builder.put("accountId", resp.getAccountId());
        return builder.build();
    }

    // ── Char List Mapping ────────────────────────────────────────────────────

    public static CharListRequest toCharListRequest(MessageFrame frame) {
        validateRequired(frame, "authToken");
        return CharListRequest.builder()
            .authToken(frame.get("authToken"))
            .build();
    }

    public static MessageFrame fromCharListRequest(CharListRequest req) {
        return MessageFrame.builder("CHAR_LIST")
            .put("authToken", req.getAuthToken())
            .build();
    }

    public static CharListResponse toCharListResponse(MessageFrame frame) {
        int count = frame.getInt("count", 0);
        List<CharListResponse.CharacterDto> characters = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            characters.add(CharListResponse.CharacterDto.builder()
                .id(frame.get("char" + i + "_id"))
                .name(frame.get("char" + i + "_name"))
                .race(frame.getInt("char" + i + "_race", 0))
                .raceName(frame.get("char" + i + "_raceName"))
                .size(frame.getInt("char" + i + "_size", 1))
                .face(frame.getInt("char" + i + "_face", 0))
                .mainJob(frame.getInt("char" + i + "_mainJob", 1))
                .jobName(frame.get("char" + i + "_jobName"))
                .nation(frame.getInt("char" + i + "_nation", 0))
                .zone(frame.getInt("char" + i + "_zone", 0))
                .build());
        }
        return CharListResponse.builder()
            .code("OK")
            .characters(characters)
            .build();
    }

    public static MessageFrame fromCharListResponse(CharListResponse resp) {
        var builder = MessageFrame.builder("CHAR_LIST_OK");
        builder.put("count", resp.getCharacters().size());
        for (int i = 0; i < resp.getCharacters().size(); i++) {
            var c = resp.getCharacters().get(i);
            builder.put("char" + i + "_id", c.getId())
                   .put("char" + i + "_name", c.getName())
                   .put("char" + i + "_race", c.getRace())
                   .put("char" + i + "_raceName", c.getRaceName())
                   .put("char" + i + "_size", c.getSize())
                   .put("char" + i + "_face", c.getFace())
                   .put("char" + i + "_mainJob", c.getMainJob())
                   .put("char" + i + "_jobName", c.getJobName())
                   .put("char" + i + "_nation", c.getNation())
                   .put("char" + i + "_zone", c.getZone());
        }
        return builder.build();
    }

    // ── Char Create Mapping ──────────────────────────────────────────────────

    public static CharCreateRequest toCharCreateRequest(MessageFrame frame) {
        validateRequired(frame, "authToken", "name", "race", "size", "face", "mainJob", "nation");
        return CharCreateRequest.builder()
            .authToken(frame.get("authToken"))
            .name(frame.get("name"))
            .race(frame.getInt("race", -1))
            .size(frame.getInt("size", -1))
            .face(frame.getInt("face", -1))
            .mainJob(frame.getInt("mainJob", 1))
            .nation(frame.get("nation"))
            .build();
    }

    public static MessageFrame fromCharCreateRequest(CharCreateRequest req) {
        return MessageFrame.builder("CHAR_CREATE")
            .put("authToken", req.getAuthToken())
            .put("name", req.getName())
            .put("race", req.getRace())
            .put("size", req.getSize())
            .put("face", req.getFace())
            .put("mainJob", req.getMainJob())
            .put("nation", req.getNation())
            .build();
    }

    public static CharCreateResponse toCharCreateResponse(MessageFrame frame) {
        return CharCreateResponse.builder()
            .code(frame.get("code"))
            .message(frame.get("message"))
            .characterId(frame.getLong("characterId", -1))
            .name(frame.get("name"))
            .build();
    }

    public static MessageFrame fromCharCreateResponse(CharCreateResponse resp) {
        var builder = MessageFrame.builder("CHAR_CREATE_OK");
        if (resp.getCode() != null) builder.put("code", resp.getCode());
        if (resp.getMessage() != null) builder.put("message", resp.getMessage());
        builder.put("characterId", resp.getCharacterId());
        if (resp.getName() != null) builder.put("name", resp.getName());
        return builder.build();
    }

    // ── Char Select Mapping ──────────────────────────────────────────────────

    public static CharSelectRequest toCharSelectRequest(MessageFrame frame) {
        validateRequired(frame, "authToken", "characterId");
        return CharSelectRequest.builder()
            .authToken(frame.get("authToken"))
            .characterId(frame.getLong("characterId", -1))
            .build();
    }

    public static MessageFrame fromCharSelectRequest(CharSelectRequest req) {
        return MessageFrame.builder("CHAR_SELECT")
            .put("authToken", req.getAuthToken())
            .put("characterId", req.getCharacterId())
            .build();
    }

    public static CharSelectResponse toCharSelectResponse(MessageFrame frame) {
        return CharSelectResponse.builder()
            .code(frame.get("code"))
            .message(frame.get("message"))
            .characterId(frame.getLong("characterId", -1))
            .characterName(frame.get("characterName"))
            .homeZoneId(frame.getInt("homeZoneId", 0))
            .currentZoneId(frame.getInt("currentZoneId", 0))
            .x(frame.getFloat("x", 0f))
            .y(frame.getFloat("y", 0f))
            .z(frame.getFloat("z", 0f))
            .rot(frame.getFloat("rot", 0f))
            .build();
    }

    public static MessageFrame fromCharSelectResponse(CharSelectResponse resp) {
        var builder = MessageFrame.builder("CHAR_SELECT_OK");
        if (resp.getCode() != null) builder.put("code", resp.getCode());
        if (resp.getMessage() != null) builder.put("message", resp.getMessage());
        builder.put("characterId", resp.getCharacterId());
        if (resp.getCharacterName() != null) builder.put("characterName", resp.getCharacterName());
        builder.put("homeZoneId", resp.getHomeZoneId())
               .put("currentZoneId", resp.getCurrentZoneId())
               .put("x", resp.getX())
               .put("y", resp.getY())
               .put("z", resp.getZ())
               .put("rot", resp.getRot());
        return builder.build();
    }

    // ── Char Delete Mapping ──────────────────────────────────────────────────

    public static CharDeleteRequest toCharDeleteRequest(MessageFrame frame) {
        validateRequired(frame, "authToken", "characterId");
        return CharDeleteRequest.builder()
            .authToken(frame.get("authToken"))
            .characterId(frame.getLong("characterId", -1))
            .build();
    }

    public static MessageFrame fromCharDeleteRequest(CharDeleteRequest req) {
        return MessageFrame.builder("CHAR_DELETE")
            .put("authToken", req.getAuthToken())
            .put("characterId", req.getCharacterId())
            .build();
    }

    public static CharDeleteResponse toCharDeleteResponse(MessageFrame frame) {
        return CharDeleteResponse.builder()
            .code(frame.get("code"))
            .message(frame.get("message"))
            .characterId(frame.getLong("characterId", -1))
            .build();
    }

    public static MessageFrame fromCharDeleteResponse(CharDeleteResponse resp) {
        var builder = MessageFrame.builder("CHAR_DELETE_OK");
        if (resp.getCode() != null) builder.put("code", resp.getCode());
        if (resp.getMessage() != null) builder.put("message", resp.getMessage());
        builder.put("characterId", resp.getCharacterId());
        return builder.build();
    }

    // ── Play Mapping ─────────────────────────────────────────────────────────

    public static PlayRequest toPlayRequest(MessageFrame frame) {
        validateRequired(frame, "authToken", "characterId");
        return PlayRequest.builder()
            .authToken(frame.get("authToken"))
            .characterId(frame.getLong("characterId", -1))
            .build();
    }

    public static MessageFrame fromPlayRequest(PlayRequest req) {
        return MessageFrame.builder("PLAY")
            .put("authToken", req.getAuthToken())
            .put("characterId", req.getCharacterId())
            .build();
    }

    public static PlayResponse toPlayResponse(MessageFrame frame) {
        return PlayResponse.builder()
            .code(frame.get("code"))
            .message(frame.get("message"))
            .sessionId(frame.get("sessionId"))
            .accountId(frame.getLong("accountId", -1))
            .characterId(frame.getLong("characterId", -1))
            .characterName(frame.get("characterName"))
            .zoneId(frame.getInt("zoneId", 0))
            .playersInZone(frame.getInt("playersInZone", 0))
            .keepaliveIntervalMs(frame.getLong("keepaliveIntervalMs", 5000L))
            .homeZoneId(frame.getInt("homeZoneId", 0))
            .x(frame.getFloat("x", 0f))
            .y(frame.getFloat("y", 0f))
            .z(frame.getFloat("z", 0f))
            .rot(frame.getFloat("rot", 0f))
            .build();
    }

    public static MessageFrame fromPlayResponse(PlayResponse resp) {
        var builder = MessageFrame.builder("PLAY_OK");
        if (resp.getCode() != null) builder.put("code", resp.getCode());
        if (resp.getMessage() != null) builder.put("message", resp.getMessage());
        if (resp.getSessionId() != null) builder.put("sessionId", resp.getSessionId());
        builder.put("accountId", resp.getAccountId())
               .put("characterId", resp.getCharacterId());
        if (resp.getCharacterName() != null) builder.put("characterName", resp.getCharacterName());
        builder.put("zoneId", resp.getZoneId())
               .put("playersInZone", resp.getPlayersInZone())
               .put("keepaliveIntervalMs", resp.getKeepaliveIntervalMs())
               .put("homeZoneId", resp.getHomeZoneId())
               .put("x", resp.getX())
               .put("y", resp.getY())
               .put("z", resp.getZ())
               .put("rot", resp.getRot());
        return builder.build();
    }

    // ── Ping Mapping ─────────────────────────────────────────────────────────

    public static PingRequest toPingRequest(MessageFrame frame) {
        validateRequired(frame, "sessionId");
        return PingRequest.builder()
            .sessionId(frame.get("sessionId"))
            .build();
    }

    public static MessageFrame fromPingRequest(PingRequest req) {
        return MessageFrame.builder("PING")
            .put("sessionId", req.getSessionId())
            .build();
    }

    public static PingResponse toPingResponse(MessageFrame frame) {
        return PingResponse.builder()
            .type(frame.type())
            .sessionId(frame.get("sessionId"))
            .code(frame.get("code"))
            .message(frame.get("message"))
            .build();
    }

    public static MessageFrame fromPingResponse(PingResponse resp) {
        var builder = MessageFrame.builder(resp.getType() != null ? resp.getType() : "PONG");
        if (resp.getSessionId() != null) builder.put("sessionId", resp.getSessionId());
        if (resp.getCode() != null) builder.put("code", resp.getCode());
        if (resp.getMessage() != null) builder.put("message", resp.getMessage());
        return builder.build();
    }

    // ── Logout Mapping ───────────────────────────────────────────────────────

    public static LogoutRequest toLogoutRequest(MessageFrame frame) {
        validateRequired(frame, "sessionId");
        return LogoutRequest.builder()
            .sessionId(frame.get("sessionId"))
            .build();
    }

    public static MessageFrame fromLogoutRequest(LogoutRequest req) {
        return MessageFrame.builder("LOGOUT")
            .put("sessionId", req.getSessionId())
            .build();
    }

    public static LogoutResponse toLogoutResponse(MessageFrame frame) {
        return LogoutResponse.builder()
            .sessionId(frame.get("sessionId"))
            .code(frame.get("code"))
            .message(frame.get("message"))
            .build();
    }

    public static MessageFrame fromLogoutResponse(LogoutResponse resp) {
        var builder = MessageFrame.builder("BYE");
        if (resp.getSessionId() != null) builder.put("sessionId", resp.getSessionId());
        if (resp.getCode() != null) builder.put("code", resp.getCode());
        if (resp.getMessage() != null) builder.put("message", resp.getMessage());
        return builder.build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void validateRequired(MessageFrame frame, String... keys) {
        for (String key : keys) {
            String val = frame.get(key);
            if (val == null || val.isBlank()) {
                throw new IllegalArgumentException("Missing required protocol field: '" + key + "'");
            }
        }
    }
}
