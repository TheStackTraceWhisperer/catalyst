package catalyst.common.dto;

import catalyst.common.network.MessageFrame;
import catalyst.common.network.ResponseCode;
import java.util.ArrayList;
import java.util.List;

public final class ProtocolMapper {

    private ProtocolMapper() {}

    private static ResponseCode parseCode(String codeStr) {
        if (codeStr == null) return null;
        try {
            return ResponseCode.valueOf(codeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseCode.ERROR;
        }
    }

    private static String stringifyCode(ResponseCode code) {
        return code != null ? code.name() : null;
    }

    // ── Login Mapping ───────────────────────────────────────────────────────

    public static LoginRequest toLoginRequest(MessageFrame frame) {
        validateRequired(frame, "username", "password");
        return new LoginRequest(
            frame.get("username"),
            frame.get("password")
        );
    }

    public static MessageFrame fromLoginRequest(LoginRequest req) {
        return MessageFrame.builder("LOGIN")
            .put("username", req.username())
            .put("password", req.password())
            .build();
    }

    public static LoginResponse toLoginResponse(MessageFrame frame) {
        ResponseCode code = "LOGIN_OK".equals(frame.type()) ? ResponseCode.OK : parseCode(frame.get("code"));
        return new LoginResponse(
            code,
            frame.get("message"),
            frame.get("authToken"),
            frame.getLong("accountId", -1)
        );
    }

    public static MessageFrame fromLoginResponse(LoginResponse resp) {
        var builder = MessageFrame.builder(resp.code() == ResponseCode.OK ? "LOGIN_OK" : "LOGIN_ERR");
        if (resp.code() != null) builder.put("code", stringifyCode(resp.code()));
        if (resp.message() != null) builder.put("message", resp.message());
        if (resp.authToken() != null) builder.put("authToken", resp.authToken());
        builder.put("accountId", resp.accountId());
        return builder.build();
    }

    // ── Char List Mapping ────────────────────────────────────────────────────

    public static CharListRequest toCharListRequest(MessageFrame frame) {
        validateRequired(frame, "authToken");
        return new CharListRequest(frame.get("authToken"));
    }

    public static MessageFrame fromCharListRequest(CharListRequest req) {
        return MessageFrame.builder("CHAR_LIST")
            .put("authToken", req.authToken())
            .build();
    }

    public static CharListResponse toCharListResponse(MessageFrame frame) {
        ResponseCode code = "CHAR_LIST_OK".equals(frame.type()) ? ResponseCode.OK : parseCode(frame.get("code"));
        int count = frame.getInt("count", 0);
        List<CharListResponse.CharacterDto> characters = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            characters.add(new CharListResponse.CharacterDto(
                frame.get("char" + i + "_id"),
                frame.get("char" + i + "_name"),
                frame.getInt("char" + i + "_race", 0),
                frame.get("char" + i + "_raceName"),
                frame.getInt("char" + i + "_size", 1),
                frame.getInt("char" + i + "_face", 0),
                frame.getInt("char" + i + "_mainJob", 1),
                frame.get("char" + i + "_jobName"),
                frame.getInt("char" + i + "_nation", 0),
                frame.getInt("char" + i + "_zone", 0)
            ));
        }
        return new CharListResponse(code, characters);
    }

    public static MessageFrame fromCharListResponse(CharListResponse resp) {
        var builder = MessageFrame.builder(resp.code() == ResponseCode.OK ? "CHAR_LIST_OK" : "CHAR_LIST_ERR");
        builder.put("code", stringifyCode(resp.code()));
        builder.put("count", resp.characters().size());
        for (int i = 0; i < resp.characters().size(); i++) {
            var c = resp.characters().get(i);
            builder.put("char" + i + "_id", c.id())
                   .put("char" + i + "_name", c.name())
                   .put("char" + i + "_race", c.race())
                   .put("char" + i + "_raceName", c.raceName())
                   .put("char" + i + "_size", c.size())
                   .put("char" + i + "_face", c.face())
                   .put("char" + i + "_mainJob", c.mainJob())
                   .put("char" + i + "_jobName", c.jobName())
                   .put("char" + i + "_nation", c.nation())
                   .put("char" + i + "_zone", c.zone());
        }
        return builder.build();
    }

    // ── Char Create Mapping ──────────────────────────────────────────────────

    public static CharCreateRequest toCharCreateRequest(MessageFrame frame) {
        validateRequired(frame, "authToken", "name", "race", "size", "face", "mainJob", "nation");
        return new CharCreateRequest(
            frame.get("authToken"),
            frame.get("name"),
            frame.getInt("race", -1),
            frame.getInt("size", -1),
            frame.getInt("face", -1),
            frame.getInt("mainJob", 1),
            frame.get("nation")
        );
    }

    public static MessageFrame fromCharCreateRequest(CharCreateRequest req) {
        return MessageFrame.builder("CHAR_CREATE")
            .put("authToken", req.authToken())
            .put("name", req.name())
            .put("race", req.race())
            .put("size", req.size())
            .put("face", req.face())
            .put("mainJob", req.mainJob())
            .put("nation", req.nation())
            .build();
    }

    public static CharCreateResponse toCharCreateResponse(MessageFrame frame) {
        ResponseCode code = "CHAR_CREATE_OK".equals(frame.type()) ? ResponseCode.OK : parseCode(frame.get("code"));
        return new CharCreateResponse(
            code,
            frame.get("message"),
            frame.getLong("characterId", -1),
            frame.get("name")
        );
    }

    public static MessageFrame fromCharCreateResponse(CharCreateResponse resp) {
        var builder = MessageFrame.builder(resp.code() == ResponseCode.OK ? "CHAR_CREATE_OK" : "CHAR_CREATE_ERR");
        if (resp.code() != null) builder.put("code", stringifyCode(resp.code()));
        if (resp.message() != null) builder.put("message", resp.message());
        builder.put("characterId", resp.characterId());
        if (resp.name() != null) builder.put("name", resp.name());
        return builder.build();
    }

    // ── Char Select Mapping ──────────────────────────────────────────────────

    public static CharSelectRequest toCharSelectRequest(MessageFrame frame) {
        validateRequired(frame, "authToken", "characterId");
        return new CharSelectRequest(
            frame.get("authToken"),
            frame.getLong("characterId", -1)
        );
    }

    public static MessageFrame fromCharSelectRequest(CharSelectRequest req) {
        return MessageFrame.builder("CHAR_SELECT")
            .put("authToken", req.authToken())
            .put("characterId", req.characterId())
            .build();
    }

    public static CharSelectResponse toCharSelectResponse(MessageFrame frame) {
        ResponseCode code = "CHAR_SELECT_OK".equals(frame.type()) ? ResponseCode.OK : parseCode(frame.get("code"));
        return new CharSelectResponse(
            code,
            frame.get("message"),
            frame.getLong("characterId", -1),
            frame.get("characterName"),
            frame.getInt("homeZoneId", 0),
            frame.getInt("currentZoneId", 0),
            frame.getFloat("x", 0f),
            frame.getFloat("y", 0f),
            frame.getFloat("z", 0f),
            frame.getFloat("rot", 0f)
        );
    }

    public static MessageFrame fromCharSelectResponse(CharSelectResponse resp) {
        var builder = MessageFrame.builder(resp.code() == ResponseCode.OK ? "CHAR_SELECT_OK" : "CHAR_SELECT_ERR");
        if (resp.code() != null) builder.put("code", stringifyCode(resp.code()));
        if (resp.message() != null) builder.put("message", resp.message());
        builder.put("characterId", resp.characterId());
        if (resp.characterName() != null) builder.put("characterName", resp.characterName());
        builder.put("homeZoneId", resp.homeZoneId())
               .put("currentZoneId", resp.currentZoneId())
               .put("x", resp.x())
               .put("y", resp.y())
               .put("z", resp.z())
               .put("rot", resp.rot());
        return builder.build();
    }

    // ── Char Delete Mapping ──────────────────────────────────────────────────

    public static CharDeleteRequest toCharDeleteRequest(MessageFrame frame) {
        validateRequired(frame, "authToken", "characterId");
        return new CharDeleteRequest(
            frame.get("authToken"),
            frame.getLong("characterId", -1)
        );
    }

    public static MessageFrame fromCharDeleteRequest(CharDeleteRequest req) {
        return MessageFrame.builder("CHAR_DELETE")
            .put("authToken", req.authToken())
            .put("characterId", req.characterId())
            .build();
    }

    public static CharDeleteResponse toCharDeleteResponse(MessageFrame frame) {
        ResponseCode code = "CHAR_DELETE_OK".equals(frame.type()) ? ResponseCode.OK : parseCode(frame.get("code"));
        return new CharDeleteResponse(
            code,
            frame.get("message"),
            frame.getLong("characterId", -1)
        );
    }

    public static MessageFrame fromCharDeleteResponse(CharDeleteResponse resp) {
        var builder = MessageFrame.builder(resp.code() == ResponseCode.OK ? "CHAR_DELETE_OK" : "CHAR_DELETE_ERR");
        if (resp.code() != null) builder.put("code", stringifyCode(resp.code()));
        if (resp.message() != null) builder.put("message", resp.message());
        builder.put("characterId", resp.characterId());
        return builder.build();
    }

    // ── Play Mapping ─────────────────────────────────────────────────────────

    public static PlayRequest toPlayRequest(MessageFrame frame) {
        validateRequired(frame, "authToken", "characterId");
        return new PlayRequest(
            frame.get("authToken"),
            frame.getLong("characterId", -1)
        );
    }

    public static MessageFrame fromPlayRequest(PlayRequest req) {
        return MessageFrame.builder("PLAY")
            .put("authToken", req.authToken())
            .put("characterId", req.characterId())
            .build();
    }

    public static PlayResponse toPlayResponse(MessageFrame frame) {
        ResponseCode code = "PLAY_OK".equals(frame.type()) ? ResponseCode.OK : parseCode(frame.get("code"));
        return new PlayResponse(
            code,
            frame.get("message"),
            frame.get("sessionId"),
            frame.getLong("accountId", -1),
            frame.getLong("characterId", -1),
            frame.get("characterName"),
            frame.getInt("zoneId", 0),
            frame.getInt("playersInZone", 0),
            frame.getLong("keepaliveIntervalMs", 5000L),
            frame.getInt("homeZoneId", 0),
            frame.getFloat("x", 0f),
            frame.getFloat("y", 0f),
            frame.getFloat("z", 0f),
            frame.getFloat("rot", 0f)
        );
    }

    public static MessageFrame fromPlayResponse(PlayResponse resp) {
        var builder = MessageFrame.builder(resp.code() == ResponseCode.OK ? "PLAY_OK" : "PLAY_ERR");
        if (resp.code() != null) builder.put("code", stringifyCode(resp.code()));
        if (resp.message() != null) builder.put("message", resp.message());
        if (resp.sessionId() != null) builder.put("sessionId", resp.sessionId());
        builder.put("accountId", resp.accountId())
               .put("characterId", resp.characterId());
        if (resp.characterName() != null) builder.put("characterName", resp.characterName());
        builder.put("zoneId", resp.zoneId())
               .put("playersInZone", resp.playersInZone())
               .put("keepaliveIntervalMs", resp.keepaliveIntervalMs())
               .put("homeZoneId", resp.homeZoneId())
               .put("x", resp.x())
               .put("y", resp.y())
               .put("z", resp.z())
               .put("rot", resp.rot());
        return builder.build();
    }

    // ── Ping Mapping ─────────────────────────────────────────────────────────

    public static PingRequest toPingRequest(MessageFrame frame) {
        validateRequired(frame, "sessionId");
        return new PingRequest(frame.get("sessionId"));
    }

    public static MessageFrame fromPingRequest(PingRequest req) {
        return MessageFrame.builder("PING")
            .put("sessionId", req.sessionId())
            .build();
    }

    public static PingResponse toPingResponse(MessageFrame frame) {
        ResponseCode code = "PONG".equals(frame.type()) ? ResponseCode.OK : parseCode(frame.get("code"));
        return new PingResponse(
            frame.type(),
            frame.get("sessionId"),
            code,
            frame.get("message")
        );
    }

    public static MessageFrame fromPingResponse(PingResponse resp) {
        var builder = MessageFrame.builder(resp.type() != null ? resp.type() : "PONG");
        if (resp.sessionId() != null) builder.put("sessionId", resp.sessionId());
        if (resp.code() != null) builder.put("code", stringifyCode(resp.code()));
        if (resp.message() != null) builder.put("message", resp.message());
        return builder.build();
    }

    // ── Logout Mapping ───────────────────────────────────────────────────────

    public static LogoutRequest toLogoutRequest(MessageFrame frame) {
        validateRequired(frame, "sessionId");
        return new LogoutRequest(frame.get("sessionId"));
    }

    public static MessageFrame fromLogoutRequest(LogoutRequest req) {
        return MessageFrame.builder("LOGOUT")
            .put("sessionId", req.sessionId())
            .build();
    }

    public static LogoutResponse toLogoutResponse(MessageFrame frame) {
        ResponseCode code = "BYE".equals(frame.type()) ? ResponseCode.OK : parseCode(frame.get("code"));
        return new LogoutResponse(
            frame.get("sessionId"),
            code,
            frame.get("message")
        );
    }

    public static MessageFrame fromLogoutResponse(LogoutResponse resp) {
        var builder = MessageFrame.builder("BYE");
        if (resp.sessionId() != null) builder.put("sessionId", resp.sessionId());
        if (resp.code() != null) builder.put("code", stringifyCode(resp.code()));
        if (resp.message() != null) builder.put("message", resp.message());
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
