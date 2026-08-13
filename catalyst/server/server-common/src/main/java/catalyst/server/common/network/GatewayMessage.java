package catalyst.server.common.network;

import catalyst.common.network.DecodedPacket;

public record GatewayMessage(ClientSession session, DecodedPacket packet) {}