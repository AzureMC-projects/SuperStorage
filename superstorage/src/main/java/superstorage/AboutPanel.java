package superstorage;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Font;

/** The "About" tab: version, credits, third-party notice. */
public class AboutPanel extends JPanel {

    public AboutPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(30, 20, 20, 20));

        add(center(new JLabel(new ImageIcon(AppInfo.icon(96)))));
        add(Box.createVerticalStrut(12));
        add(center(styled(new JLabel(AppInfo.NAME), Font.BOLD, 28f)));
        add(center(new JLabel("Version " + AppInfo.version())));
        add(Box.createVerticalStrut(18));
        add(center(styled(new JLabel(AppInfo.CREDITS), Font.BOLD, 15f)));
        add(Box.createVerticalStrut(22));
        add(center(new JLabel("Free up disk space and scan folders for suspicious files.")));
        add(Box.createVerticalStrut(6));
        add(center(small("The scanner is a heuristic tool and does not replace Windows Security.")));
        add(Box.createVerticalStrut(6));
        add(center(small("Cleanup actions that delete files are permanent. Review selections before you run them.")));
        add(Box.createVerticalStrut(26));
        add(center(small("Third-party software: FlatLaf (Apache License 2.0), Copyright FormDev Software GmbH.")));
        add(Box.createVerticalStrut(6));
        add(center(small("Java " + System.getProperty("java.version") + "  |  "
                + System.getProperty("os.name") + " " + System.getProperty("os.version"))));
        add(Box.createVerticalStrut(6));
        add(center(small("Settings file: " + Settings.file())));
        add(Box.createVerticalGlue());
    }

    private static JLabel center(JLabel l) {
        l.setAlignmentX(CENTER_ALIGNMENT);
        return l;
    }

    private static JLabel styled(JLabel l, int style, float size) {
        l.setFont(l.getFont().deriveFont(style, size));
        return l;
    }

    private static JLabel small(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(12f));
        l.setEnabled(false);
        return l;
    }
}
