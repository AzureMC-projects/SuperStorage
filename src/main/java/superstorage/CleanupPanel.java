package superstorage;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/** The "Cleanup" tab: tick tasks, run them through PowerShell. */
public class CleanupPanel extends JPanel {

    record Task(String name, String description, String script, boolean defaultOn, String warning) {}

    private static final List<Task> TASKS = List.of(
            new Task("Component Store Cleanup (DISM)",
                    "dism /Online /Cleanup-Image /StartComponentCleanup - removes superseded Windows component versions.",
                    "dism /Online /Cleanup-Image /StartComponentCleanup\nexit $LASTEXITCODE",
                    true, null),

            new Task("Empty Recycle Bin",
                    "Permanently removes everything in the Recycle Bin.",
                    "Clear-RecycleBin -Force -ErrorAction SilentlyContinue\nWrite-Output 'Recycle Bin emptied.'",
                    true, null),

            new Task("Clear user temp files",
                    "Deletes files in your %TEMP% folder (files in use are skipped).",
                    """
                    Get-ChildItem -LiteralPath $env:TEMP -Force -ErrorAction SilentlyContinue |
                        Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
                    Write-Output 'User temp files cleared (files in use were skipped).'
                    """,
                    true, null),

            new Task("Clear Windows temp files",
                    "Deletes files in C:\\Windows\\Temp (files in use are skipped).",
                    """
                    Get-ChildItem -LiteralPath "$env:SystemRoot\\Temp" -Force -ErrorAction SilentlyContinue |
                        Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
                    Write-Output 'Windows temp files cleared (files in use were skipped).'
                    """,
                    true, null),

            new Task("Clear Windows Update download cache",
                    "Deletes already-downloaded update files in SoftwareDistribution\\Download.",
                    """
                    $wu = (Get-Service wuauserv -ErrorAction SilentlyContinue).Status -eq 'Running'
                    $bits = (Get-Service bits -ErrorAction SilentlyContinue).Status -eq 'Running'
                    Stop-Service wuauserv -Force -ErrorAction SilentlyContinue
                    Stop-Service bits -Force -ErrorAction SilentlyContinue
                    Get-ChildItem -LiteralPath "$env:SystemRoot\\SoftwareDistribution\\Download" -Force -ErrorAction SilentlyContinue |
                        Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
                    if ($bits) { Start-Service bits -ErrorAction SilentlyContinue }
                    if ($wu) { Start-Service wuauserv -ErrorAction SilentlyContinue }
                    Write-Output 'Windows Update download cache cleared.'
                    """,
                    false, null),

            new Task("Delete everything in Downloads",
                    "Permanently deletes all files and folders in your Downloads folder.",
                    """
                    $p = (New-Object -ComObject Shell.Application).NameSpace('shell:Downloads').Self.Path
                    if (-not $p -or -not (Test-Path -LiteralPath $p)) { Write-Output 'Downloads folder not found.'; exit 1 }
                    Write-Output "Deleting contents of $p"
                    Get-ChildItem -LiteralPath $p -Force | ForEach-Object {
                        Write-Output "  removing $($_.Name)"
                        Remove-Item -LiteralPath $_.FullName -Recurse -Force -ErrorAction SilentlyContinue
                    }
                    Write-Output 'Downloads folder emptied.'
                    """,
                    false,
                    "This PERMANENTLY deletes everything in your Downloads folder.\n"
                            + "It does NOT go to the Recycle Bin and cannot be undone.\n\nContinue?")
    );

    private final List<JCheckBox> boxes = new ArrayList<>();
    private final JButton runButton = new JButton("Clean Selected");
    private final JButton allButton = new JButton("Select All");
    private final JTextArea log = new JTextArea();
    private final JLabel status = new JLabel("Ready");
    private final JProgressBar busy = new JProgressBar();

    public CleanupPanel() {
        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 10, 6, 10));

        JPanel taskPanel = new JPanel();
        taskPanel.setLayout(new BoxLayout(taskPanel, BoxLayout.Y_AXIS));
        taskPanel.setBorder(BorderFactory.createTitledBorder("Cleanup tasks"));
        for (Task t : TASKS) {
            JCheckBox box = new JCheckBox("<html><b>" + t.name() + "</b><br><span style='font-size:90%'>"
                    + t.description().replace("<", "&lt;") + "</span></html>", t.defaultOn());
            box.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
            boxes.add(box);
            taskPanel.add(box);
        }

        runButton.setFont(runButton.getFont().deriveFont(Font.BOLD, 15f));
        runButton.addActionListener(e -> runSelected());
        allButton.addActionListener(e -> {
            boolean anyOff = boxes.stream().anyMatch(b -> !b.isSelected());
            boxes.forEach(b -> b.setSelected(anyOff));
            allButton.setText(anyOff ? "Select None" : "Select All");
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        buttons.add(runButton);
        buttons.add(allButton);

        JPanel top = new JPanel(new BorderLayout());
        top.add(taskPanel, BorderLayout.CENTER);
        top.add(buttons, BorderLayout.SOUTH);

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        busy.setIndeterminate(true);
        busy.setVisible(false);
        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.add(status, BorderLayout.WEST);
        bottom.add(busy, BorderLayout.CENTER);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(log), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        if (!Sys.isWindows()) {
            runButton.setEnabled(false);
            status.setText("Windows only");
            append("Cleanup only works on Windows.");
        }
    }

    private void runSelected() {
        List<Task> selected = new ArrayList<>();
        for (int i = 0; i < TASKS.size(); i++) {
            if (boxes.get(i).isSelected()) selected.add(TASKS.get(i));
        }
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Tick at least one task first.");
            return;
        }
        if (Settings.getBool(Settings.CONFIRM_DELETE)) {
            for (Task t : selected) {
                if (t.warning() != null) {
                    int ok = JOptionPane.showConfirmDialog(this, t.warning(), "Confirm: " + t.name(),
                            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (ok != JOptionPane.YES_OPTION) return;
                }
            }
        }

        setRunning(true);
        log.setText("");
        long freeBefore = freeSpace();

        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                int n = 0;
                for (Task t : selected) {
                    n++;
                    publish("\n=== [" + n + "/" + selected.size() + "] " + t.name() + " ===");
                    int code = runPowerShell(t.script());
                    publish(code == 0 ? "-> OK" : "-> finished with exit code " + code
                            + (code == 740 ? " (needs administrator rights)" : ""));
                }
                return null;
            }

            private int runPowerShell(String script) throws Exception {
                Process p = new ProcessBuilder(Sys.psCommand(script)).redirectErrorStream(true).start();
                Charset cs = Charset.forName(System.getProperty("native.encoding", Charset.defaultCharset().name()));
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), cs))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (!line.isBlank()) publish(line);
                    }
                }
                return p.waitFor();
            }

            @Override
            protected void process(List<String> lines) {
                lines.forEach(CleanupPanel.this::append);
            }

            @Override
            protected void done() {
                setRunning(false);
                try {
                    get();
                    long freed = freeSpace() - freeBefore;
                    String msg = "Done. Free space change: " + (freed >= 0 ? "+" : "-") + Sys.format(Math.abs(freed));
                    status.setText(msg);
                    append("\n" + msg);
                } catch (Exception ex) {
                    status.setText("Error");
                    append("Error: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void setRunning(boolean running) {
        runButton.setEnabled(!running);
        allButton.setEnabled(!running);
        boxes.forEach(b -> b.setEnabled(!running));
        busy.setVisible(running);
        if (running) status.setText("Running...");
    }

    private static long freeSpace() {
        return new File(System.getenv().getOrDefault("SystemDrive", "C:") + "\\").getFreeSpace();
    }

    private void append(String text) {
        log.append(text + System.lineSeparator());
        log.setCaretPosition(log.getDocument().getLength());
    }
}
