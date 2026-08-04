package catalyst.ffxi.client.network;

import catalyst.ffxi.common.net.MessageFrame;
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
            MessageFrame loginResp = service.login(host, port, "dev", "dev");
            System.out.println("Login Response: " + loginResp);
            if (!"LOGIN_OK".equals(loginResp.type())) {
                throw new AssertionError("LOGIN failed: " + loginResp.get("message"));
            }
            String authToken = loginResp.get("authToken");
            System.out.println("AuthToken acquired: " + authToken);

            // 2. CHAR_CREATE
            System.out.println("\nStep 2: Sending CHAR_CREATE...");
            String charName = "E2ETest" + (100 + new java.util.Random().nextInt(900));
            MessageFrame createResp = service.createCharacter(
                host, port, authToken, charName, 1, 1, 3, 1, "0"
            );
            System.out.println("Create Response: " + createResp);
            if (!"CHAR_CREATE_OK".equals(createResp.type())) {
                throw new AssertionError("CHAR_CREATE failed: " + createResp.get("message"));
            }
            String newCharId = createResp.get("characterId");
            System.out.println("Created character: Name=" + charName + " ID=" + newCharId);

            // 3. CHAR_LIST verification
            System.out.println("\nStep 3: Sending CHAR_LIST verification...");
            List<QuicGatewayService.CharacterSummary> summaries = service.listCharacterSummaries(host, port, authToken);
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
            MessageFrame selectResp = service.selectCharacter(host, port, authToken, newCharId);
            System.out.println("Select Response: " + selectResp);
            if (!"CHAR_SELECT_OK".equals(selectResp.type())) {
                throw new AssertionError("CHAR_SELECT failed: " + selectResp.get("message"));
            }

            // 5. PLAY
            System.out.println("\nStep 5: Sending PLAY...");
            MessageFrame playResp = service.play(host, port, authToken, newCharId);
            System.out.println("Play Response: " + playResp);
            if (!"PLAY_OK".equals(playResp.type())) {
                throw new AssertionError("PLAY failed: " + playResp.get("message"));
            }
            String sessionId = playResp.get("sessionId");
            System.out.println("Session ID acquired: " + sessionId);

            // 6. PING/PONG validation
            System.out.println("\nStep 6: Sending PING/PONG validation...");
            MessageFrame pingResp = service.ping(host, port, sessionId);
            System.out.println("Ping Response: " + pingResp);
            if (!"PONG".equals(pingResp.type())) {
                throw new AssertionError("PING/PONG failed!");
            }
            System.out.println("PING/PONG validation succeeded.");

            // 7. LOGOUT
            System.out.println("\nStep 7: Sending LOGOUT...");
            MessageFrame logoutResp = service.logout(host, port, sessionId);
            System.out.println("Logout Response: " + logoutResp);
            if (!"BYE".equals(logoutResp.type())) {
                throw new AssertionError("LOGOUT failed!");
            }
            System.out.println("LOGOUT succeeded.");

            // 8. Cleanup Character
            System.out.println("\nStep 8: Cleanup character...");
            MessageFrame deleteResp = service.deleteCharacter(host, port, authToken, newCharId);
            System.out.println("Delete Response: " + deleteResp);
            if (!"CHAR_DELETE_OK".equals(deleteResp.type())) {
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
