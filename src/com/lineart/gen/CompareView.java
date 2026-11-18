package com.lineart.gen;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

/**
 * 同尺寸滑动对比视图：
 *  - 原图与线稿使用完全相同的目标矩形（fitCenter），保证对比框中两者尺寸一致
 *  - 拖动分割线连续对比；长按显示完整原图；双击切换 原图/线稿 全屏
 */
public class CompareView extends View {

    private Bitmap origin, lineart;
    private float split = 0.5f;
    private boolean showOrigin = false;   // 长按 / 双击状态
    private boolean pinnedOrigin = false; // 双击锁定
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();
    private GestureDetector detector;
    private LongPressListener longPressListener;

    public interface LongPressListener {
        void onShowOriginChanged(boolean showing);
    }

    public CompareView(Context c, AttributeSet attrs) {
        super(c, attrs);
        linePaint.setColor(0xFFE53935);
        linePaint.setStrokeWidth(3f);
        handlePaint.setColor(0xFFE53935);
        handlePaint.setStyle(Paint.Style.FILL);
        labelPaint.setColor(0xFFFFFFFF);
        labelPaint.setTextSize(28f);
        labelPaint.setShadowLayer(4f, 0, 0, 0x88000000);

        detector = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent e) {
                if (pinnedOrigin) return;
                showOrigin = true;
                notifyState();
                invalidate();
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                pinnedOrigin = !pinnedOrigin;
                showOrigin = pinnedOrigin;
                notifyState();
                invalidate();
                return true;
            }
        });
    }

    public void setLongPressListener(LongPressListener l) {
        this.longPressListener = l;
    }

    private void notifyState() {
        if (longPressListener != null) longPressListener.onShowOriginChanged(showOrigin);
    }

    public void setImages(Bitmap originBmp, Bitmap lineartBmp) {
        this.origin = originBmp;
        this.lineart = lineartBmp;
        invalidate();
    }

    public void setLineart(Bitmap bmp) {
        this.lineart = bmp;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (origin == null || lineart == null) return;
        int vw = getWidth(), vh = getHeight();
        // 两图同尺寸，共用 fitCenter 目标矩形
        computeDst(vw, vh, origin.getWidth(), origin.getHeight());

        if (showOrigin) {
            canvas.drawBitmap(origin, null, dst, paint);
        } else {
            int splitX = Math.round(dst.left + dst.width() * split);
            // 左侧：原图
            canvas.save();
            canvas.clipRect(dst.left, dst.top, splitX, dst.bottom);
            canvas.drawBitmap(origin, null, dst, paint);
            canvas.restore();
            // 右侧：线稿
            canvas.save();
            canvas.clipRect(splitX, dst.top, dst.right, dst.bottom);
            canvas.drawBitmap(lineart, null, dst, paint);
            canvas.restore();
            // 分割线与手柄
            canvas.drawLine(splitX, dst.top, splitX, dst.bottom, linePaint);
            float cy = dst.top + dst.height() / 2f;
            canvas.drawCircle(splitX, cy, 20f, handlePaint);
            canvas.drawLine(splitX - 8f, cy, splitX + 8f, cy, labelPaint);
            // 标签
            canvas.drawText("原图", dst.left + 8, dst.top + labelPaint.getTextSize() + 4, labelPaint);
            float lw = labelPaint.measureText("线稿");
            canvas.drawText("线稿", dst.right - lw - 8, dst.top + labelPaint.getTextSize() + 4, labelPaint);
        }
    }

    private void computeDst(int vw, int vh, int bw, int bh) {
        if (bw <= 0 || bh <= 0) {
            dst.set(0, 0, vw, vh);
            return;
        }
        float scale = Math.min(vw / (float) bw, vh / (float) bh);
        float dw = bw * scale, dh = bh * scale;
        float left = (vw - dw) / 2f, top = (vh - dh) / 2f;
        dst.set(left, top, left + dw, top + dh);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        detector.onTouchEvent(event);
        if (showOrigin) {
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (!pinnedOrigin) {
                    showOrigin = false;
                    notifyState();
                    invalidate();
                }
            }
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN
                || event.getAction() == MotionEvent.ACTION_MOVE) {
            if (origin != null && dst.width() > 0) {
                float x = event.getX();
                split = Math.max(0f, Math.min(1f, (x - dst.left) / dst.width()));
                invalidate();
            }
        }
        return true;
    }
}
