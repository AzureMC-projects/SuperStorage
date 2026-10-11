package com.azuremc.superstorage;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StatFs;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * SuperStorage Android. Cleanup is restricted to a folder the user explicitly
 * selects through Android's Storage Access Framework. Every deletion is reviewed
 * and confirmed; Android owns the permission grant.
 */
public class MainActivity extends Activity {
    private static final int REQUEST_FOLDER = 41;
    private static final int MAX_RESULTS = 250;
    private final int ink = Color.rgb(25, 34, 49);
    private final int muted = Color.rgb(103, 115, 132);
    private final int accent = Color.rgb(44, 91, 225);
    private final int canvas = Color.rgb(246, 248, 252);
    private LinearLayout content;
    private String current = "Home";
    private Uri folderUri;
    private final List<FileEntry> files = new ArrayList<>();
    private final List<CheckBox> fileChecks = new ArrayList<>();
    private SharedPreferences prefs;
    private boolean scanComplete = false;

    private static class FileEntry {
        final Uri uri;
        final String name;
        final long size;
        final boolean directory;
        FileEntry(Uri uri, String name, long size, boolean directory) {
            this.uri = uri; this.name = name; this.size = size; this.directory = directory;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        String saved = prefs.getString("folder_uri", null);
        if (saved != null) {
            folderUri = Uri.parse(saved);
            try { getContentResolver().takePersistableUriPermission(folderUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); }
            catch (Exception ignored) { folderUri = null; }
        }
        getWindow().setStatusBarColor(canvas);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        showPage("Home");
    }

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return t;
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private View space(int h) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h))); return v; }

    private void showPage(String page) {
        current = page;
        LinearLayout root = column(); root.setBackgroundColor(canvas); root.setPadding(dp(20), dp(20), dp(20), dp(6));
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text("◈", 29, accent, true), new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout brand = column(); brand.addView(text("SuperStorage", 20, ink, true)); brand.addView(text("Space, made simple", 12, muted, false));
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(header); root.addView(space(18));
        ScrollView scroll = new ScrollView(this); content = column(); content.setPadding(0, 0, 0, dp(18)); scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); render(page);
        LinearLayout nav = new LinearLayout(this); nav.setGravity(Gravity.CENTER); nav.setBackgroundColor(Color.WHITE);
        for (String item : new String[]{"Home", "Storage", "Security", "Settings"}) {
            TextView tab = text((current.equals(item) ? "● " : "") + item, 11, current.equals(item) ? accent : muted, current.equals(item));
            tab.setGravity(Gravity.CENTER); tab.setPadding(dp(3), dp(14), dp(3), dp(14)); tab.setOnClickListener(v -> showPage(item));
            nav.addView(tab, new LinearLayout.LayoutParams(0, -2, 1));
        }
        LinearLayout shell = column(); shell.addView(root, new LinearLayout.LayoutParams(-1, 0, 1)); shell.addView(nav); setContentView(shell);
    }

    private void heading(String title, String subtitle) {
        content.addView(text(title, 27, ink, true)); TextView sub = text(subtitle, 14, muted, false);
        sub.setPadding(0, dp(6), 0, dp(16)); content.addView(sub);
    }
    private void card(String title, String description, String action, Runnable run) {
        LinearLayout box = column(); box.setBackgroundColor(Color.WHITE); box.setPadding(dp(16), dp(15), dp(16), dp(15));
        box.addView(text(title, 17, ink, true)); TextView d = text(description, 13, muted, false);
        d.setPadding(0, dp(7), 0, dp(12)); box.addView(d);
        Button b = new Button(this); b.setText(action); b.setAllCaps(false); b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent)); b.setOnClickListener(v -> run.run());
        box.addView(b, new LinearLayout.LayoutParams(-1, dp(46))); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12); content.addView(box, lp);
    }
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
    private String storageSummary() {
        try {
            StatFs fs = new StatFs(android.os.Environment.getDataDirectory().getAbsolutePath());
            long total = fs.getTotalBytes(), free = fs.getAvailableBytes();
            return formatSize(total - free) + " used of " + formatSize(total) + " • " + formatSize(free) + " available";
        } catch (Exception e) { return "Storage details are unavailable on this device."; }
    }

    private void render(String page) {
        if ("Home".equals(page)) {
            heading("Your phone,\nunder control.", "Useful storage tools, transparent security, and settings.");
            card("Storage overview", storageSummary(), "Review storage", () -> showPage("Storage"));
            card("Folder cleanup", folderUri == null ? "Choose a folder to review its files and sizes." : "Folder selected. Scan it, then choose exactly which files to remove.", "Open cleanup", () -> showPage("Storage"));
            card("Security centre", "Check Android's built-in protection and review security settings.", "Open security", () -> showPage("Security"));
        } else if ("Storage".equals(page)) {
            heading("Clear up storage", "Scan a folder you choose. Nothing is removed without your confirmation.");
            content.addView(text("Device storage", 16, ink, true)); content.addView(text(storageSummary(), 14, muted, false)); content.addView(space(10));
            card("1. Choose a folder", folderUri == null ? "Android will ask you to select a folder. Grant access only to a location you trust." : "Selected: " + DocumentFile.fromTreeUri(this, folderUri).getName(), "Choose folder", this::chooseFolder);
            card("2. Scan files", scanComplete ? files.size() + " files listed (up to " + MAX_RESULTS + " entries)." : "Review file names and sizes before selecting anything.", "Scan selected folder", this::scanFolder);
            if (scanComplete) renderFileResults();
            card("System storage tools", "For app caches and system-managed space, use Android Settings. Apps cannot safely clear other apps' private data.", "Open Android storage", this::openStorageSettings);
        } else if ("Security".equals(page)) {
            heading("Security centre", "Real system controls, no fake scan results.");
            card("Google Play Protect", "Use Google's built-in app scanning. This app does not claim to have a malware signature engine.", "Open Play Protect", this::openPlayProtect);
            card("Android security", "Review your device's security controls and update status.", "Open security settings", this::openSecuritySettings);
            card("Scan status", "No antivirus scan has been run by SuperStorage. It does not inspect other apps' private data.", "Why this matters", () -> new AlertDialog.Builder(this).setTitle("Honest security status").setMessage("Android sandboxes apps. A real antivirus product needs a maintained detection engine and carefully scoped access. SuperStorage currently delegates app protection to Google Play Protect.").setPositiveButton("OK", null).show());
        } else {
            heading("Settings", "Preferences are saved on this device.");
            card("Theme", "Choose the interface appearance.", "Light theme (default)", () -> new AlertDialog.Builder(this).setTitle("Appearance").setItems(new String[]{"Light", "System default"}, (d, which) -> { prefs.edit().putString("theme", which == 0 ? "light" : "system").apply(); Toast.makeText(this, "Preference saved. Theme switching will apply in a future update.", Toast.LENGTH_SHORT).show(); }).show());
            card("Cleanup safety", "Files are never deleted automatically. Each deletion requires a review and Android confirmation.", "Review-before-delete: enabled", () -> new AlertDialog.Builder(this).setTitle("Cleanup safety").setMessage("Automatic deletion is disabled. You choose the files, review the selection, and confirm deletion in Android's system dialog.").setPositiveButton("OK", null).show());
            card("Selected folder", folderUri == null ? "No folder access saved." : "Folder permission is saved for future sessions.", folderUri == null ? "Choose folder in Storage" : "Forget folder", () -> {
                if (folderUri == null) showPage("Storage");
                else new AlertDialog.Builder(this).setTitle("Forget selected folder?").setMessage("SuperStorage will no longer remember this folder. Your files will not be changed.").setNegativeButton("Cancel", null).setPositiveButton("Forget", (d, w) -> { try { getContentResolver().releasePersistableUriPermission(folderUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); } catch (Exception ignored) {} folderUri = null; prefs.edit().remove("folder_uri").apply(); files.clear(); scanComplete = false; showPage("Settings"); }).show();
            });
            card("App permissions", "Manage permissions using Android's own app settings.", "Open app settings", () -> { Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS); i.setData(Uri.parse("package:" + getPackageName())); startActivity(i); });
            card("About", "SuperStorage for Android • 1.1.0", "Open project", () -> { Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AzureMC-projects/SuperStorage")); startActivity(i); });
        }
    }

    private void chooseFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_FOLDER);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FOLDER && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData(); int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try { getContentResolver().takePersistableUriPermission(uri, flags); } catch (Exception ignored) {}
            folderUri = uri; prefs.edit().putString("folder_uri", uri.toString()).apply(); files.clear(); scanComplete = false;
            Toast.makeText(this, "Folder access saved.", Toast.LENGTH_SHORT).show(); showPage("Storage");
        }
    }
    private void scanFolder() {
        if (folderUri == null) { chooseFolder(); return; }
        files.clear(); scanComplete = false;
        try {
            DocumentFile root = DocumentFile.fromTreeUri(this, folderUri);
            if (root == null || !root.canRead()) throw new IllegalStateException("Folder is not readable.");
            walk(root, 0);
            Collections.sort(files, (a, b) -> Long.compare(b.size, a.size));
            scanComplete = true;
            Toast.makeText(this, "Found " + files.size() + " files to review.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not scan this folder: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        showPage("Storage");
    }
    private void walk(DocumentFile dir, int depth) {
        if (depth > 12 || files.size() >= MAX_RESULTS) return;
        DocumentFile[] children = dir.listFiles();
        for (DocumentFile f : children) {
            if (files.size() >= MAX_RESULTS) break;
            if (f.isDirectory()) walk(f, depth + 1);
            else if (f.isFile()) files.add(new FileEntry(f.getUri(), f.getName() == null ? "(unnamed file)" : f.getName(), Math.max(0, f.length()), false));
        }
    }
    private void renderFileResults() {
        content.addView(space(6)); content.addView(text("Review files", 19, ink, true));
        TextView note = text("Sorted by size • Select only files you recognise. Scan is limited to " + MAX_RESULTS + " files.", 12, muted, false);
        note.setPadding(0, dp(5), 0, dp(8)); content.addView(note); fileChecks.clear();
        if (files.isEmpty()) { content.addView(text("No files found in this folder.", 14, muted, false)); return; }
        for (FileEntry f : files) {
            CheckBox cb = new CheckBox(this); cb.setText(f.name + "\n" + formatSize(f.size)); cb.setTextSize(13); cb.setTextColor(ink);
            cb.setPadding(dp(4), dp(4), dp(4), dp(4)); cb.setTag(f); fileChecks.add(cb); content.addView(cb);
        }
        Button delete = new Button(this); delete.setText("Delete selected files"); delete.setAllCaps(false);
        delete.setOnClickListener(v -> deleteSelected()); content.addView(delete, new LinearLayout.LayoutParams(-1, dp(48)));
    }
    private void deleteSelected() {
        List<FileEntry> selected = new ArrayList<>();
        for (CheckBox cb : fileChecks) if (cb.isChecked()) selected.add((FileEntry) cb.getTag());
        if (selected.isEmpty()) { Toast.makeText(this, "Select at least one file first.", Toast.LENGTH_SHORT).show(); return; }
        long bytes = 0; for (FileEntry f : selected) bytes += f.size;
        new AlertDialog.Builder(this).setTitle("Review deletion").setMessage("Delete " + selected.size() + " selected file(s), totalling about " + formatSize(bytes) + "? This cannot always be undone.")
            .setNegativeButton("Cancel", null).setPositiveButton("Continue", (dialog, which) -> {
                int removed = 0;
                for (FileEntry f : selected) {
                    try { DocumentFile doc = DocumentFile.fromSingleUri(this, f.uri); if (doc != null && doc.delete()) removed++; }
                    catch (Exception ignored) {}
                }
                scanComplete = false; files.clear();
                Toast.makeText(this, "Deleted " + removed + " of " + selected.size() + " selected files.", Toast.LENGTH_LONG).show();
                scanFolder();
            }).show();
    }
    private void openStorageSettings() {
        try { startActivity(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)); }
        catch (Exception e) { try { startActivity(new Intent(Settings.ACTION_MANAGE_STORAGE)); } catch (Exception ignored) { Toast.makeText(this, "Open Settings → Storage.", Toast.LENGTH_LONG).show(); } }
    }
    private void openSecuritySettings() {
        try { startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)); }
        catch (Exception e) { Toast.makeText(this, "Open Android Settings → Security.", Toast.LENGTH_LONG).show(); }
    }
    private void openPlayProtect() {
        try { startActivity(new Intent("com.google.android.gms.security.ACTION_HARMFUL_APP_PROTECTION_SETTINGS")); }
        catch (Exception e) { openSecuritySettings(); }
    }
}
