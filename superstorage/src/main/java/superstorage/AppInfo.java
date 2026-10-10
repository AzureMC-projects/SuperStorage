package superstorage;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

/** Names, version, credits and the generated app icon. */
final class AppInfo {

    static final String NAME = "SuperStorage";
    static final String CREDITS = "JacobProjects SuperStorage 2026 Do not distribute";

    private AppInfo() {}

    static String version() {
        String v = AppInfo.class.getPackage() == null ? null : AppInfo.class.getPackage().getImplementationVersion();
        return v != null ? v : "dev";
    }

    /** A simple generated icon: a blue rounded square with a stack of storage bars. */
    static Image icon(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x1565C0));
        g.fillRoundRect(0, 0, s, s, s / 4, s / 4);
        g.setColor(Color.WHITE);
        int x = Math.round(s * 0.18f);
        int w = Math.round(s * 0.64f);
        int h = Math.round(s * 0.16f);
        for (int i = 0; i < 3; i++) {
            int y = Math.round(s * (0.20f + i * 0.24f));
            g.fillRoundRect(x, y, w, h, h, h);
        }
        g.setColor(new Color(0x64B5F6));
        int d = Math.max(2, Math.round(s * 0.07f));
        for (int i = 0; i < 3; i++) {
            int y = Math.round(s * (0.20f + i * 0.24f)) + (h - d) / 2;
            g.fillOval(x + w - d * 2, y, d, d);
        }
        g.dispose();
        return img;
    }

    static List<Image> icons() {
        return List.of(icon(16), icon(32), icon(64), icon(256));
    }
}
