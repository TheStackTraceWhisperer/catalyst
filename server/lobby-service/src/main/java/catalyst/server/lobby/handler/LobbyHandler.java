package catalyst.server.lobby.handler;

import catalyst.common.network.MessageFrame;
import catalyst.common.network.ResponseCode;
import catalyst.common.dto.*;
import catalyst.server.lobby.repository.CharacterRepository;
import catalyst.server.lobby.repository.SessionRepository;
import catalyst.server.common.repository.AuthTicketStore;
import catalyst.server.lobby.properties.ServerProperties;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class LobbyHandler {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z]{3,15}$");

    private static final Map<Integer, RaceRule> RACE_RULES = Map.of(
        1, new RaceRule("Hume Male",       0, 2),
        2, new RaceRule("Hume Female",     0, 2),
        3, new RaceRule("Elvaan Male",     0, 2),
        4, new RaceRule("Elvaan Female",   0, 2),
        5, new RaceRule("Tarutaru Male",   0, 0),
        6, new RaceRule("Tarutaru Female", 0, 0),
        7, new RaceRule("Mithra",          0, 2),
        8, new RaceRule("Galka",           2, 2)
    );

    private static final List<List<ZoneSpawn>> NATION_ZONES = List.of(
        List.of(new ZoneSpawn(230,-64,0,209,0), new ZoneSpawn(231,-32,0,-20,0), new ZoneSpawn(232,43,0,-9,0)),
        List.of(new ZoneSpawn(234,39,0,58,0),   new ZoneSpawn(235,27,0,-24,0), new ZoneSpawn(233,-29,0,5,0)),
        List.of(new ZoneSpawn(238,-55,0,71,0),  new ZoneSpawn(239,-6,0,37,0),  new ZoneSpawn(240,-95,0,40,0))
    );

    private final CharacterRepository characters;
    private final SessionRepository sessions;
    private final AuthTicketStore tickets;
    private final ServerProperties props;
    private final Random rng = new Random();

    public MessageFrame handleList(MessageFrame reqFrame) {
        CharListRequest req;
        try {
            req = ProtocolMapper.toCharListRequest(reqFrame);
        } catch (IllegalArgumentException e) {
            return unauthorized();
        }

        Long accountId = tickets.validate(req.authToken());
        if (accountId == null) return unauthorized();
        try {
            List<CharacterRepository.CharacterListRow> rows = characters.findActiveByAccount(accountId);
            List<CharListResponse.CharacterDto> characterDtos = new ArrayList<>(rows.size());
            for (var r : rows) {
                characterDtos.add(new CharListResponse.CharacterDto(
                    Long.toString(r.id()),
                    r.name(),
                    r.race(),
                    r.raceName(),
                    r.size(),
                    r.face(),
                    r.mainJob(),
                    r.jobName(),
                    r.nation(),
                    r.currentZoneId()
                ));
            }
            CharListResponse resp = new CharListResponse(ResponseCode.OK, characterDtos);
            log.info("CHAR_LIST_OK account={} count={}", accountId, rows.size());
            return ProtocolMapper.fromCharListResponse(resp);
        } catch (SQLException e) {
            log.error("CHAR_LIST_ERR account={}", accountId, e);
            CharListResponse resp = new CharListResponse(ResponseCode.ERROR, new ArrayList<>());
            return ProtocolMapper.fromCharListResponse(resp);
        }
    }

    public MessageFrame handleCreate(MessageFrame reqFrame) {
        CharCreateRequest req;
        try {
            req = ProtocolMapper.toCharCreateRequest(reqFrame);
        } catch (IllegalArgumentException e) {
            CharCreateResponse resp = new CharCreateResponse(ResponseCode.CONFLICT, e.getMessage(), -1, null);
            return ProtocolMapper.fromCharCreateResponse(resp);
        }

        Long accountId = tickets.validate(req.authToken());
        if (accountId == null) return unauthorized();

        String name    = normalize(req.name());
        int    race    = req.race();
        int    size    = req.size();
        int    face    = req.face();
        int    mainJob = Math.clamp(req.mainJob(), 1, 6);

        int nation = -1;
        try {
            nation = Integer.parseInt(req.nation());
        } catch (NumberFormatException ignored) {}

        if (!NAME_PATTERN.matcher(name).matches()) {
            CharCreateResponse resp = new CharCreateResponse(ResponseCode.CONFLICT, "Character name must be 3-15 letters (A-Z)", -1, null);
            return ProtocolMapper.fromCharCreateResponse(resp);
        }
        RaceRule rule = RACE_RULES.get(race);
        if (rule == null) {
            CharCreateResponse resp = new CharCreateResponse(ResponseCode.CONFLICT, "Race must be 1-8", -1, null);
            return ProtocolMapper.fromCharCreateResponse(resp);
        }
        if (size < rule.minSize() || size > rule.maxSize()) {
            CharCreateResponse resp = new CharCreateResponse(ResponseCode.CONFLICT,
                "Size for " + rule.name() + " must be " + rule.minSize() + ".." + rule.maxSize(), -1, null);
            return ProtocolMapper.fromCharCreateResponse(resp);
        }
        if (face < 0 || face > 15) {
            CharCreateResponse resp = new CharCreateResponse(ResponseCode.CONFLICT, "Face must be 0-15", -1, null);
            return ProtocolMapper.fromCharCreateResponse(resp);
        }
        if (nation < 0 || nation > 2) {
            CharCreateResponse resp = new CharCreateResponse(ResponseCode.CONFLICT, "Nation must be 0-2", -1, null);
            return ProtocolMapper.fromCharCreateResponse(resp);
        }

        ZoneSpawn spawn = NATION_ZONES.get(nation).get(rng.nextInt(3));

        try {
            long charId = characters.createWithJobs(accountId, name, race, size, face, mainJob, nation,
                spawn.zoneId(), spawn.x(), spawn.y(), spawn.z(), spawn.rot());
            log.info("CHAR_CREATE_OK account={} characterId={} name={} race={} job={} nation={}",
                accountId, charId, name, race, mainJob, nation);
            CharCreateResponse resp = new CharCreateResponse(ResponseCode.OK, null, charId, name);
            return ProtocolMapper.fromCharCreateResponse(resp);
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                log.info("CHAR_CREATE_ERR account={} reason=duplicate_name name={}", accountId, name);
                CharCreateResponse resp = new CharCreateResponse(ResponseCode.CONFLICT, "Character name is already in use", -1, null);
                return ProtocolMapper.fromCharCreateResponse(resp);
            }
            log.error("CHAR_CREATE_ERR account={}", accountId, e);
            CharCreateResponse resp = new CharCreateResponse(ResponseCode.ERROR, "Failed to create character", -1, null);
            return ProtocolMapper.fromCharCreateResponse(resp);
        }
    }

    public MessageFrame handleSelect(MessageFrame reqFrame) {
        CharSelectRequest req;
        try {
            req = ProtocolMapper.toCharSelectRequest(reqFrame);
        } catch (IllegalArgumentException e) {
            CharSelectResponse resp = new CharSelectResponse(ResponseCode.CONFLICT, e.getMessage(), -1, null, 0, 0, 0f, 0f, 0f, 0f);
            return ProtocolMapper.fromCharSelectResponse(resp);
        }

        Long accountId = tickets.validate(req.authToken());
        if (accountId == null) return unauthorized();
        long charId = req.characterId();
        if (charId <= 0) {
            CharSelectResponse resp = new CharSelectResponse(ResponseCode.CONFLICT, "characterId required", -1, null, 0, 0, 0f, 0f, 0f, 0f);
            return ProtocolMapper.fromCharSelectResponse(resp);
        }
        try {
            if (sessions.hasActiveSession(accountId)) {
                CharSelectResponse resp = new CharSelectResponse(ResponseCode.CONFLICT, "Account is already online", -1, null, 0, 0, 0f, 0f, 0f, 0f);
                return ProtocolMapper.fromCharSelectResponse(resp);
            }
            var identity = characters.findActiveByIdAndAccount(charId, accountId);
            if (identity.isEmpty()) {
                CharSelectResponse resp = new CharSelectResponse(ResponseCode.NOT_FOUND, "Character not found", -1, null, 0, 0, 0f, 0f, 0f, 0f);
                return ProtocolMapper.fromCharSelectResponse(resp);
            }
            var id = identity.get();
            log.info("CHAR_SELECT_OK account={} characterId={} zone={}", accountId, charId, id.currentZoneId());

            CharSelectResponse resp = new CharSelectResponse(
                ResponseCode.OK, null, charId, id.name(),
                id.homeZoneId(), id.currentZoneId(),
                id.currentX(), id.currentY(), id.currentZ(), id.currentHeading()
            );
            return ProtocolMapper.fromCharSelectResponse(resp);
        } catch (SQLException e) {
            log.error("CHAR_SELECT_ERR account={} charId={}", accountId, charId, e);
            CharSelectResponse resp = new CharSelectResponse(ResponseCode.ERROR, "Failed to load character", -1, null, 0, 0, 0f, 0f, 0f, 0f);
            return ProtocolMapper.fromCharSelectResponse(resp);
        }
    }

    public MessageFrame handleDelete(MessageFrame reqFrame) {
        CharDeleteRequest req;
        try {
            req = ProtocolMapper.toCharDeleteRequest(reqFrame);
        } catch (IllegalArgumentException e) {
            CharDeleteResponse resp = new CharDeleteResponse(ResponseCode.CONFLICT, e.getMessage(), -1);
            return ProtocolMapper.fromCharDeleteResponse(resp);
        }

        Long accountId = tickets.validate(req.authToken());
        if (accountId == null) return unauthorized();
        long charId = req.characterId();
        if (charId <= 0) {
            CharDeleteResponse resp = new CharDeleteResponse(ResponseCode.CONFLICT, "characterId required", -1);
            return ProtocolMapper.fromCharDeleteResponse(resp);
        }
        try {
            if (sessions.characterHasActiveSession(charId)) {
                CharDeleteResponse resp = new CharDeleteResponse(ResponseCode.CONFLICT, "Character is currently online", charId);
                return ProtocolMapper.fromCharDeleteResponse(resp);
            }
            if (!characters.softDelete(charId, accountId)) {
                CharDeleteResponse resp = new CharDeleteResponse(ResponseCode.NOT_FOUND, "Character not found", charId);
                return ProtocolMapper.fromCharDeleteResponse(resp);
            }
            log.info("CHAR_DELETE_OK account={} characterId={}", accountId, charId);
            CharDeleteResponse resp = new CharDeleteResponse(ResponseCode.OK, null, charId);
            return ProtocolMapper.fromCharDeleteResponse(resp);
        } catch (SQLException e) {
            log.error("CHAR_DELETE_ERR account={} charId={}", accountId, charId, e);
            CharDeleteResponse resp = new CharDeleteResponse(ResponseCode.ERROR, "Failed to delete character", charId);
            return ProtocolMapper.fromCharDeleteResponse(resp);
        }
    }

    public MessageFrame handlePlay(MessageFrame reqFrame) {
        PlayRequest req;
        try {
            req = ProtocolMapper.toPlayRequest(reqFrame);
        } catch (IllegalArgumentException e) {
            PlayResponse resp = new PlayResponse(ResponseCode.CONFLICT, e.getMessage(), null, -1, -1, null, 0, 0, 5000L, 0, 0f, 0f, 0f, 0f);
            return ProtocolMapper.fromPlayResponse(resp);
        }

        Long accountId = tickets.validate(req.authToken());
        if (accountId == null) {
            PlayResponse resp = new PlayResponse(ResponseCode.UNAUTHORIZED, "Invalid or expired auth token", null, -1, -1, null, 0, 0, 5000L, 0, 0f, 0f, 0f, 0f);
            return ProtocolMapper.fromPlayResponse(resp);
        }
        long charId = req.characterId();
        if (charId <= 0) {
            PlayResponse resp = new PlayResponse(ResponseCode.CONFLICT, "characterId required", null, -1, -1, null, 0, 0, 5000L, 0, 0f, 0f, 0f, 0f);
            return ProtocolMapper.fromPlayResponse(resp);
        }
        try {
            var identity = characters.findActiveByIdAndAccount(charId, accountId);
            if (identity.isEmpty()) {
                PlayResponse resp = new PlayResponse(ResponseCode.NOT_FOUND, "Character not found", null, -1, -1, null, 0, 0, 5000L, 0, 0f, 0f, 0f, 0f);
                return ProtocolMapper.fromPlayResponse(resp);
            }
            var id = identity.get();
            String sessionId;
            try {
                sessionId = sessions.create(accountId, charId, id.currentZoneId());
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    log.info("PLAY_ERR account={} charId={} reason=already_online", accountId, charId);
                    PlayResponse resp = new PlayResponse(ResponseCode.CONFLICT, "Account or character is already online", null, -1, -1, null, 0, 0, 5000L, 0, 0f, 0f, 0f, 0f);
                    return ProtocolMapper.fromPlayResponse(resp);
                }
                throw e;
            }
            int pop = sessions.getZonePopulation(id.currentZoneId());
            log.info("PLAY_OK account={} charId={} session={} zone={} pop={}", accountId, charId, sessionId, id.currentZoneId(), pop);

            PlayResponse resp = new PlayResponse(
                ResponseCode.OK, null, sessionId,
                accountId, charId, id.name(),
                id.currentZoneId(), pop,
                props.getKeepaliveIntervalMs(),
                id.homeZoneId(),
                id.currentX(), id.currentY(), id.currentZ(), id.currentHeading()
            );
            return ProtocolMapper.fromPlayResponse(resp);
        } catch (SQLException e) {
            log.error("PLAY_ERR account={} charId={}", accountId, charId, e);
            PlayResponse resp = new PlayResponse(ResponseCode.ERROR, "Failed to enter world", null, -1, -1, null, 0, 0, 5000L, 0, 0f, 0f, 0f, 0f);
            return ProtocolMapper.fromPlayResponse(resp);
        }
    }

    private String normalize(String v) { return v == null ? "" : v.trim(); }
    private MessageFrame unauthorized() { return error("UNAUTHORIZED", "Invalid or expired auth token"); }
    private MessageFrame error(String code, String message) {
        return MessageFrame.builder("ERROR").put("code", code).put("message", message).build();
    }

    private record RaceRule(String name, int minSize, int maxSize) {}
    private record ZoneSpawn(int zoneId, float x, float y, float z, float rot) {}
}
