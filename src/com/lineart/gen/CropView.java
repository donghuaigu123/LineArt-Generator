package com.lineart.gen;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 自由裁剪视图：图片 fitCenter 显示，叠加可拖动矩形选区。
 *  - 8 个手柄（4 角 + 4 边中）可独立调整选区边
 *  - 框内拖动整体平移
 *  - 选区外半透明遮罩 + 三分线辅助构图
 *  - getCroppedBitmap() 把 view 坐标映射回图片像素并裁剪
 */
public class CropView extends View {

    private Bitmap bitmap;
    private final Paint imgPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleStroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF imageRect = new RectF();
    private final RectF crop = new RectF();
    private float handleR = 22f;
    private static final float MIN_SIZE = 80f;
    private int activeMode = -1; // -1 无, 0..7 手柄, 8 移动
    private float lastX, lastY;

    public CropView(Context c, AttributeSet a) {
        super(c, a);
        maskPaint.setColor(0x99000000);
        framePaint.setColor(0xFFFFFFFF);
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(3f);
        gridPaint.setColor(0x88FFFFFF);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);
        handlePaint.setColor(0xFFFFFFFF);
        handlePaint.setStyle(Paint.Style.FILL);
        handleStroke.setColor(0xFFC62828);
        handleStroke.setStyle(Paint.Style.STROKE);
        handleStroke.setStrokeWidth(3f);
    }

    public void setBitmap(Bitmap b) {
        bitmap = b;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        computeImageRect();
        if (crop.width() == 0 || crop.height() == 0) resetCrop();
        else clamp();
        invalidate();
    }

    private void computeImageRect() {
        if (bitmap == null) { imageRect.set(0, 0, 0, 0); return; }
        int vw = getWidth(), vh = getHeight();
        if (vw == 0 || vh == 0) { imageRect.set(0, 0, 0, 0); return; }
        float scale = Math.min(vw / (float) bitmap.getWidth(), vh / (float) bitmap.getHeight());
        float dw = bitmap.getWidth() * scale, dh = bitmap.getHeight() * scale;
        float left = (vw - dw) / 2f, top = (vh - dh) / 2f;
        imageRect.set(left, top, left + dw, top + dh);
    }

    /** 重置选区为图片 90% 区域 */
    public void resetCrop() {
        if (imageRect.width() == 0) return;
        float m = Math.min(imageRect.width(), imageRect.height()) * 0.05f;
        crop.set(imageRect.left + m, imageRect.top + m, imageRect.right - m, imageRect.bottom - m);
        clamp();
        invalidate();
    }

    private void clamp() {
        if (imageRect.width() == 0) return;
        if (crop.left < imageRect.left) crop.left = imageRect.left;
        if (crop.top < imageRect.top) crop.top = imageRect.top;
        if (crop.right > imageRect.right) crop.right = imageRect.right;
        if (crop.bottom > imageRect.bottom) crop.bottom = imageRect.bottom;
        if (crop.width() < MIN_SIZE) {
            float cx = (crop.left + crop.right) / 2f;
            crop.left = cx - MIN_SIZE / 2f;
            crop.right = cx + MIN_SIZE / 2f;
            if (crop.left < imageRect.left) { crop.left = imageRect.left; crop.right = imageRect.left + MIN_SIZE; }
            if (crop.right > imageRect.right) { crop.right = imageRect.right; crop.left = imageRect.right - MIN_SIZE; }
        }
        if (crop.height() < MIN_SIZE) {
            float cy = (crop.top + crop.bottom) / 2f;
            crop.top = cy - MIN_SIZE / 2f;
            crop.bottom = cy + MIN_SIZE / 2f;
            if (crop.top < imageRect.top) { crop.top = imageRect.top; crop.bottom = imageRect.top + MIN_SIZE; }
            if (crop.bottom > imageRect.bottom) { crop.bottom = imageRect.bottom; crop.top = imageRect.bottom - MIN_SIZE; }
        }
    }

    // 手柄序号：0左上 1上中 2右上 3右中 4右下 5下中 6左下 7左中
    private RectF[] handleRects() {
        float L = crop.left, T = crop.top, R = crop.right, B = crop.bottom;
        float Cx = (L + R) / 2f, Cy = (T + B) / 2f, h = handleR;
        return new RectF[] {
            new RectF(L - h, T - h, L + h, T + h),
            new RectF(Cx - h, T - h, Cx + h, T + h),
            new RectF(R - h, T - h, R + h, T + h),
            new RectF(R - h, Cy - h, R + h, Cy + h),
            new RectF(R - h, B - h, R + h, B + h),
            new RectF(Cx - h, B - h, Cx + h, B + h),
            new RectF(L - h, B - h, L + h, B + h),
            new RectF(L - h, Cy - h, L + h, Cy + h),
        };
    }

    private int hitTest(float x, float y) {
        RectF[] hs = handleRects();
        for (int i = 0; i < 8; i++) if (hs[i].contains(x, y)) return i;
        if (crop.contains(x, y)) return 8;
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                activeMode = hitTest(x, y);
                lastX = x; lastY = y;
                return activeMode >= 0;
            case MotionEvent.ACTION_MOVE: {
                if (activeMode < 0) return false;
                float dx = x - lastX, dy = y - lastY;
                lastX = x; lastY = y;
                if (activeMode == 8) {
                    crop.offset(dx, dy);
                    clamp();
                } else {
                    switch (activeMode) {
                        case 0: crop.left += dx; crop.top += dy; break;
                        case 1: crop.top += dy; break;
                        case 2: crop.right += dx; crop.top += dy; break;
                        case 3: crop.right += dx; break;
                        case 4: crop.right += dx; crop.bottom += dy; break;
                        case 5: crop.bottom += dy; break;
                        case 6: crop.left += dx; crop.bottom += dy; break;
                        case 7: crop.left += dx; break;
                    }
                    clamp();
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activeMode = -1;
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (bitmap == null || imageRect.width() == 0) return;
        c.drawBitmap(bitmap, null, imageRect, imgPaint);
        // 选区外遮罩（EVEN_ODD 双矩形）
        Path p = new Path();
        p.setFillType(Path.FillType.EVEN_ODD);
        p.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        p.addRect(crop, Path.Direction.CW);
        c.drawPath(p, maskPaint);
        // 框 + 三分线
        c.drawRect(crop, framePaint);
        float w3 = crop.width() / 3f, h3 = crop.height() / 3f;
        c.drawLine(crop.left + w3, crop.top, crop.left + w3, crop.bottom, gridPaint);
        c.drawLine(crop.left + 2 * w3, crop.top, crop.left + 2 * w3, crop.bottom, gridPaint);
        c.drawLine(crop.left, crop.top + h3, crop.right, crop.top + h3, gridPaint);
        c.drawLine(crop.left, crop.top + 2 * h3, crop.right, crop.top + 2 * h3, gridPaint);
        // 手柄
        for (RectF hr : handleRects()) {
            c.drawCircle(hr.centerX(), hr.centerY(), handleR, handlePaint);
            c.drawCircle(hr.centerX(), hr.centerY(), handleR, handleStroke);
        }
    }

    /** 把选区映射回图片像素坐标并裁剪出 Bitmap */
    public Bitmap getCroppedBitmap() {
        if (bitmap == null || imageRect.width() == 0) return null;
        float sx = bitmap.getWidth() / imageRect.width();
        float sy = bitmap.getHeight() / imageRect.height();
        int left = Math.round((crop.left - imageRect.left) * sx);
        int top = Math.round((crop.top - imageRect.top) * sy);
        int right = Math.round((crop.right - imageRect.left) * sx);
        int bottom = Math.round((crop.bottom - imageRect.top) * sy);
        int bw = bitmap.getWidth(), bh = bitmap.getHeight();
        left = Math.max(0, Math.min(bw, left));
        top = Math.max(0, Math.min(bh, top));
        right = Math.max(0, Math.min(bw, right));
        bottom = Math.max(0, Math.min(bh, bottom));
        if (right - left < 2 || bottom - top < 2) {
            return bitmap.copy(Bitmap.Config.ARGB_8888, true);
        }
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);
    }
}
