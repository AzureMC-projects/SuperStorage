package superstorage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

/** PowerShell / elevation / formatting helpers. */
final class Sys {

    private static final String PS_HEADER = "$ProgressPreference='SilentlyContinue'\n";

    private Sys() {}

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    static String q(String s) {
        return s.replace("'", "''");
    }

    /** Builds a PowerShell command line; the script is Base64-encoded so quoting can't break it. */
    static List<String> psCommand(String script) {
        String encoded = Base64.getEncoder().encodeToString((PS_HEADER + script).getBytes(StandardCharsets.UTF_16LE));
        return List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-OutputFormat", "Text", "-EncodedCommand", encoded);
    }

    static boolean isAdmin() {
        String script = "if (([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent())"
                + ".IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { exit 0 } else { exit 1 }";
        try {
            Process p = new ProcessBuilder(psCommand(script)).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Starts a second copy of this app with a UAC prompt. Returns true if it was launched. */
    static boolean relaunchElevated() {
        try {
            String java = Path.of(System.getProperty("java.home"), "bin", "javaw.exe").toString();
            String cp = Path.of(SuperStorage.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
            while (cp.endsWith("\\")) cp = cp.substring(0, cp.length() - 1);

            String script = "try { Start-Process -FilePath '" + q(java) + "' -ArgumentList '-cp \"" + q(cp)
                    + "\" superstorage.SuperStorage' -Verb RunAs -ErrorAction Stop; exit 0 } catch { exit 1 }";
            Process p = new ProcessBuilder(psCommand(script)).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    static String format(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double v = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int i = -1;
        while (v >= 1024 && i < units.length - 1) {
            v /= 1024;
            i++;
        }
        return String.format("%.1f %s", v, units[i]);
    }
}
