package com.razdrop.app.ui.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import com.razdrop.app.util.QrUtils;

public class QrCodeView extends View {
    private Bitmap qrBitmap;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect srcRect = new Rect();
    private final Rect dstRect = new Rect();

    public QrCodeView(Context context) {
        super(context);
    }

    public QrCodeView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setQrContent(String content) {
        if (content == null || content.isEmpty()) {
            qrBitmap = null;
            invalidate();
            return;
        }
        post(() -> {
            int w = getWidth() > 0 ? getWidth() : 400;
            int h = getHeight() > 0 ? getHeight() : 400;
            int size = Math.min(w, h);
            qrBitmap = QrUtils.generateQrBitmap(content, size, size);
            invalidate();
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (qrBitmap != null) {
            int w = getWidth();
            int h = getHeight();
            int size = Math.min(w, h);
            int left = (w - size) / 2;
            int top = (h - size) / 2;

            srcRect.set(0, 0, qrBitmap.getWidth(), qrBitmap.getHeight());
            dstRect.set(left, top, left + size, top + size);
            canvas.drawBitmap(qrBitmap, srcRect, dstRect, paint);
        }
    }
}
