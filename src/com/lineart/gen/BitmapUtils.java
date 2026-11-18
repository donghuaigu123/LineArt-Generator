package com.lineart.gen;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import java.io.InputStream;

/** 图片解码 / EXIF 方向 / Bitmap 与像素数组互转 */
public final class BitmapUtils {

    /** 解码并限制最大边长（自动降采样，同时处理 JPEG EXIF 旋转） */
    public static Bitmap decode(Context ctx, Uri uri, int maxSide) throws Exception {
        InputStream in = ctx.getContentResolver().openInputStream(uri);
        if (in == null) throw new IllegalStateException("无法读取图片");
        int rotation = readExifRotation(in);
        in.close();

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        in = ctx.getContentResolver().openInputStream(uri);
        BitmapFactory.decodeStream(in, null, opts);
        if (in != null) in.close();

        int w = opts.outWidth, h = opts.outHeight;
        if (w <= 0 || h <= 0) throw new IllegalStateException("图片格式不支持");
        int sample = 1;
        while (Math.max(w, h) / sample > maxSide) sample <<= 1;
        // 微调：允许 1/3 步进更贴近目标尺寸
        if (Math.max(w, h) / sample > maxSide * 1.5 && sample > 1) sample += sample / 2;

        opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        in = ctx.getContentResolver().openInputStream(uri);
        Bitmap bmp = BitmapFactory.decodeStream(in, null, opts);
        if (in != null) in.close();
        if (bmp == null) throw new IllegalStateException("图片解码失败");

        // 若仍超过限制则精确缩放
        int bw = bmp.getWidth(), bh = bmp.getHeight();
        if (Math.max(bw, bh) > maxSide) {
            float s = maxSide / (float) Math.max(bw, bh);
            Bitmap scaled = Bitmap.createScaledBitmap(bmp, Math.round(bw * s), Math.round(bh * s), true);
            if (scaled != bmp) bmp.recycle();
            bmp = scaled;
        }
        if (rotation != 0) {
            Matrix m = new Matrix();
            m.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            if (rotated != bmp) bmp.recycle();
            bmp = rotated;
        }
        return bmp;
    }

    /** Bitmap → ARGB int[] */
    public static int[] toPixels(Bitmap bmp) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        return px;
    }

    /** ARGB int[] → Bitmap */
    public static Bitmap fromPixels(int[] px, int w, int h) {
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    /** 极简 JPEG EXIF Orientation 读取（只解析 APP1 / IFD0 的 0x0112），失败返回 0 */
    static int readExifRotation(InputStream in) {
        try {
            int b;
            if ((b = in.read()) != 0xFF || (b = in.read()) != 0xD8) return 0;
            while (true) {
                int marker = in.read();
                if (marker != 0xFF) {
                    if (marker < 0) return 0;
                    continue;
                }
                marker = in.read();
                if (marker == 0xE1) { // APP1
                    int len = (in.read() << 8) | in.read();
                    byte[] data = new byte[len - 2];
                    int read = 0;
                    while (read < data.length) {
                        int r = in.read(data, read, data.length - read);
                        if (r < 0) break;
                        read += r;
                    }
                    return parseExifOrientation(data);
                } else if (marker == 0xDA || marker == 0xD9) {
                    return 0; // 进入图像数据，无 EXIF
                } else {
                    int len = (in.read() << 8) | in.read();
                    long skip = len - 2;
                    while (skip > 0) {
                        long s = in.skip(skip);
                        if (s <= 0) return 0;
                        skip -= s;
                    }
                }
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private static int parseExifOrientation(byte[] d) {
        try {
            if (d.length < 14) return 0;
            if (!(d[0] == 'E' && d[1] == 'x' && d[2] == 'i' && d[3] == 'f')) return 0;
            boolean little = d[6] == 0x49 && d[7] == 0x49;
            int ifdOff = readInt(d, 10, little);
            if (ifdOff + 2 > d.length) return 0;
            int count = readShort(d, ifdOff, little);
            for (int i = 0; i < count; i++) {
                int entry = ifdOff + 2 + i * 12;
                if (entry + 12 > d.length) return 0;
                int tag = readShort(d, entry, little);
                if (tag == 0x0112) {
                    int val = readShort(d, entry + 8, little);
                    switch (val) {
                        case 3: return 180;
                        case 6: return 90;
                        case 8: return 270;
                        default: return 0;
                    }
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static int readShort(byte[] d, int off, boolean little) {
        if (little) return ((d[off + 1] & 0xFF) << 8) | (d[off] & 0xFF);
        return ((d[off] & 0xFF) << 8) | (d[off + 1] & 0xFF);
    }

    private static int readInt(byte[] d, int off, boolean little) {
        if (little)
            return ((d[off + 3] & 0xFF) << 24) | ((d[off + 2] & 0xFF) << 16)
                    | ((d[off + 1] & 0xFF) << 8) | (d[off] & 0xFF);
        return ((d[off] & 0xFF) << 24) | ((d[off + 1] & 0xFF) << 16)
                | ((d[off + 2] & 0xFF) << 8) | (d[off + 3] & 0xFF);
    }

    private BitmapUtils() {}
}
