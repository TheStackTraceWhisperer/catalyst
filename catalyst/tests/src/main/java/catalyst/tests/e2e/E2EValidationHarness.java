package catalyst.tests.e2e;

import catalyst.client.network.ClientChannelInitializer;
import catalyst.client.network.ClientTransport;
import catalyst.client.network.ClientTransportService;
import catalyst.common.dto.lobby.*;
import catalyst.common.dto.login.*;
import catalyst.common.dto.world.*;
import catalyst.common.network.DecodedPacket;
import catalyst.common.network.PacketRegistry;
import catalyst.common.network.PacketType;
import catalyst.common.network.ResponseCode;

public final class E2EValidationHarness {

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 35555;

        System.out.println("=== Starting E2E Protocol Validation Harness ===");
        System.out.println("Target: " + host + ":" + port);

        final String caPath = System.getProperty("catalyst.tls.ca-path", "");
        catalyst.common.network.TlsProperties devTls = new catalyst.common.network.TlsProperties() {
            @Override public String getCertPath() { return ""; }
            @Override public String getKeyPath() { return ""; }
            @Override public String getCaPath() { return caPath; }
        };

        PacketRegistry packetRegistry = new PacketRegistry();
        ClientChannelInitializer channelInitializer = new ClientChannelInitializer(packetRegistry);
        ClientTransport transport = new ClientTransport(channelInitializer, devTls);

        try (ClientTransportService service = new ClientTransportService(transport)) {
            service.connect(host, port);

            // 1. LOGIN
            System.out.println("\nStep 1: Sending LOGIN for dev/dev...");
            var loginPacket = new DecodedPacket(PacketType.LOGIN_REQUEST, new LoginRequest("dev", "dev"));
            LoginResponse loginResp = service.request(loginPacket, LoginResponse.class);
            System.out.println("Login Response: " + loginResp);
            if (ResponseCode.OK != loginResp.code()) {
                throw new AssertionError("LOGIN failed: " + loginResp.errorMessage());
            }
            long accountId = loginResp.accountId();
            System.out.println("Account ID acquired: " + accountId);

            // 2. CHAR_CREATE
            System.out.println("\nStep 2: Sending CHAR_CREATE...");
            String charName = "EeeTest" +
              (char)('A' + new java.util.Random().nextInt(26)) +
              (char)('A' + new java.util.Random().nextInt(26)) +
              (char)('A' + new java.util.Random().nextInt(26));
            var createPacket = new DecodedPacket(
              PacketType.CHAR_CREATE_REQUEST,
              new CharCreateRequest(charName, 1, 1, 3, 1, "0")
            );
            CharCreateResponse createResp = service.request(createPacket, CharCreateResponse.class);
            System.out.println("Create Response: " + createResp);
            if (ResponseCode.OK != createResp.code()) {
                throw new AssertionError("CHAR_CREATE failed: " + createResp.errorMessage());
            }
            long newCharId = createResp.characterId();
            System.out.println("Created character: Name=" + charName + " ID=" + newCharId);

            // 3. CHAR_LIST Verification
            System.out.println("\nStep 3: Sending CHAR_LIST verification...");
            var listPacket = new DecodedPacket(PacketType.CHAR_LIST_REQUEST, new CharListRequest());
            CharListResponse charListResp = service.request(listPacket, CharListResponse.class);
            if (ResponseCode.OK != charListResp.code()) {
                throw new AssertionError("CHAR_LIST failed: " + charListResp.code());
            }
            System.out.println("Summaries retrieved: " + charListResp.characters().size());
            boolean found = false;
            for (CharacterSummary c : charListResp.characters()) {
                System.out.println(" - Char: ID=" + c.characterId() + " Name=" + c.name() + " Zone=" + c.zoneId());
                if (c.characterId() == newCharId) {
                    found = true;
                }
            }
            if (!found) {
                throw new AssertionError("Newly created character ID " + newCharId + " not found in CHAR_LIST!");
            }
            System.out.println("CHAR_LIST verification succeeded.");

            // 4. CHAR_SELECT
            System.out.println("\nStep 4: Sending CHAR_SELECT...");
            var selectPacket = new DecodedPacket(PacketType.CHAR_SELECT_REQUEST, new CharSelectRequest(newCharId));
            CharSelectResponse selectResp = service.request(selectPacket, CharSelectResponse.class);
            System.out.println("Select Response: " + selectResp);
            if (ResponseCode.OK != selectResp.code()) {
                throw new AssertionError("CHAR_SELECT failed: " + selectResp.errorMessage());
            }

            // 5. PLAY
            System.out.println("\nStep 5: Sending PLAY...");
            var playPacket = new DecodedPacket(PacketType.PLAY_REQUEST, new PlayRequest(newCharId));
            PlayResponse playResp = service.request(playPacket, PlayResponse.class);
            System.out.println("Play Response: " + playResp);
            if (ResponseCode.OK != playResp.code()) {
                throw new AssertionError("PLAY failed: " + playResp.errorMessage());
            }
            System.out.println("Entered zone: " + playResp.targetZoneId());

            // 6. PING/PONG Validation
            System.out.println("\nStep 6: Sending PING/PONG validation...");
            var pingPacket = new DecodedPacket(PacketType.PING_REQUEST, new PingRequest());
            PingResponse pingResp = service.request(pingPacket, PingResponse.class);
            System.out.println("Ping Response: " + pingResp);
            if (ResponseCode.OK != pingResp.code()) {
                throw new AssertionError("PING/PONG failed!");
            }
            System.out.println("PING/PONG validation succeeded.");

            // 7. LOGOUT
            System.out.println("\nStep 7: Sending LOGOUT...");
            var logoutPacket = new DecodedPacket(PacketType.LOGOUT_REQUEST, new LogoutRequest());
            LogoutResponse logoutResp = service.request(logoutPacket, LogoutResponse.class);
            System.out.println("Logout Response: " + logoutResp);
            if (ResponseCode.OK != logoutResp.code()) {
                throw new AssertionError("LOGOUT failed!");
            }
            System.out.println("LOGOUT succeeded.");

            // 8. Cleanup Character
            System.out.println("\nStep 8: Cleanup character...");
            var deletePacket = new DecodedPacket(PacketType.CHAR_DELETE_REQUEST, new CharDeleteRequest(newCharId));
            CharDeleteResponse deleteResp = service.request(deletePacket, CharDeleteResponse.class);
            System.out.println("Delete Response: " + deleteResp);
            if (ResponseCode.OK != deleteResp.code()) {
                throw new AssertionError("Character cleanup failed: " + deleteResp.errorMessage());
            }
            System.out.println("Cleanup completed successfully.");

            System.out.println("\n=== E2E Protocol Validation Harness Succeeded! ===");
            System.exit(0);

        } catch (Exception e) {
            System.err.println("\nE2E Validation Failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}