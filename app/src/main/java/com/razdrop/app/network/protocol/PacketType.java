package com.razdrop.app.network.protocol;

public enum PacketType {
    HANDSHAKE(0x01),
    HANDSHAKE_ACK(0x02),
    TRANSFER_MANIFEST(0x03),
    TRANSFER_ACCEPT(0x04),
    FILE_START(0x05),
    FILE_DATA(0x06),
    FILE_END(0x07),
    FILE_ACK(0x08),
    TRANSFER_COMPLETE(0x09),
    CANCEL(0x0A),
    PING(0x0B),
    PONG(0x0C);

    private final byte code;

    PacketType(int code) {
        this.code = (byte) code;
    }

    public byte getCode() {
        return code;
    }

    public static PacketType fromCode(byte code) {
        for (PacketType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
