package com.razdrop.app;

import com.razdrop.app.model.DeviceType;
import com.razdrop.app.model.TransferItem;
import com.razdrop.app.network.protocol.FileHeaderPacket;
import com.razdrop.app.network.protocol.HandshakePacket;
import com.razdrop.app.network.protocol.ManifestPacket;
import com.razdrop.app.network.protocol.PacketType;
import com.razdrop.app.network.protocol.RazPacket;
import org.junit.Assert;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;

public class ProtocolSerializationTest {

    @Test
    public void testHandshakeSerialization() {
        HandshakePacket packet = new HandshakePacket("Living Room TV", DeviceType.TV, "4821");
        String json = RazPacket.getGson().toJson(packet);

        HandshakePacket deserialized = RazPacket.getGson().fromJson(json, HandshakePacket.class);
        Assert.assertEquals(1, deserialized.getVersion());
        Assert.assertEquals("Living Room TV", deserialized.getDeviceName());
        Assert.assertEquals(DeviceType.TV, deserialized.getDeviceType());
        Assert.assertEquals("4821", deserialized.getPin());
        Assert.assertTrue(deserialized.isAccepted());
    }

    @Test
    public void testManifestSerialization() {
        List<TransferItem> items = new ArrayList<>();
        items.add(new TransferItem("1", "video.mp4", 1024 * 1024 * 50, "video/mp4", null));
        items.add(new TransferItem("2", "document.pdf", 1024 * 200, "application/pdf", null));

        ManifestPacket packet = new ManifestPacket("batch-123", 1024 * 1024 * 50 + 1024 * 200, items);
        String json = RazPacket.getGson().toJson(packet);

        ManifestPacket deserialized = RazPacket.getGson().fromJson(json, ManifestPacket.class);
        Assert.assertEquals("batch-123", deserialized.getBatchId());
        Assert.assertEquals(2, deserialized.getItems().size());
        Assert.assertEquals("video.mp4", deserialized.getItems().get(0).getName());
        Assert.assertEquals(1024 * 1024 * 50, deserialized.getItems().get(0).getSize());
    }

    @Test
    public void testFileHeaderSerialization() {
        FileHeaderPacket packet = new FileHeaderPacket(0, "item-uuid", "image.jpg", 4096000L, "image/jpeg");
        String json = RazPacket.getGson().toJson(packet);

        FileHeaderPacket deserialized = RazPacket.getGson().fromJson(json, FileHeaderPacket.class);
        Assert.assertEquals(0, deserialized.getIndex());
        Assert.assertEquals("item-uuid", deserialized.getId());
        Assert.assertEquals("image.jpg", deserialized.getName());
        Assert.assertEquals(4096000L, deserialized.getSize());
        Assert.assertEquals("image/jpeg", deserialized.getMimeType());
    }

    @Test
    public void testPacketTypeCodes() {
        for (PacketType type : PacketType.values()) {
            PacketType recovered = PacketType.fromCode(type.getCode());
            Assert.assertEquals(type, recovered);
        }
    }
}
