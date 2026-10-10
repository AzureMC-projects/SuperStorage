package superstorage;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** The "Antivirus" tab: pick what to look for, scan a folder, review/delete findings. */
public class AntivirusPanel extends JPanel {

    private record Toggle(String key, String label, String tip) {}

    private static final List<Toggle> TOGGLES = List.of(
            new Toggle(Settings.AV_ZIPBOMB, "Zip & gzip bombs",
                    "Small archives that claim to expand to enormous sizes (zip: header check, gzip: inflated up to a limit)."),
            new Toggle(Settings.AV_NESTED, "Nested / forged-size archive bombs (slower)",
                    "Unpacks archives in memory up to a safety limit to catch zips-inside-zips and lying size headers."),
            new Toggle(Settings.AV_OVERLAP, "Overlapping-entry zip bombs",
                    "Zip files whose entries share the same compressed data."),
            new Toggle(Settings.AV_ZIPSLIP, "Zip-slip path tricks",
                    "Archive entries with ../ or absolute paths that try to escape the extraction folder."),
            new Toggle(Settings.AV_EXE_IN_ARCHIVE, "Programs hidden inside zip files",
                    "Zip files that contain .exe, .scr, .bat, .vbs, .ps1 and similar."),
            new Toggle(Settings.AV_DOUBLE_EXT, "Double extensions & Unicode name tricks",
                    "Names like invoice.pdf.exe or names using a right-to-left override character."),
            new Toggle(Settings.AV_DISGUISED, "Programs disguised as documents/media",
                    "Files named .pdf/.jpg/.docx/... that are really Windows programs."),
            new Toggle(Settings.AV_RISKY, "Risky file types",
                    "Rarely-legitimate types such as .scr, .pif, .vbs, .hta, .wsf, .cpl, .reg."),
            new Toggle(Settings.AV_AUTORUN, "autorun.inf files",
                    "Files that make drives launch programs automatically."),
            new Toggle(Settings.AV_EICAR, "EICAR test file",
                    "The standard harmless antivirus test file - handy to check the scanner works.")
    );

    private final List<JCheckBox> toggleBoxes = new ArrayList<>();
    private final JCheckBox subfoldersBox = new JCheckBox("Include subfolders");
    private final JLabel banner = new JLabel(
            "  Antivirus is turned off. Enable it in Settings > Antivirus.", JLabel.LEFT);
    private final JTextField folderField = new JTextField();
    private final JButton browseButton = new JButton("Browse...");
    private final JButton allOnButton = new JButton("All on");
    private final JButton allOffButton = new JButton("All off");
    private final JButton scanButton = new JButton("Scan");
    private final JButton stopButton = new JButton("Stop");
    private final JButton explorerButton = new JButton("Show in Explorer");
    private final JButton deleteButton = new JButton("Delete selected");
    private final JLabel status = new JLabel("Ready");
    private final JProgressBar busy = new JProgressBar();

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Severity", "Detection", "File", "Details"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(model);
    private final List<FileScanner.Finding> findings = new ArrayList<>();

    private volatile boolean scanning = false;
    private AtomicBoolean cancel = new AtomicBoolean(false);

    public AntivirusPanel() {
        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 10, 6, 10));

        // --- banner shown when antivirus is disabled in Settings
        banner.setOpaque(true);
        banner.setBackground(new Color(0xFFF3CD));
        banner.setForeground(new Color(0x664D03));
        banner.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        banner.setAlignmentX(LEFT_ALIGNMENT);

        // --- detection toggles
        JPanel togglePanel = new JPanel(new GridLayout(0, 2, 12, 0));
        togglePanel.setBorder(BorderFactory.createTitledBorder("What to look for"));
        for (Toggle t : TOGGLES) {
            JCheckBox box = new JCheckBox(t.label(), Settings.getBool(t.key()));
            box.setToolTipText(t.tip());
            box.addActionListener(e -> Settings.set(t.key(), box.isSelected()));
            toggleBoxes.add(box);
            togglePanel.add(box);
        }
        togglePanel.setAlignmentX(LEFT_ALIGNMENT);
        togglePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, togglePanel.getPreferredSize().height));

        allOnButton.addActionListener(e -> setAllToggles(true));
        allOffButton.addActionListener(e -> setAllToggles(false));
        JPanel toggleButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        toggleButtons.add(allOnButton);
        toggleButtons.add(allOffButton);
        toggleButtons.add(subfoldersBox);
        toggleButtons.setAlignmentX(LEFT_ALIGNMENT);
        subfoldersBox.setSelected(Settings.getBool(Settings.SCAN_SUBFOLDERS));
        subfoldersBox.addActionListener(e -> Settings.set(Settings.SCAN_SUBFOLDERS, subfoldersBox.isSelected()));

        // --- folder row
        folderField.setText(Settings.get(Settings.SCAN_FOLDER));
        browseButton.addActionListener(e -> browse());
        JPanel folderRow = new JPanel(new BorderLayout(6, 0));
        folderRow.add(new JLabel("Folder to scan:"), BorderLayout.WEST);
        folderRow.add(folderField, BorderLayout.CENTER);
        folderRow.add(browseButton, BorderLayout.EAST);
        folderRow.setAlignmentX(LEFT_ALIGNMENT);
        folderRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, folderRow.getPreferredSize().height + 4));

        // --- scan buttons
        scanButton.setFont(scanButton.getFont().deriveFont(Font.BOLD, 14f));
        scanButton.addActionListener(e -> startScan());
        stopButton.addActionListener(e -> cancel.set(true));
        JPanel scanRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        scanRow.add(scanButton);
        scanRow.add(stopButton);
        scanRow.add(new JLabel("(heuristic checks only - keep Windows Security on too)"));
        scanRow.setAlignmentX(LEFT_ALIGNMENT);

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.add(banner);
        north.add(togglePanel);
        north.add(toggleButtons);
        north.add(folderRow);
        north.add(scanRow);

        // --- results table
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(70);
        table.getColumnModel().getColumn(1).setPreferredWidth(170);
        table.getColumnModel().getColumn(2).setPreferredWidth(300);
        table.getColumnModel().getColumn(3).setPreferredWidth(380);
        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc,
                                                           int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                comp.setFont(comp.getFont().deriveFont(Font.BOLD));
                if (sel) {
                    comp.setForeground(t.getSelectionForeground());
                } else {
                    String s = String.valueOf(v);
                    comp.setForeground(s.equals("High") ? new Color(0xE53935)
                            : s.equals("Medium") ? new Color(0xFB8C00) : Color.GRAY);
                }
                return comp;
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> updateEnabled());

        // --- bottom
        explorerButton.addActionListener(e -> showInExplorer());
        deleteButton.addActionListener(e -> deleteSelected());
        busy.setIndeterminate(true);
        busy.setVisible(false);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        actions.add(explorerButton);
        actions.add(deleteButton);
        JPanel statusRow = new JPanel(new BorderLayout(8, 0));
        statusRow.add(status, BorderLayout.WEST);
        statusRow.add(busy, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(0, 2));
        bottom.add(actions, BorderLayout.NORTH);
        bottom.add(statusRow, BorderLayout.SOUTH);

        add(north, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        Settings.addListener(() -> SwingUtilities.invokeLater(this::refresh));
        refresh();
    }

    // ------------------------------------------------------------- state sync

    /** Re-reads settings into the controls and enables/disables things. */
    private void refresh() {
        for (int i = 0; i < TOGGLES.size(); i++) {
            toggleBoxes.get(i).setSelected(Settings.getBool(TOGGLES.get(i).key()));
        }
        subfoldersBox.setSelected(Settings.getBool(Settings.SCAN_SUBFOLDERS));
        String saved = Settings.get(Settings.SCAN_FOLDER);
        if (!folderField.hasFocus() && !folderField.getText().equals(saved)) folderField.setText(saved);
        if (!Settings.getBool(Settings.AV_ENABLED)) cancel.set(true);
        updateEnabled();
    }

    private void updateEnabled() {
        boolean av = Settings.getBool(Settings.AV_ENABLED);
        boolean idle = av && !scanning;
        banner.setVisible(!av);
        toggleBoxes.forEach(b -> b.setEnabled(idle));
        subfoldersBox.setEnabled(idle);
        allOnButton.setEnabled(idle);
        allOffButton.setEnabled(idle);
        folderField.setEnabled(idle);
        browseButton.setEnabled(idle);
        scanButton.setEnabled(idle);
        stopButton.setEnabled(av && scanning);
        boolean hasSelection = table.getSelectedRowCount() > 0;
        explorerButton.setEnabled(av && !scanning && table.getSelectedRowCount() == 1);
        deleteButton.setEnabled(av && !scanning && hasSelection);
        busy.setVisible(scanning);
    }

    private void setAllToggles(boolean on) {
        for (Toggle t : TOGGLES) Settings.set(t.key(), on);
    }

    private void browse() {
        JFileChooser fc = new JFileChooser(folderField.getText());
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Settings.set(Settings.SCAN_FOLDER, fc.getSelectedFile().getAbsolutePath());
            folderField.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    // ---------------------------------------------------------------- scanning

    private void startScan() {
        if (!Settings.getBool(Settings.AV_ENABLED) || scanning) return;

        Path root;
        try {
            root = Path.of(folderField.getText().trim());
        } catch (InvalidPathException ex) {
            JOptionPane.showMessageDialog(this, "That folder path is not valid.");
            return;
        }
        if (!Files.isDirectory(root)) {
            JOptionPane.showMessageDialog(this, "Folder not found:\n" + root);
            return;
        }
        Settings.set(Settings.SCAN_FOLDER, root.toString());

        model.setRowCount(0);
        findings.clear();
        cancel = new AtomicBoolean(false);
        AtomicBoolean myCancel = cancel;
        boolean subfolders = Settings.getBool(Settings.SCAN_SUBFOLDERS);
        FileScanner scanner = new FileScanner();
        final Path scanRoot = root;

        scanning = true;
        status.setText("Scanning...");
        updateEnabled();

        new SwingWorker<Long, FileScanner.Finding>() {
            @Override
            protected Long doInBackground() throws Exception {
                return scanner.scan(scanRoot, subfolders, f -> publish(f),
                        path -> SwingUtilities.invokeLater(() -> status.setText("Scanning: " + path)), myCancel);
            }

            @Override
            protected void process(List<FileScanner.Finding> chunk) {
                for (FileScanner.Finding f : chunk) {
                    findings.add(f);
                    model.addRow(new Object[]{f.severity(), f.type(), f.path().toString(), f.detail()});
                }
            }

            @Override
            protected void done() {
                scanning = false;
                try {
                    long files = get();
                    String how = myCancel.get() ? "Stopped" : "Scan finished";
                    status.setText(how + ": " + files + " files checked, " + findings.size() + " finding(s).");
                } catch (Exception ex) {
                    status.setText("Scan failed: " + ex.getMessage());
                }
                updateEnabled();
            }
        }.execute();
    }

    // ----------------------------------------------------------------- results

    private void showInExplorer() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return;
        FileScanner.Finding f = findings.get(table.convertRowIndexToModel(viewRow));
        try {
            new ProcessBuilder("explorer.exe", "/select,", f.path().toString()).start();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not open Explorer: " + ex.getMessage());
        }
    }

    private void deleteSelected() {
        int[] viewRows = table.getSelectedRows();
        if (viewRows.length == 0) return;

        if (Settings.getBool(Settings.CONFIRM_DELETE)) {
            int ok = JOptionPane.showConfirmDialog(this,
                    "Permanently delete " + viewRows.length + " selected file(s)?\n"
                            + "They do NOT go to the Recycle Bin and this cannot be undone.",
                    "Confirm delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ok != JOptionPane.YES_OPTION) return;
        }

        List<Integer> modelRows = new ArrayList<>();
        for (int r : viewRows) modelRows.add(table.convertRowIndexToModel(r));
        modelRows.sort(Collections.reverseOrder());

        int deleted = 0;
        int failed = 0;
        for (int idx : modelRows) {
            FileScanner.Finding f = findings.get(idx);
            try {
                Files.deleteIfExists(f.path());
                model.removeRow(idx);
                findings.remove(idx);
                deleted++;
            } catch (IOException ex) {
                failed++;
            }
        }
        status.setText("Deleted " + deleted + " file(s)" + (failed > 0 ? ", " + failed + " could not be deleted." : "."));
        updateEnabled();
    }
}
