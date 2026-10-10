package superstorage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

/** Simple persistent settings stored in %APPDATA%\SuperStorage\settings.properties. */
public final class Settings {

    public static final String AV_ENABLED = "av.enabled";
    public static final String AV_ZIPBOMB = "av.zipbomb";
    public static final String AV_NESTED = "av.nested";
    public static final String AV_OVERLAP = "av.overlap";
    public static final String AV_ZIPSLIP = "av.zipslip";
    public static final String AV_EXE_IN_ARCHIVE = "av.exeInArchive";
    public static final String AV_DOUBLE_EXT = "av.doubleExt";
    public static final String AV_DISGUISED = "av.disguisedExe";
    public static final String AV_RISKY = "av.risky";
    public static final String AV_AUTORUN = "av.autorun";
    public static final String AV_EICAR = "av.eicar";
    public static final String AV_MAX_RATIO = "av.maxRatio";
    public static final String AV_MIN_MB = "av.minExpandedMb";
    public static final String AV_BUDGET_MB = "av.budgetMb";
    public static final String AV_MAX_DEPTH = "av.maxDepth";
    public static final String SCAN_FOLDER = "scan.folder";
    public static final String SCAN_SUBFOLDERS = "scan.subfolders";
    public static final String ASK_ADMIN = "app.askAdmin";
    public static final String CONFIRM_DELETE = "app.confirmDelete";
    public static final String THEME = "app.theme";

    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    private static final Properties PROPS = new Properties();
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Path FILE;

    static {
        DEFAULTS.put(AV_ENABLED, "true");
        DEFAULTS.put(AV_ZIPBOMB, "true");
        DEFAULTS.put(AV_NESTED, "true");
        DEFAULTS.put(AV_OVERLAP, "true");
        DEFAULTS.put(AV_ZIPSLIP, "true");
        DEFAULTS.put(AV_EXE_IN_ARCHIVE, "true");
        DEFAULTS.put(AV_DOUBLE_EXT, "true");
        DEFAULTS.put(AV_DISGUISED, "true");
        DEFAULTS.put(AV_RISKY, "true");
        DEFAULTS.put(AV_AUTORUN, "true");
        DEFAULTS.put(AV_EICAR, "true");
        DEFAULTS.put(AV_MAX_RATIO, "100");
        DEFAULTS.put(AV_MIN_MB, "50");
        DEFAULTS.put(AV_BUDGET_MB, "256");
        DEFAULTS.put(AV_MAX_DEPTH, "3");
        DEFAULTS.put(SCAN_FOLDER, Path.of(System.getProperty("user.home"), "Downloads").toString());
        DEFAULTS.put(SCAN_SUBFOLDERS, "true");
        DEFAULTS.put(ASK_ADMIN, "true");
        DEFAULTS.put(CONFIRM_DELETE, "true");
        DEFAULTS.put(THEME, "Light");

        String appData = System.getenv("APPDATA");
        Path base = appData != null ? Path.of(appData) : Path.of(System.getProperty("user.home"));
        FILE = base.resolve("SuperStorage").resolve("settings.properties");
        load();
    }

    private Settings() {}

    public static Path file() {
        return FILE;
    }

    public static String get(String key) {
        return PROPS.getProperty(key, DEFAULTS.getOrDefault(key, ""));
    }

    public static boolean getBool(String key) {
        return Boolean.parseBoolean(get(key));
    }

    public static int getInt(String key) {
        try {
            return Integer.parseInt(get(key).trim());
        } catch (NumberFormatException e) {
            return Integer.parseInt(DEFAULTS.get(key));
        }
    }

    public static void set(String key, Object value) {
        String v = String.valueOf(value);
        if (v.equals(get(key))) return;
        PROPS.setProperty(key, v);
        save();
        fire();
    }

    public static void resetAll() {
        PROPS.clear();
        save();
        fire();
    }

    /** Called (on whatever thread changed a setting) whenever any setting changes. */
    public static void addListener(Runnable r) {
        LISTENERS.add(r);
    }

    private static void fire() {
        for (Runnable r : LISTENERS) r.run();
    }

    private static void load() {
        if (Files.exists(FILE)) {
            try (InputStream in = Files.newInputStream(FILE)) {
                PROPS.load(in);
            } catch (IOException ignored) {
            }
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                PROPS.store(out, "SuperStorage settings");
            }
        } catch (IOException ignored) {
        }
    }
}
