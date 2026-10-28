package com.razdrop.app;

import com.razdrop.app.model.DeviceType;
import com.razdrop.app.model.TransferBatch;
import com.razdrop.app.model.TransferItem;
import org.junit.Assert;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;

public class FileUtilsTest {

    @Test
    public void testFormatBytes() {
        Assert.assertEquals("500 B", TransferItem.formatBytes(500));
        Assert.assertEquals("1.0 KB", TransferItem.formatBytes(1024));
        Assert.assertEquals("1.5 MB", TransferItem.formatBytes((long) (1.5 * 1024 * 1024)));
        Assert.assertEquals("2.0 GB", TransferItem.formatBytes(2L * 1024 * 1024 * 1024));
    }

    @Test
    public void testBatchProgressCalculations() {
        List<TransferItem> items = new ArrayList<>();
        items.add(new TransferItem("1", "doc1.pdf", 1000, "application/pdf", null));
        items.add(new TransferItem("2", "doc2.pdf", 3000, "application/pdf", null));

        TransferBatch batch = new TransferBatch("batch-1", true, null, items);
        Assert.assertEquals(4000L, batch.getTotalBytes());

        batch.setTransferredBytes(2000L);
        Assert.assertEquals(50, batch.getOverallProgressPercent());

        batch.setSpeedBytesPerSec(1000.0);
        batch.setEtaSeconds(2);
        Assert.assertEquals("1000 B/s", batch.getFormattedSpeed());
        Assert.assertEquals("2s", batch.getFormattedEta());
    }

    @Test
    public void testDeviceTypeConversion() {
        Assert.assertEquals(DeviceType.PHONE, DeviceType.fromInt(1));
        Assert.assertEquals(DeviceType.TABLET, DeviceType.fromInt(2));
        Assert.assertEquals(DeviceType.TV, DeviceType.fromInt(3));
        Assert.assertEquals(DeviceType.DESKTOP, DeviceType.fromInt(4));
        Assert.assertEquals(DeviceType.UNKNOWN, DeviceType.fromInt(99));

        Assert.assertEquals(1, DeviceType.PHONE.toInt());
        Assert.assertEquals(3, DeviceType.TV.toInt());
    }
}
