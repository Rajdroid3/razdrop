package com.razdrop.app.util;

import android.graphics.Bitmap;
import android.graphics.Color;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.HashMap;
import java.util.Map;

public class QrUtils {
    public static Bitmap generateQrBitmap(String content, int width, int height) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 2);

            BitMatrix matrix = new MultiFormatWriter().encode(
                    content,
                    BarcodeFormat.QR_CODE,
                    width,
                    height,
                    hints
            );

            int matrixWidth = matrix.getWidth();
            int matrixHeight = matrix.getHeight();
            int[] pixels = new int[matrixWidth * matrixHeight];

            // High-contrast pure white background with dark midnight navy modules
            int colorForeground = 0xFF0A0F1D;
            int colorBackground = 0xFFFFFFFF;

            for (int y = 0; y < matrixHeight; y++) {
                int offset = y * matrixWidth;
                for (int x = 0; x < matrixWidth; x++) {
                    pixels[offset + x] = matrix.get(x, y) ? colorForeground : colorBackground;
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    public static String buildPairingUri(String ip, int port, String pin, String deviceName) {
        return "razdrop://connect?ip=" + ip + "&port=" + port + "&pin=" + (pin != null ? pin : "") + "&name=" + UriEncode(deviceName);
    }

    private static String UriEncode(String s) {
        if (s == null) return "";
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
