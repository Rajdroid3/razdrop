package com.razdrop.app.network.protocol;

import com.google.gson.Gson;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class RazPacket {
    public static final byte[] MAGIC = new byte[]{0x52, 0x41, 0x5A, 0x31}; // "RAZ1"
    public static final int DEFAULT_PORT = 58920;
    private static final Gson GSON = new Gson();

    public static class FrameHeader {
        public final PacketType type;
        public final int payloadLength;

        public FrameHeader(PacketType type, int payloadLength) {
            this.type = type;
            this.payloadLength = payloadLength;
        }
    }

    public static void writeJsonPacket(DataOutputStream out, PacketType type, Object payloadObj) throws IOException {
        String json = payloadObj != null ? GSON.toJson(payloadObj) : "";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        out.write(MAGIC);
        out.writeByte(type.getCode());
        out.writeInt(bytes.length);
        if (bytes.length > 0) {
            out.write(bytes);
        }
        out.flush();
    }

    public static void writeRawChunk(DataOutputStream out, int fileIndex, byte[] buffer, int offset, int length) throws IOException {
        out.write(MAGIC);
        out.writeByte(PacketType.FILE_DATA.getCode());
        out.writeInt(4 + length); // 4 bytes for fileIndex + chunk bytes
        out.writeInt(fileIndex);
        out.write(buffer, offset, length);
    }

    public static void writeControlPacket(DataOutputStream out, PacketType type) throws IOException {
        out.write(MAGIC);
        out.writeByte(type.getCode());
        out.writeInt(0);
        out.flush();
    }

    public static FrameHeader readHeader(DataInputStream in) throws IOException {
        int matchCount = 0;
        int skippedBytes = 0;
        final int MAX_SKIP = 65536;

        while (matchCount < 4) {
            int b = in.read();
            if (b == -1) {
                throw new java.io.EOFException("Unexpected EOF while reading RazDrop magic header");
            }
            if ((byte) b == MAGIC[matchCount]) {
                matchCount++;
            } else {
                if (matchCount > 0) {
                    matchCount = ((byte) b == MAGIC[0]) ? 1 : 0;
                }
                skippedBytes++;
                if (skippedBytes > MAX_SKIP) {
                    throw new IOException("Invalid RazDrop protocol magic header: unable to synchronize after skipping " + skippedBytes + " bytes");
                }
            }
        }
        byte typeCode = in.readByte();
        PacketType type = PacketType.fromCode(typeCode);
        if (type == null) {
            throw new IOException("Unknown RazDrop packet type: " + typeCode);
        }
        int length = in.readInt();
        if (length < 0 || length > 128 * 1024 * 1024) { // 128MB safety limit
            throw new IOException("Invalid packet payload length: " + length);
        }
        return new FrameHeader(type, length);
    }

    public static <T> T readJsonPayload(DataInputStream in, int length, Class<T> clazz) throws IOException {
        if (length == 0) return null;
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        String json = new String(bytes, StandardCharsets.UTF_8);
        return GSON.fromJson(json, clazz);
    }

    public static Gson getGson() {
        return GSON;
    }
}
