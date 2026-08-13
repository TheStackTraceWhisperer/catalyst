package catalyst.client.network;

import catalyst.client.network.handler.*;
import catalyst.common.network.PacketRegistry;
import catalyst.common.network.PacketType;
import catalyst.common.network.TlsProperties;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

/**
 * Micronaut Factory to expose ClientDispatcher and QuicGateway as singleton beans.
 * Keeps ClientDispatcher and QuicGateway dependency-free POJOs.
 */
@Factory
public class ClientNetworkFactory {

    @Singleton
    PacketRegistry packetRegistry(
      PingResponseHandler pingResponseHandler,
      LoginResponseHandler loginResponseHandler,
      LogoutResponseHandler logoutResponseHandler,
      CharListResponseHandler charListResponseHandler,
      CharCreateResponseHandler charCreateResponseHandler,
      CharDeleteResponseHandler charDeleteResponseHandler,
      CharSelectResponseHandler charSelectResponseHandler,
      PlayResponseHandler playResponseHandler
    ) {
        PacketRegistry registry = new PacketRegistry();

        // Register O(1) handlers for incoming server packets
        registry.register(PacketType.PING_RESPONSE, pingResponseHandler);
        registry.register(PacketType.LOGIN_RESPONSE, loginResponseHandler);
        registry.register(PacketType.LOGOUT_RESPONSE, logoutResponseHandler);
        registry.register(PacketType.CHAR_LIST_RESPONSE, charListResponseHandler);
        registry.register(PacketType.CHAR_CREATE_RESPONSE, charCreateResponseHandler);
        registry.register(PacketType.CHAR_DELETE_RESPONSE, charDeleteResponseHandler);
        registry.register(PacketType.CHAR_SELECT_RESPONSE, charSelectResponseHandler);
        registry.register(PacketType.PLAY_RESPONSE, playResponseHandler);

        return registry;
    }

    @Singleton
    ClientChannelInitializer clientChannelInitializer(PacketRegistry packetRegistry) {
        return new ClientChannelInitializer(packetRegistry);
    }

    @Singleton
    public ClientTransport quicGateway(ClientChannelInitializer initializer, TlsProperties tlsProps) {
        return new ClientTransport(initializer, tlsProps);
    }
}
