package catalyst.tests.e2e;

import catalyst.common.network.ResponseCode;
import catalyst.client.network.QuicGatewayService;
import catalyst.client.network.QuicGatewayService.CharacterSummary;
import java.io.IOException;
import java.util.List;

public final class E2EValidationHarness {

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 35555;
        
        System.out.println("=== Starting E2E Protocol Validation Harness ===");
        System.out.println("Target: " + host + ":" + port);

        try (QuicGatewayService service = new QuicGatewayService()) {
            // 1. LOGIN
            System.out.println("\nStep 1: Sending LOGIN for dev/dev...");
            var loginResp = service.login(host, port, "dev", "dev");
            System.out.println("Login Response: " + loginResp);
            if (ResponseCode.OK != loginResp.code()) {
                throw new AssertionError("LOGIN failed: " + loginResp.message());
            }
            String authToken = loginResp.authToken();
            System.out.println("AuthToken acquired: " + authToken);

            // 2. CHAR_CREATE
            System.out.println("\nStep 2: Sending CHAR_CREATE...");
            String charName = "EeeTest" + 
                (char)('A' + new java.util.Random().nextInt(26)) + 
                (char)('A' + new java.util.Random().nextInt(26)) + 
                (char)('A' + new java.util.Random().nextInt(26));
            var createResp = service.createCharacter(
                host, port, authToken, charName, 1, 1, 3, 1, "0"
            );
            System.out.println("Create Response: " + createResp);
            if (ResponseCode.OK != createResp.code()) {
                throw new AssertionError("CHAR_CREATE failed: " + createResp.message());
            }
            String newCharId = Long.toString(createResp.characterId());
            System.out.println("Created character: Name=" + charName + " ID=" + newCharId);

            // 3. CHAR_LIST verification
            System.out.println("\nStep 3: Sending CHAR_LIST verification...");
            List<CharacterSummary> summaries = service.listCharacterSummaries(host, port, authToken);
            System.out.println("Summaries retrieved: " + summaries.size());
            boolean found = false;
            for (var c : summaries) {
                System.out.println(" - Char: ID=" + c.id() + " Name=" + c.name() + " Zone=" + c.nationName());
                if (c.id().equals(newCharId)) {
                    found = true;
                }
            }
            if (!found) {
                throw new AssertionError("Newly created character ID " + newCharId + " not found in CHAR_LIST!");
            }
            System.out.println("CHAR_LIST verification succeeded.");

            // 4. CHAR_SELECT
            System.out.println("\nStep 4: Sending CHAR_SELECT...");
            var selectResp = service.selectCharacter(host, port, authToken, newCharId);
            System.out.println("Select Response: " + selectResp);
            if (ResponseCode.OK != selectResp.code()) {
                throw new AssertionError("CHAR_SELECT failed: " + selectResp.message());
            }

            // 5. PLAY
            System.out.println("\nStep 5: Sending PLAY...");
            var playResp = service.play(host, port, authToken, newCharId);
            System.out.println("Play Response: " + playResp);
            if (ResponseCode.OK != playResp.code()) {
                throw new AssertionError("PLAY failed: " + playResp.message());
            }
            String sessionId = playResp.sessionId();
            System.out.println("Session ID acquired: " + sessionId);

            // 6. PING/PONG validation
            System.out.println("\nStep 6: Sending PING/PONG validation...");
            var pingResp = service.ping(host, port, sessionId);
            System.out.println("Ping Response: " + pingResp);
            if (!"PONG".equals(pingResp.type())) {
                throw new AssertionError("PING/PONG failed!");
            }
            System.out.println("PING/PONG validation succeeded.");

            // 7. LOGOUT
            System.out.println("\nStep 7: Sending LOGOUT...");
            var logoutResp = service.logout(host, port, sessionId);
            System.out.println("Logout Response: " + logoutResp);
            if (logoutResp.sessionId() == null) {
                throw new AssertionError("LOGOUT failed!");
            }
            System.out.println("LOGOUT succeeded.");

            // 8. Cleanup Character
            System.out.println("\nStep 8: Cleanup character...");
            var deleteResp = service.deleteCharacter(host, port, authToken, newCharId);
            System.out.println("Delete Response: " + deleteResp);
            if (ResponseCode.OK != deleteResp.code()) {
                throw new AssertionError("Character cleanup failed!");
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
