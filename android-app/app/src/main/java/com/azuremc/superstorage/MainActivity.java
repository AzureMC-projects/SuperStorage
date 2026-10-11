package com.azuremc.superstorage;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Native Android-first SuperStorage shell.
 * Storage management opens Android's own storage screen; this app does not claim
 * to clean files or detect malware until those engines are implemented.
 */
public class MainActivity extends Activity {
    private final int ink = Color.rgb(27, 35, 49);
    private final int muted = Color.rgb(105, 116, 132);
    private final int accent = Color.rgb(45, 91, 230);
    private final int canvas = Color.rgb(246, 248, 252);
    private LinearLayout content;
    private String current = "Home";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(canvas);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        showPage("Home");
    }

    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private void showPage(String page) {
        current = page;
        LinearLayout root = column();
        root.setBackgroundColor(canvas);
        root.setPadding(dp(20), dp(22), dp(20), dp(8));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = text("◈", 28, accent, true);
        header.addView(logo, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout brand = column();
        brand.addView(text("SuperStorage", 20, ink, true));
        brand.addView(text("A calmer, cleaner phone", 12, muted, false));
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header);
        root.addView(space(20));

        ScrollView scroll = new ScrollView(this);
        content = column();
        content.setPadding(0, 0, 0, dp(18));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        render(page);

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(Color.WHITE);
        String[] pages = {"Home", "Storage", "Security", "Settings"};
        for (String item : pages) {
            TextView tab = text((current.equals(item) ? "●  " : "") + item, 11, current.equals(item) ? accent : muted, current.equals(item));
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(5), dp(14), dp(5), dp(14));
            tab.setOnClickListener(v -> showPage(item));
            nav.addView(tab, new LinearLayout.LayoutParams(0, -2, 1));
        }
        LinearLayout shell = column();
        shell.addView(root, new LinearLayout.LayoutParams(-1, 0, 1));
        shell.addView(nav);
        setContentView(shell);
    }

    private View space(int height) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        return v;
    }

    private void heading(String title, String subtitle) {
        content.addView(text(title, 27, ink, true));
        TextView sub = text(subtitle, 14, muted, false);
        sub.setPadding(0, dp(6), 0, dp(16));
        content.addView(sub);
    }

    private void card(String title, String description, String action, Runnable run) {
        LinearLayout box = column();
        box.setBackgroundColor(Color.WHITE);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView h = text(title, 17, ink, true);
        box.addView(h);
        TextView d = text(description, 13, muted, false);
        d.setPadding(0, dp(7), 0, dp(14));
        box.addView(d);
        Button button = new Button(this);
        button.setText(action);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));
        button.setOnClickListener(v -> run.run());
        box.addView(button, new LinearLayout.LayoutParams(-1, dp(46)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        content.addView(box, lp);
    }

    private void render(String page) {
        if ("Home".equals(page)) {
            heading("Your phone,\nunder control.", "Simple tools for storage and peace of mind.");
            card("Clear up storage", "Find what is using space and choose what you want to remove.", "Review storage", () -> showPage("Storage"));
            card("Security centre", "Understand what can be checked on Android—without fake protection claims.", "Open security", () -> showPage("Security"));
            card("Your settings", "Personalise the experience and manage privacy choices.", "Manage settings", () -> showPage("Settings"));
        } else if ("Storage".equals(page)) {
            heading("Clear up storage", "Review space safely. Nothing is deleted by this screen.");
            card("Android storage", "See storage usage and use the built-in Android tools to free up space.", "Open system storage", this::openStorageSettings);
            card("Large files & duplicates", "File-by-file review and duplicate detection are planned; no automatic deletion is enabled yet.", "Feature status", () -> Toast.makeText(this, "File analysis is not implemented yet.", Toast.LENGTH_LONG).show());
            card("Safe cleanup", "The app will ask you to review every item before removal. Android limits access to other apps' private data.", "How it works", () -> Toast.makeText(this, "Cleanup will use Android-approved access and confirmation.", Toast.LENGTH_LONG).show());
        } else if ("Security".equals(page)) {
            heading("Security centre", "Honest status, not a pretend antivirus.");
            card("Google Play Protect", "Android's built-in app protection can check installed apps. Open system settings to review it.", "Open security settings", this::openSecuritySettings);
            card("File scanning", "A local file scanner and threat-signature engine have not been implemented. No files are currently being scanned.", "Scan status", () -> Toast.makeText(this, "No malware scan has run. Protection is not being claimed.", Toast.LENGTH_LONG).show());
            card("Privacy first", "No background monitoring or uploading of personal files is performed by this starter app.", "Understood", () -> {});
        } else {
            heading("Settings", "Your app, your choices.");
            card("Appearance", "This starter uses a light, high-contrast layout. Theme selection can be added next.", "Theme options", () -> Toast.makeText(this, "Theme controls are not implemented yet.", Toast.LENGTH_SHORT).show());
            card("Cleanup confirmations", "Cleanup should always show a review list before removing anything.", "Confirmation policy: on", () -> Toast.makeText(this, "Review-before-delete is the intended default.", Toast.LENGTH_SHORT).show());
            card("Permissions & privacy", "Only request access when a feature needs it. No broad storage permission is requested by this starter.", "App permissions", () -> {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                i.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(i);
            });
            card("About SuperStorage", "Android-native starter • Version 1.0.0", "Done", () -> {});
        }
    }

    private void openStorageSettings() {
        try { startActivity(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)); }
        catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_MANAGE_STORAGE)); }
            catch (Exception ignored) { Toast.makeText(this, "Open Settings → Storage to review space.", Toast.LENGTH_LONG).show(); }
        }
    }

    private void openSecuritySettings() {
        try { startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)); }
        catch (Exception e) { Toast.makeText(this, "Open Android Settings → Security.", Toast.LENGTH_LONG).show(); }
    }
}
