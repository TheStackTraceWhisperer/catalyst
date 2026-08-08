package catalyst.common.network;

public enum ServiceType {
    LOGIN((byte) 0x01),
    LOBBY((byte) 0x02),
    WORLD((byte) 0x03);

    private final byte flag;

    ServiceType(byte flag) {
        this.flag = flag;
    }

    public byte flag() {
        return flag;
    }

    public static ServiceType fromFlag(byte flag) {
        for (ServiceType type : values()) {
            if (type.flag == flag) {
                return type;
            }
        }
        return null;
    }
}
