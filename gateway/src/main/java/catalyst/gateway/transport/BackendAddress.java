package catalyst.gateway.transport;

public record BackendAddress(String host, int port) {
    @Override
    public String toString() {
        return host + ":" + port;
    }
}
