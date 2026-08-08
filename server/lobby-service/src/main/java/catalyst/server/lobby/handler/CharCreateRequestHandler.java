package catalyst.server.lobby.handler;

import catalyst.common.dto.lobby.CharCreateRequest;
import catalyst.common.dto.lobby.CharCreateResponse;
import catalyst.server.common.network.PacketHandler;
import catalyst.common.network.ResponseCode;
import catalyst.server.common.repository.AuthTicketStore;
import catalyst.server.lobby.repository.CharacterRepository;
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
public class CharCreateRequestHandler implements PacketHandler<CharCreateRequest> {

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
    private final AuthTicketStore tickets;
    private final Random rng = new Random();

    @Override
    public Class<CharCreateRequest> getPacketType() {
        return CharCreateRequest.class;
    }

    @Override
    public Object handle(CharCreateRequest req, String sessionId) {
        Long accountId = tickets.validate(req.authToken());
        if (accountId == null) {
            return new CharCreateResponse(ResponseCode.UNAUTHORIZED, "Invalid or expired auth token", -1, null);
        }

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

    private String normalize(String v) { return v == null ? "" : v.trim(); }

    private record RaceRule(String name, int minSize, int maxSize) {}
    private record ZoneSpawn(int zoneId, float x, float y, float z, float rot) {}
}
