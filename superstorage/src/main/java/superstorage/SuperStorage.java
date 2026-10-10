package superstorage;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.BorderFactory;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Window;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

/** SuperStorage - cleanup tools, a heuristic antivirus scanner, and settings. */
public class SuperStorage {

    private static String appliedTheme = null;

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> reportCrash(e));
        SwingUtilities.invokeLater(() -> {
            applyTheme();
            start();
        });
    }

    /** Applies the theme chosen in Settings (Light / Dark / System) if it changed. */
    static void applyTheme() {
        String theme = Settings.get(Settings.THEME);
        if (theme.equals(appliedTheme)) return;
        try {
            UIManager.put("Button.arc", 10);
            UIManager.put("Component.arc", 10);
            UIManager.put("TextComponent.arc", 10);
            UIManager.put("ProgressBar.arc", 10);
            UIManager.put("TabbedPane.showTabSeparators", true);
            if (theme.equals("Dark")) {
                FlatDarkLaf.setup();
            } else if (theme.equals("System")) {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } else {
                FlatLightLaf.setup();
            }
        } catch (Exception ignored) {
        }
        boolean first = appliedTheme == null;
        appliedTheme = theme;
        if (!first) {
            for (Window w : Window.getWindows()) SwingUtilities.updateComponentTreeUI(w);
        }
    }

    private static void start() {
        Settings.addListener(() -> SwingUtilities.invokeLater(SuperStorage::applyTheme));

        if (Sys.isWindows() && Settings.getBool(Settings.ASK_ADMIN) && !Sys.isAdmin()) {
            int choice = JOptionPane.showConfirmDialog(null,
                    "SuperStorage needs administrator rights to clean system files.\n"
                            + "Restart as administrator now?",
                    AppInfo.NAME, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                if (Sys.relaunchElevated()) {
                    System.exit(0);
                }
                JOptionPane.showMessageDialog(null,
                        "Could not get administrator rights (UAC was cancelled or failed).\n"
                                + "Some cleanups will not work.",
                        AppInfo.NAME, JOptionPane.WARNING_MESSAGE);
            }
        }

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Cleanup", new CleanupPanel());
        tabs.addTab("Antivirus", new AntivirusPanel());
        tabs.addTab("Settings", new SettingsPanel());
        tabs.addTab("About", new AboutPanel());

        JFrame frame = new JFrame(AppInfo.NAME);
        frame.setIconImages(AppInfo.icons());
        frame.setLayout(new BorderLayout());
        frame.add(header(), BorderLayout.NORTH);
        frame.add(tabs, BorderLayout.CENTER);
        frame.add(footer(), BorderLayout.SOUTH);
        frame.setMinimumSize(new java.awt.Dimension(820, 700));
        frame.setSize(900, 800);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    private static JPanel header() {
        JPanel p = new JPanel(new BorderLayout(12, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, new Color(0x0D47A1), getWidth(), 0, new Color(0x1E88E5)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        p.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel logo = new JLabel(new ImageIcon(AppInfo.icon(44)));
        JLabel title = new JLabel(AppInfo.NAME);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setForeground(Color.WHITE);
        JLabel sub = new JLabel("Clean. Scan. Reclaim space.");
        sub.setForeground(new Color(0xBBDEFB));

        JPanel text = new JPanel(new BorderLayout());
        text.setOpaque(false);
        text.add(title, BorderLayout.NORTH);
        text.add(sub, BorderLayout.SOUTH);

        p.add(logo, BorderLayout.WEST);
        p.add(text, BorderLayout.CENTER);
        return p;
    }

    private static JPanel footer() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Component.borderColor") != null
                        ? UIManager.getColor("Component.borderColor") : Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(5, 12, 5, 12)));
        JLabel credits = new JLabel(AppInfo.CREDITS);
        credits.setFont(credits.getFont().deriveFont(12f));
        JLabel version = new JLabel("v" + AppInfo.version());
        version.setFont(version.getFont().deriveFont(12f));
        p.add(credits, BorderLayout.WEST);
        p.add(version, BorderLayout.EAST);
        return p;
    }

    private static void reportCrash(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        try {
            java.nio.file.Path log = Settings.file().getParent().resolve("error.log");
            Files.createDirectories(log.getParent());
            Files.writeString(log, LocalDateTime.now() + "\n" + sw + "\n", StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                "Something went wrong:\n" + e + "\n\nDetails were saved to error.log in:\n"
                        + Settings.file().getParent(),
                AppInfo.NAME, JOptionPane.ERROR_MESSAGE));
    }
}
