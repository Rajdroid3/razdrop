package com.razdrop.app;

import com.razdrop.app.network.protocol.PacketType;
import com.razdrop.app.network.protocol.RazPacket;
import org.junit.Assert;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class PacketFramingTest {

    @Test
    public void testFramingRoundTrip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);

        RazPacket.writeControlPacket(out, PacketType.PING);

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
        RazPacket.FrameHeader header = RazPacket.readHeader(in);

        Assert.assertEquals(PacketType.PING, header.type);
        Assert.assertEquals(0, header.payloadLength);
    }

    @Test
    public void testRawChunkFraming() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);

        byte[] sampleData = "Sample RazDrop file streaming payload".getBytes();
        RazPacket.writeRawChunk(out, 4, sampleData, 0, sampleData.length);

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
        RazPacket.FrameHeader header = RazPacket.readHeader(in);

        Assert.assertEquals(PacketType.FILE_DATA, header.type);
        Assert.assertEquals(4 + sampleData.length, header.payloadLength);

        int streamIndex = in.readInt();
        Assert.assertEquals(4, streamIndex);

        byte[] readBack = new byte[sampleData.length];
        in.readFully(readBack);
        Assert.assertArrayEquals(sampleData, readBack);
    }

    @Test(expected = IOException.class)
    public void testInvalidMagicThrowsException() throws IOException {
        byte[] invalidHeader = new byte[]{0x00, 0x01, 0x02, 0x03, 0x01, 0x00, 0x00, 0x00, 0x00};
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(invalidHeader));
        RazPacket.readHeader(in);
    }

    @Test
    public void testStreamResynchronizationWithUnreadGarbageBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);

        // Simulate 17 stray bytes from unconsumed JSON
        baos.write("{\"isAccepted\":true}".getBytes());

        // Then a valid packet
        RazPacket.writeControlPacket(out, PacketType.FILE_ACK);

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
        RazPacket.FrameHeader header = RazPacket.readHeader(in);

        Assert.assertEquals(PacketType.FILE_ACK, header.type);
        Assert.assertEquals(0, header.payloadLength);
    }
}
