import com.lineart.gen.core.LineArtEngine;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;

public class DebugMag2 {
    public static void main(String[] args) throws Exception {
        BufferedImage img = ImageIO.read(new File("sample1.jpg"));
        int w = img.getWidth(), h = img.getHeight();
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);
        int n = w * h;
        float[] gray = new float[n];
        for (int i = 0; i < n; i++) {
            int c = px[i];
            gray[i] = (0.299f*((c>>16)&0xFF) + 0.587f*((c>>8)&0xFF) + 0.114f*(c&0xFF)) / 255f;
        }
        float[] blur = LineArtEngine.gaussianBlur(gray, w, h, 1.3f);
        float[] mag = new float[n], dir = new float[n];
        for (int y = 1; y < h - 1; y++)
            for (int x = 1; x < w - 1; x++) {
                int i = y*w+x;
                float gx = (blur[i-w+1]+2*blur[i+1]+blur[i+w+1]) - (blur[i-w-1]+2*blur[i-1]+blur[i+w-1]);
                float gy = (blur[i+w-1]+2*blur[i+w]+blur[i+w+1]) - (blur[i-w-1]+2*blur[i-w]+blur[i-w+1]);
                mag[i] = (float) Math.sqrt(gx*gx+gy*gy);
                dir[i] = (float) Math.atan2(gy, gx);
            }
        // NMS
        float[] nms = new float[n];
        for (int y = 1; y < h - 1; y++)
            for (int x = 1; x < w - 1; x++) {
                int i = y*w+x; float m = mag[i];
                if (m <= 0f) continue;
                float a = dir[i]; if (a < 0) a += Math.PI;
                float d1, d2;
                if (a < Math.PI/8 || a >= Math.PI*7/8) { d1 = mag[i-1]; d2 = mag[i+1]; }
                else if (a < Math.PI*3/8) { d1 = mag[i-w+1]; d2 = mag[i+w-1]; }
                else if (a < Math.PI*5/8) { d1 = mag[i-w]; d2 = mag[i+w]; }
                else { d1 = mag[i-w-1]; d2 = mag[i+w+1]; }
                if (m >= d1 && m >= d2) nms[i] = m;
            }
        float[] sorted = nms.clone(); Arrays.sort(sorted);
        float norm = sorted[(int)(0.997*(n-1))];
        System.out.println("nms p997 = " + norm);
        // 种子图（>highT）与 lowT 图
        BufferedImage seedImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        BufferedImage lowImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        float highT = norm * 0.244f, lowT = highT * 0.36f;
        System.out.println("highT=" + highT + " lowT=" + lowT);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y*w+x;
            seedImg.setRGB(x, y, nms[i] >= highT ? 0x000000 : 0xFFFFFF);
            lowImg.setRGB(x, y, nms[i] >= lowT ? 0x000000 : 0xFFFFFF);
        }
        ImageIO.write(seedImg, "png", new File("debug_seed.png"));
        ImageIO.write(lowImg, "png", new File("debug_low.png"));
        System.out.println("done");
    }
}
