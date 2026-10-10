package superstorage;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

/** The "Settings" tab. Every change is saved immediately. */
public class SettingsPanel extends JPanel {

    private final List<Runnable> syncers = new ArrayList<>();
    private boolean syncing = false;

    public SettingsPanel() {
        setLayout(new BorderLayout());

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        // ---------------- Appearance
        JPanel look = section("Appearance");
        JComboBox<String> theme = new JComboBox<>(new String[]{"Light", "Dark", "System"});
        theme.setSelectedItem(Settings.get(Settings.THEME));
        theme.addActionListener(e -> Settings.set(Settings.THEME, String.valueOf(theme.getSelectedItem())));
        syncers.add(() -> theme.setSelectedItem(Settings.get(Settings.THEME)));
        JPanel themeWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        themeWrap.add(theme);
        addRow(look, 0, new JLabel("Theme"), themeWrap);
        content.add(look);

        // ---------------- Antivirus
        JPanel av = section("Antivirus");
        JCheckBox enable = check(Settings.AV_ENABLED, "Enable antivirus");
        addRow(av, 0, enable, null);
        addNote(av, 1, "When off, the Antivirus tab is disabled and nothing is scanned.");
        addRow(av, 2, new JLabel("Flag archives that expand by more than (x : 1)"),
                spinner(Settings.AV_MAX_RATIO, 2, 100_000, 10));
        addRow(av, 3, new JLabel("...but only if they expand to at least (MB)"),
                spinner(Settings.AV_MIN_MB, 1, 10_240, 10));
        addRow(av, 4, new JLabel("Max nested archive levels before flagging"),
                spinner(Settings.AV_MAX_DEPTH, 1, 20, 1));
        addRow(av, 5, new JLabel("Deep-scan unpack limit per archive (MB)"),
                spinner(Settings.AV_BUDGET_MB, 16, 8_192, 16));

        JTextField folder = new JTextField(Settings.get(Settings.SCAN_FOLDER), 28);
        folder.addActionListener(e -> Settings.set(Settings.SCAN_FOLDER, folder.getText().trim()));
        folder.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                Settings.set(Settings.SCAN_FOLDER, folder.getText().trim());
            }
        });
        JButton browse = new JButton("Browse...");
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(folder.getText());
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                folder.setText(fc.getSelectedFile().getAbsolutePath());
                Settings.set(Settings.SCAN_FOLDER, folder.getText());
            }
        });
        JPanel folderBox = new JPanel(new BorderLayout(6, 0));
        folderBox.add(folder, BorderLayout.CENTER);
        folderBox.add(browse, BorderLayout.EAST);
        addRow(av, 6, new JLabel("Default scan folder"), folderBox);
        syncers.add(() -> {
            if (!folder.hasFocus()) folder.setText(Settings.get(Settings.SCAN_FOLDER));
        });
        content.add(av);

        // ---------------- General
        JPanel general = section("General");
        addRow(general, 0, check(Settings.ASK_ADMIN, "Offer to restart as administrator on startup (UAC prompt)"), null);
        addRow(general, 1, check(Settings.CONFIRM_DELETE, "Confirm before permanently deleting files"), null);
        content.add(general);

        // ---------------- Reset
        JPanel reset = section("Reset");
        JButton resetButton = new JButton("Reset all settings to defaults");
        resetButton.addActionListener(e -> {
            int ok = JOptionPane.showConfirmDialog(this, "Reset every setting to its default?",
                    "Reset settings", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) Settings.resetAll();
        });
        addRow(reset, 0, resetButton, null);
        addNote(reset, 1, "Settings file: " + Settings.file());
        content.add(reset);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        Settings.addListener(() -> SwingUtilities.invokeLater(this::syncAll));
    }

    private void syncAll() {
        syncing = true;
        try {
            syncers.forEach(Runnable::run);
        } finally {
            syncing = false;
        }
    }

    // -------------------------------------------------------------- helpers

    private JPanel section(String title) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    private void addRow(JPanel p, int row, JComponent left, JComponent right) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(3, 6, 3, 6);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        if (right == null) {
            c.gridwidth = 2;
            p.add(left, c);
        } else {
            p.add(left, c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            p.add(right, c);
        }
    }

    private void addNote(JPanel p, int row, String text) {
        JLabel l = new JLabel("<html><span style='font-size:90%'>" + text.replace("<", "&lt;") + "</span></html>");
        l.setEnabled(false);
        addRow(p, row, l, null);
    }

    private JCheckBox check(String key, String label) {
        JCheckBox box = new JCheckBox(label, Settings.getBool(key));
        box.addActionListener(e -> Settings.set(key, box.isSelected()));
        syncers.add(() -> box.setSelected(Settings.getBool(key)));
        return box;
    }

    private JComponent spinner(String key, int min, int max, int step) {
        JSpinner sp = new JSpinner(new SpinnerNumberModel(Settings.getInt(key), min, max, step));
        sp.setPreferredSize(new Dimension(90, sp.getPreferredSize().height));
        sp.addChangeListener(e -> {
            if (!syncing) Settings.set(key, ((Number) sp.getValue()).intValue());
        });
        syncers.add(() -> sp.setValue(Settings.getInt(key)));
        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wrap.add(sp);
        return wrap;
    }
}
