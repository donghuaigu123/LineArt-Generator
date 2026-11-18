import com.lineart.gen.core.LineArtEngine;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

public class TestHarness {

    static BufferedImage load(String path, int maxSide) throws Exception {
        BufferedImage img = ImageIO.read(new File(path));
        System.out.println("原图尺寸: " + img.getWidth() + "x" + img.getHeight());
        int w = img.getWidth(), h = img.getHeight();
        if (Math.max(w, h) > maxSide) {
            float s = maxSide / (float) Math.max(w, h);
            int nw = Math.round(w * s), nh = Math.round(h * s);
            BufferedImage r = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = r.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, nw, nh, null);
            g.dispose();
            img = r;
        }
        return img;
    }

    static int[] toPixels(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        return img.getRGB(0, 0, w, h, null, 0, w);
    }

    static BufferedImage fromPixels(int[] px, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, w, h, px, 0, w);
        return img;
    }

    public static void main(String[] args) throws Exception {
        String in = args.length > 0 ? args[0] : "sample1.jpg";
        BufferedImage src = load(in, 1400);
        int w = src.getWidth(), h = src.getHeight();
        int[] pixels = toPixels(src);

        // 测试 5 种模式，各自较优默认参数
        LineArtEngine.Params[][] presets = {
            { pm(LineArtEngine.MODE_FINE, 72, 1, 55, 60) },
            { pm(LineArtEngine.MODE_STANDARD, 55, 1, 50, 50) },
            { pm(LineArtEngine.MODE_BOLD, 40, 1, 38, 60) },
            { pm(LineArtEngine.MODE_SKETCH, 65, 0, 55, 45) },
            { pm(LineArtEngine.MODE_STENCIL, 50, 0, 35, 50) },
        };
        if (args.length > 1 && "dark".equals(args[1])) {
            for (LineArtEngine.Params[] pa : presets) pa[0].darkBgSuppression = true;
        }
        String[] names = { "fine", "standard", "bold", "sketch", "stencil" };

        BufferedImage grid = new BufferedImage(w * 3, h * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = grid.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, w * 3, h * 2);
        g.drawImage(src, 0, 0, null);
        for (int i = 0; i < 5; i++) {
            long t0 = System.currentTimeMillis();
            int[] out = LineArtEngine.process(pixels, w, h, presets[i][0]);
            long dt = System.currentTimeMillis() - t0;
            System.out.printf("%s: %dx%d, %d ms%n", names[i], w, h, dt);
            ImageIO.write(fromPixels(out, w, h), "png", new File("out_" + names[i] + ".png"));
            int gx = ((i + 1) % 3) * w, gy = ((i + 1) / 3) * h;
            g.drawImage(fromPixels(out, w, h), gx, gy, null);
        }
        g.dispose();
        ImageIO.write(grid, "png", new File("grid.png"));
        System.out.println("完成: grid.png, out_*.png");
    }

    static LineArtEngine.Params pm(int mode, int detail, int thickness, int ink, int clean) {
        LineArtEngine.Params p = new LineArtEngine.Params();
        p.mode = mode; p.detail = detail; p.thickness = thickness; p.ink = ink; p.clean = clean;
        return p;
    }
}
