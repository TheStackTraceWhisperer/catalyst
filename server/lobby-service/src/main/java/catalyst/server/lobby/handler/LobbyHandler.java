package catalyst.server.lobby.handler;

import catalyst.common.dto.*;
import catalyst.common.network.ResponseCode;
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
import java.util.function.Function;
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

    public CharListResponse handleList(CharListRequest req) {
        Long accountId = tickets.validate(req.authToken());
        if (accountId == null) return unauthorized(code -> new CharListResponse(code, new ArrayList<>()));
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
            log.info("CHAR_LIST_OK account={} count={}", accountId, rows.size());
            return new CharListResponse(ResponseCode.OK, characterDtos);
        } catch (SQLException e) {
            log.error("CHAR_LIST_ERR account={}", accountId, e);
            return new CharListResponse(ResponseCode.ERROR, new ArrayList<>());
        }
    }

    public CharCreateResponse handleCreate(CharCreateRequest req) {
        Long accountId = tickets.validate(req.authToken());
        if (accountId == null) return unauthorized(code -> new CharCreateResponse(code, "Invalid or expired auth token", -1, null));

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
            return new CharCreateResponse(ResponseCode.CONFLICT, "Character name must be 3-15 letters (A-Z)", -1, null);
        }
        RaceRule rule = RACE_RULES.get(race);
        if (rule == null) {
            return new CharCreateResponse(ResponseCode.CONFLICT, "Race must be 1-8", -1, null);
        }
        if (size < rule.minSize() || size > rule.maxSize()) {
            return new CharCreateResponse(ResponseCode.CONFLICT,
                "Size for " + rule.name() + " must be " + rule.minSize() + ".." + rule.maxSize(), -1, null);
        }
        if (face < 0 || face > 15) {
            return new CharCreateResponse(ResponseCode.CONFLICT, "Face must be 0-15", -1, null);
        }
        if (nation < 0 || nation > 2) {
            return new CharCreateResponse(ResponseCode.CONFLICT, "Nation must be 0-2", -1, null);
        }

        ZoneSpawn spawn = NATION_ZONES.get(nation).get(rng.nextInt(3));

        try {
            long charId = characters.createWithJobs(accountId, name, race, size, face, mainJob, nation,
                spawn.zoneId(), spawn.x(), spawn.y(), spawn.z(), spawn.rot());
            log.info("CHAR_CREATE_OK account={} characterId={} name={} race={} job={} nation={}",
                accountId, charId, name, race, mainJob, nation);
            return new CharCreateResponse(ResponseCode.OK, null, charId, name);
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                log.info("CHAR_CREATE_ERR account={} reason=duplicate_name name={}", accountId, name);
                return new CharCreateResponse(ResponseCode.CONFLICT, "Character name is already in use", -1, null);
            }
            log.error("CHAR_CREATE_ERR account={}", accountId, e);
            return new CharCreateResponse(ResponseCode.ERROR, "Failed to create character", -1, null);
        }
    }

    public CharSelectResponse handleSelect(CharSelectRequest req) {
        Long accountId = tickets.validate(req.authToken());
        if (accountId == null) return unauthorized(code -> new CharSelectResponse(code, "Invalid or expired auth token", -1, null, 0, 0, 0f, 0f, 0f, 0f));
        long charId = req.characterId();
        if (charId <= 0) {
            return new CharSelectResponse(ResponseCode.CONFLICT, "characterId required", -1, null, 0, 0, 0f, 0f, 0f, 0f);
        }
        try {
            if (sessions.hasActiveSession(accountId)) {
                return new CharSelectResponse(ResponseCode.CONFLICT, "Account is already online", -1, null, 0, 0, 0f, 0f, 0f, 0f);
            }
            var identity = characters.findActiveByIdAndAccount(charId, accountId);
            if (identity.isEmpty()) {
                return new CharSelectResponse(ResponseCode.NOT_FOUND, "Character not found", -1, null, 0, 0, 0f, 0f, 0f, 0f);
            }
            var id = identity.get();
            log.info("CHAR_SELECT_OK account={} characterId={} zone={}", accountId, charId, id.currentZoneId());

            return new CharSelectResponse(
                ResponseCode.OK, null, charId, id.name(),
                id.homeZoneId(), id.currentZoneId(),
                id.currentX(), id.currentY(), id.currentZ(), id.currentHeading()
            );
        } catch (SQLException e) {
            log.error("CHAR_SELECT_ERR account={} charId={}", accountId, charId, e);
            return new CharSelectResponse(ResponseCode.ERROR, "Failed to load character", -1, null, 0, 0, 0f, 0f, 0f, 0f);
        }
    }

    public CharDeleteResponse handleDelete(CharDeleteRequest req) {
        Long accountId = tickets.validate(req.authToken());
        if (accountId == null) return unauthorized(code -> new CharDeleteResponse(code, "Invalid or expired auth token", -1));
        long charId = req.characterId();
        if (charId <= 0) {
            return new CharDeleteResponse(ResponseCode.CONFLICT, "characterId required", -1);
        }
        try {
            if (sessions.characterHasActiveSession(charId)) {
                return new CharDeleteResponse(ResponseCode.CONFLICT, "Character is currently online", charId);
            }
            if (!characters.softDelete(charId, accountId)) {
                return new CharDeleteResponse(ResponseCode.NOT_FOUND, "Character not found", charId);
            }
            log.info("CHAR_DELETE_OK account={} characterId={}", accountId, charId);
            return new CharDeleteResponse(ResponseCode.OK, null, charId);
        } catch (SQLException e) {
            log.error("CHAR_DELETE_ERR account={} charId={}", accountId, charId, e);
            return new CharDeleteResponse(ResponseCode.ERROR, "Failed to delete character", charId);
        }
    }

    public PlayResponse handlePlay(PlayRequest req) {
        Long accountId = tickets.validate(req.authToken());
        if (accountId == null) {
            return unauthorized(code -> new PlayResponse(code, "Invalid or expired auth token", null, -1, -1, null, 0, 0, 5000L, 0, 0f, 0f, 0f, 0f));
        }
        long charId = req.characterId();
        if (charId <= 0) {
            return new PlayResponse(ResponseCode.CONFLICT, "characterId required", null, -1, -1, null, 0, 0, 5000L, 0, 0f, 0f, 0f, 0f);
        }
        try {
            var identity = characters.findActiveByIdAndAccount(charId, accountId);
            if (identity.isEmpty()) {
                return new PlayResponse(ResponseCode.NOT_FOUND, "Character not found", null, -1, -1, null, 0, 0, 5000L, 0, 0f, 0f, 0f, 0f);
            }
            var id = identity.get();
            String sessionId;
            try {
                sessionId = sessions.create(accountId, charId, id.currentZoneId());
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    log.info("PLAY_ERR account={} charId={} reason=already_online", accountId, charId);
                    return new PlayResponse(ResponseCode.CONFLICT, "Account or character is already online", null, -1, -1, null, 0, 0, 5000L, 0, 0f, 0f, 0f, 0f);
                }
                throw e;
            }
            int pop = sessions.getZonePopulation(id.currentZoneId());
            log.info("PLAY_OK account={} charId={} session={} zone={} pop={}", accountId, charId, sessionId, id.currentZoneId(), pop);

            return new PlayResponse(
                ResponseCode.OK, null, sessionId,
                accountId, charId, id.name(),
                id.currentZoneId(), pop,
                props.getKeepaliveIntervalMs(),
                id.homeZoneId(),
                id.currentX(), id.currentY(), id.currentZ(), id.currentHeading()
            );
        } catch (SQLException e) {
            log.error("PLAY_ERR account={} charId={}", accountId, charId, e);
            return new PlayResponse(ResponseCode.ERROR, "Failed to enter world", null, -1, -1, null, 0, 0, 5000L, 0, 0f, 0f, 0f, 0f);
        }
    }

    private String normalize(String v) { return v == null ? "" : v.trim(); }
    private <T> T unauthorized(Function<ResponseCode, T> responseFactory) {
        return responseFactory.apply(ResponseCode.UNAUTHORIZED);
    }

    private record RaceRule(String name, int minSize, int maxSize) {}
    private record ZoneSpawn(int zoneId, float x, float y, float z, float rot) {}
}
