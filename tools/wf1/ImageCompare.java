import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Pixel comparison of two same-sized screenshots, plus a side-by-side montage (left reference, right candidate, a third
 * panel marking pixels that differ). Run as: java tools/wf1/ImageCompare.java reference.png candidate.png out.png
 * Prints one line: mean absolute channel difference, share of differing pixels (any channel > 24) and their bounding box.
 */
public final class ImageCompare {

    public static void main(String[] args) throws Exception {
        BufferedImage a = ImageIO.read(new File(args[0])), b = ImageIO.read(new File(args[1]));
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight())
            throw new IllegalArgumentException("size " + a.getWidth() + "x" + a.getHeight() + " vs " + b.getWidth() + "x" + b.getHeight());
        int w = a.getWidth(), h = a.getHeight();
        BufferedImage mark = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        long sum = 0, differing = 0;
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int p = a.getRGB(x, y), q = b.getRGB(x, y);
            int dr = Math.abs(((p >> 16) & 255) - ((q >> 16) & 255)), dg = Math.abs(((p >> 8) & 255) - ((q >> 8) & 255)),
                db = Math.abs((p & 255) - (q & 255));
            sum += dr + dg + db;
            boolean different = Math.max(dr, Math.max(dg, db)) > 24;
            if (different) {
                differing++;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                mark.setRGB(x, y, 0xFF2020);
            } else mark.setRGB(x, y, ((q >> 1) & 0x7F7F7F));
        }
        BufferedImage out = new BufferedImage(w * 3 / 2, h / 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        int pw = w / 2, ph = h / 2;
        g.drawImage(a, 0, 0, pw, ph, null);
        g.drawImage(b, pw, 0, pw, ph, null);
        g.drawImage(mark, pw * 2, 0, pw, ph, null);
        g.setColor(Color.WHITE);
        g.drawString("reference", 6, 14);
        g.drawString("candidate", pw + 6, 14);
        g.drawString("difference (> 24)", pw * 2 + 6, 14);
        g.dispose();
        ImageIO.write(out, "png", new File(args[2]));
        System.out.printf("meanAbsDiff=%.3f differingPixels=%.4f%% bbox=%s%n", sum / (3.0 * w * h), 100.0 * differing / (w * h),
            maxX < 0 ? "none" : minX + "," + minY + "-" + maxX + "," + maxY);
    }
}
