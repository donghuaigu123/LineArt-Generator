import com.lineart.gen.core.LineArtEngine;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;

public class DebugMag {
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
        // 模拟 standard: sigma = 1.3
        float[] blur = LineArtEngine.gaussianBlur(gray, w, h, 1.3f);
        float[] mag = new float[n];
        for (int y = 1; y < h - 1; y++)
            for (int x = 1; x < w - 1; x++) {
                int i = y*w+x;
                float gx = (blur[i-w+1]+2*blur[i+1]+blur[i+w+1]) - (blur[i-w-1]+2*blur[i-1]+blur[i+w-1]);
                float gy = (blur[i+w-1]+2*blur[i+w]+blur[i+w+1]) - (blur[i-w-1]+2*blur[i-w]+blur[i-w+1]);
                mag[i] = (float) Math.sqrt(gx*gx+gy*gy);
            }
        // 统计
        float[] sorted = mag.clone();
        Arrays.sort(sorted);
        System.out.printf("mean=%.4f p50=%.4f p90=%.4f p95=%.4f p99=%.4f p997=%.4f p999=%.4f max=%.4f%n",
            mean(mag), sorted[(int)(0.50*(n-1))], sorted[(int)(0.90*(n-1))], sorted[(int)(0.95*(n-1))],
            sorted[(int)(0.99*(n-1))], sorted[(int)(0.997*(n-1))], sorted[(int)(0.999*(n-1))], sorted[n-1]);
        // 采样葫芦左右轮廓边缘的梯度（图 589x1080，葫芦大约 x 200-450, y 250-900）
        System.out.println("葫芦边缘采样点梯度：");
        int[][] pts = {{230,400},{240,600},{250,800},{420,500},{430,700},{410,300},{320,950},{300,270}};
        for (int[] pt : pts) {
            int x=pt[0], y=pt[1];
            float m=0; int bestX=x;
            for (int dx=-15; dx<=15; dx++) {
                int xx=Math.min(w-1,Math.max(0,x+dx));
                if (mag[y*w+xx]>m){m=mag[y*w+xx];bestX=xx;}
            }
            System.out.printf("  y=%d 附近 x=%d 处最大梯度=%.4f%n", y, bestX, m);
        }
        System.out.println("竹叶区域采样梯度（背景 x<100 或 x>500）：");
        for (int y = 100; y <= 1000; y += 300) {
            float m = 0;
            for (int x = 0; x < 80 && x < w; x++) m = Math.max(m, mag[y*w+x]);
            System.out.printf("  y=%d 左边缘最大梯度=%.4f%n", y, m);
        }
    }
    static float mean(float[] a){float s=0;for(float v:a)s+=v;return s/a.length;}
}
