package catalyst.ffxi.server.handler;

import catalyst.ffxi.common.net.MessageFrame;
import catalyst.ffxi.server.repository.CharacterRepository;
import catalyst.ffxi.server.repository.SessionRepository;
import catalyst.ffxi.server.session.AuthTicketStore;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
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
    private final Random rng = new Random();

    public MessageFrame handleList(MessageFrame req) {
        Long accountId = tickets.validate(req.get("authToken"));
        if (accountId == null) return unauthorized();
        try {
            List<CharacterRepository.CharacterListRow> rows = characters.findActiveByAccount(accountId);
            MessageFrame.Builder b = MessageFrame.builder("CHAR_LIST_OK").put("count", rows.size());
            for (int i = 0; i < rows.size(); i++) {
                var r = rows.get(i);
                b.put("char" + i + "_id",      r.id())
                 .put("char" + i + "_name",    r.name())
                 .put("char" + i + "_race",    r.race())
                 .put("char" + i + "_raceName",r.raceName())
                 .put("char" + i + "_size",    r.size())
                 .put("char" + i + "_face",    r.face())
                 .put("char" + i + "_mainJob", r.mainJob())
                 .put("char" + i + "_jobName", r.jobName())
                 .put("char" + i + "_nation",  r.nation())
                 .put("char" + i + "_zone",    r.currentZoneId());
            }
            log.info("CHAR_LIST_OK account={} count={}", accountId, rows.size());
            return b.build();
        } catch (SQLException e) {
            log.error("CHAR_LIST_ERR account={}", accountId, e);
            return error("SERVER_ERROR", "Failed to load characters");
        }
    }

    public MessageFrame handleCreate(MessageFrame req) {
        Long accountId = tickets.validate(req.get("authToken"));
        if (accountId == null) return unauthorized();

        String name    = normalize(req.get("name"));
        int    race    = req.getInt("race",    -1);
        int    size    = req.getInt("size",    -1);
        int    face    = req.getInt("face",    -1);
        int    mainJob = Math.clamp(req.getInt("mainJob", 1), 1, 6);
        int    nation  = req.getInt("nation",  -1);

        if (!NAME_PATTERN.matcher(name).matches())
            return error("INVALID_NAME", "Character name must be 3-15 letters (A-Z)");
        RaceRule rule = RACE_RULES.get(race);
        if (rule == null) return error("INVALID_RACE", "Race must be 1-8");
        if (size < rule.minSize() || size > rule.maxSize())
            return error("INVALID_SIZE", "Size for " + rule.name() + " must be " + rule.minSize() + ".." + rule.maxSize());
        if (face < 0 || face > 15) return error("INVALID_FACE", "Face must be 0-15");
        if (nation < 0 || nation > 2) return error("INVALID_NATION", "Nation must be 0-2");

        ZoneSpawn spawn = NATION_ZONES.get(nation).get(rng.nextInt(3));

        try {
            long charId = characters.createWithJobs(accountId, name, race, size, face, mainJob, nation,
                spawn.zoneId(), spawn.x(), spawn.y(), spawn.z(), spawn.rot());
            log.info("CHAR_CREATE_OK account={} characterId={} name={} race={} job={} nation={}",
                accountId, charId, name, race, mainJob, nation);
            return MessageFrame.builder("CHAR_CREATE_OK").put("characterId", charId).put("name", name).build();
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                log.info("CHAR_CREATE_ERR account={} reason=duplicate_name name={}", accountId, name);
                return error("NAME_ALREADY_TAKEN", "Character name is already in use");
            }
            log.error("CHAR_CREATE_ERR account={}", accountId, e);
            return error("SERVER_ERROR", "Failed to create character");
        }
    }

    public MessageFrame handleSelect(MessageFrame req) {
        Long accountId = tickets.validate(req.get("authToken"));
        if (accountId == null) return unauthorized();
        long charId = req.getLong("characterId", -1);
        if (charId <= 0) return error("INVALID_CHARACTER", "characterId required");
        try {
            if (sessions.hasActiveSession(accountId)) return error("ALREADY_ONLINE", "Account is already online");
            var identity = characters.findActiveByIdAndAccount(charId, accountId);
            if (identity.isEmpty()) return error("CHARACTER_NOT_FOUND", "Character not found");
            var id = identity.get();
            log.info("CHAR_SELECT_OK account={} characterId={} zone={}", accountId, charId, id.currentZoneId());
            return MessageFrame.builder("CHAR_SELECT_OK")
                .put("characterId", charId)
                .put("characterName", id.name())
                .put("homeZoneId",    id.homeZoneId())
                .put("currentZoneId", id.currentZoneId())
                .put("x",   id.currentX())
                .put("y",   id.currentY())
                .put("z",   id.currentZ())
                .put("rot", id.currentHeading())
                .build();
        } catch (SQLException e) {
            log.error("CHAR_SELECT_ERR account={} charId={}", accountId, charId, e);
            return error("SERVER_ERROR", "Failed to load character");
        }
    }

    public MessageFrame handleDelete(MessageFrame req) {
        Long accountId = tickets.validate(req.get("authToken"));
        if (accountId == null) return unauthorized();
        long charId = req.getLong("characterId", -1);
        if (charId <= 0) return error("INVALID_CHARACTER", "characterId required");
        try {
            if (sessions.characterHasActiveSession(charId)) return error("CHARACTER_ACTIVE", "Character is currently online");
            if (!characters.softDelete(charId, accountId)) return error("CHARACTER_NOT_FOUND", "Character not found");
            log.info("CHAR_DELETE_OK account={} characterId={}", accountId, charId);
            return MessageFrame.builder("CHAR_DELETE_OK").put("characterId", charId).build();
        } catch (SQLException e) {
            log.error("CHAR_DELETE_ERR account={} charId={}", accountId, charId, e);
            return error("SERVER_ERROR", "Failed to delete character");
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
