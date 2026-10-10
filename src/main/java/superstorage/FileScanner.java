package superstorage;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Heuristic file scanner. It looks for suspicious file STRUCTURE (zip bombs, disguised
 * executables, ...). It does not use malware signatures and is not a replacement for
 * Windows Security.
 */
public final class FileScanner {

    public record Finding(String severity, String type, Path path, String detail) {}

    private record CdEntry(String name, int nameLen, long comp, long uncomp, long offset, boolean zip64) {}

    private static final class Budget {
        long remaining;
        long total;
        boolean tooDeep;
        boolean exhausted;

        Budget(long remaining) {
            this.remaining = remaining;
        }
    }

    private static final Set<String> ARCHIVE_EXTS = Set.of("zip", "jar", "war", "apk", "docx", "xlsx", "pptx", "odt");
    private static final Set<String> GZIP_EXTS = Set.of("gz", "tgz");
    private static final Set<String> EXEC_EXTS = Set.of("exe", "scr", "bat", "cmd", "com", "pif", "vbs", "vbe",
            "js", "jse", "wsf", "wsh", "hta", "msi", "ps1", "cpl");
    private static final Set<String> DISGUISE_EXTS = Set.of("txt", "pdf", "doc", "docx", "xls", "xlsx", "ppt",
            "pptx", "rtf", "csv", "jpg", "jpeg", "png", "gif", "bmp", "mp3", "mp4", "avi", "mkv", "wav",
            "zip", "rar", "7z");
    private static final Set<String> RISKY_EXTS = Set.of("scr", "pif", "vbs", "vbe", "jse", "wsf", "wsh", "hta",
            "cpl", "reg");
    private static final Pattern DOUBLE_EXT = Pattern.compile(
            ".+\\.(pdf|docx?|xlsx?|pptx?|txt|rtf|jpe?g|png|gif|bmp|zip|rar|7z|mp3|mp4|avi)\\s*"
                    + "\\.(exe|scr|com|bat|cmd|pif|vbs|vbe|js|jse|wsf|hta|ps1|msi|cpl)$",
            Pattern.CASE_INSENSITIVE);

    // Detection switches and limits, snapshotted when the scanner is created.
    private final boolean zipBomb = Settings.getBool(Settings.AV_ZIPBOMB);
    private final boolean nested = Settings.getBool(Settings.AV_NESTED);
    private final boolean overlap = Settings.getBool(Settings.AV_OVERLAP);
    private final boolean zipSlip = Settings.getBool(Settings.AV_ZIPSLIP);
    private final boolean exeInArchive = Settings.getBool(Settings.AV_EXE_IN_ARCHIVE);
    private final boolean doubleExt = Settings.getBool(Settings.AV_DOUBLE_EXT);
    private final boolean disguised = Settings.getBool(Settings.AV_DISGUISED);
    private final boolean risky = Settings.getBool(Settings.AV_RISKY);
    private final boolean autorun = Settings.getBool(Settings.AV_AUTORUN);
    private final boolean eicar = Settings.getBool(Settings.AV_EICAR);
    private final double maxRatio = Settings.getInt(Settings.AV_MAX_RATIO);
    private final long minBytes = Settings.getInt(Settings.AV_MIN_MB) * 1024L * 1024L;
    private final long budgetBytes = Settings.getInt(Settings.AV_BUDGET_MB) * 1024L * 1024L;
    private final int maxDepth = Settings.getInt(Settings.AV_MAX_DEPTH);

    private AtomicBoolean cancel;
    private Consumer<Finding> out;

    /** Scans a folder. Returns the number of files looked at. */
    public long scan(Path root, boolean subfolders, Consumer<Finding> onFinding,
                     Consumer<String> onProgress, AtomicBoolean cancelFlag) throws IOException {
        this.cancel = cancelFlag;
        this.out = onFinding;
        long[] count = {0};
        Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), subfolders ? Integer.MAX_VALUE : 1,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (cancelled()) return FileVisitResult.TERMINATE;
                        count[0]++;
                        if (count[0] % 25 == 0) onProgress.accept(file.toString());
                        try {
                            scanFile(file, attrs);
                        } catch (Exception ignored) {
                            // unreadable file: skip it
                        }
                        return cancelled() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                });
        return count[0];
    }

    private boolean cancelled() {
        return cancel != null && cancel.get();
    }

    private void add(String severity, String type, Path p, String detail) {
        out.accept(new Finding(severity, type, p, detail));
    }

    // ------------------------------------------------------------- per-file checks

    private void scanFile(Path p, BasicFileAttributes attrs) throws IOException {
        if (!attrs.isRegularFile()) return;
        String name = p.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        String ext = extOf(lower);
        long size = attrs.size();

        if (autorun && lower.equals("autorun.inf")) {
            add("Medium", "Autorun file", p, "autorun.inf can make a drive launch programs automatically.");
        }
        if (doubleExt) {
            if (hasBidiControl(name)) {
                add("High", "Unicode filename trick", p,
                        "The name contains a text-direction override character that can hide the real extension.");
            } else if (DOUBLE_EXT.matcher(name).matches()) {
                add("High", "Double extension", p,
                        "Looks like a document or image but really ends in a program/script extension.");
            }
        }
        if (risky && RISKY_EXTS.contains(ext)) {
            add("Medium", "Risky file type", p, "." + ext + " files can run code when opened.");
        }
        if (disguised && DISGUISE_EXTS.contains(ext) && size >= 2 && startsWithMZ(p)) {
            add("High", "Disguised executable", p,
                    "Named ." + ext + " but it is really a Windows program (MZ header).");
        }
        if (eicar && size > 0 && size <= 1024 && containsEicar(p)) {
            add("High", "EICAR test file", p, "Standard (harmless) antivirus test signature.");
        }
        if (ARCHIVE_EXTS.contains(ext) && size >= 22) {
            scanZip(p, ext, size);
        } else if (zipBomb && GZIP_EXTS.contains(ext) && size > 0) {
            scanGzip(p, size);
        }
    }

    private static String extOf(String lowerName) {
        int dot = lowerName.lastIndexOf('.');
        return dot >= 0 ? lowerName.substring(dot + 1) : "";
    }

    private static boolean hasBidiControl(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= '\u202A' && c <= '\u202E') || (c >= '\u2066' && c <= '\u2069')) return true;
        }
        return false;
    }

    private static boolean startsWithMZ(Path p) throws IOException {
        try (InputStream in = Files.newInputStream(p)) {
            return in.read() == 'M' && in.read() == 'Z';
        }
    }

    /** The EICAR string is assembled at runtime so this source/jar is not itself flagged by antivirus. */
    private static String eicarString() {
        String[] parts = {"X5O!P%@AP[4\\PZX54(P^)7CC)7}$", "EICAR-STANDARD-", "ANTIVIRUS-TEST-FILE!", "$H+H*"};
        return String.join("", parts);
    }

    private static boolean containsEicar(Path p) throws IOException {
        String text = new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1);
        return text.contains(eicarString());
    }

    // ------------------------------------------------------------------- zip checks

    private void scanZip(Path p, String ext, long size) {
        List<CdEntry> entries = null;
        try {
            entries = readCentralDirectory(p, size);
        } catch (IOException ignored) {
        }
        boolean flagged = false;
        if (entries != null) {
            if (zipBomb) flagged |= checkDeclaredSizes(p, entries, size);
            if (overlap) flagged |= checkOverlap(p, entries);
            if (zipSlip) checkZipSlip(p, entries);
            if (exeInArchive && ext.equals("zip")) checkExecutables(p, entries);
        }
        if (nested && !flagged && !cancelled()) deepScan(p, size);
    }

    private boolean checkDeclaredSizes(Path p, List<CdEntry> entries, long size) {
        long total = 0;
        for (CdEntry e : entries) {
            if (!e.zip64()) total += e.uncomp();
        }
        if (total >= minBytes && (double) total / size > maxRatio) {
            add("High", "Zip bomb", p, "Declares " + Sys.format(total) + " of data inside a " + Sys.format(size)
                    + " file (about " + (long) ((double) total / size) + ":1).");
            return true;
        }
        return false;
    }

    private boolean checkOverlap(Path p, List<CdEntry> entries) {
        List<CdEntry> sorted = entries.stream().filter(e -> !e.zip64())
                .sorted(Comparator.comparingLong(CdEntry::offset)).toList();
        for (int i = 0; i + 1 < sorted.size(); i++) {
            CdEntry a = sorted.get(i);
            CdEntry b = sorted.get(i + 1);
            long minEnd = a.offset() + 30 + a.nameLen() + a.comp();
            if (b.offset() < minEnd) {
                add("High", "Overlapping zip entries", p, "Entries '" + shorten(a.name()) + "' and '"
                        + shorten(b.name()) + "' share the same data - a known zip bomb technique.");
                return true;
            }
        }
        return false;
    }

    private void checkZipSlip(Path p, List<CdEntry> entries) {
        for (CdEntry e : entries) {
            String n = e.name().replace('\\', '/');
            boolean bad = n.startsWith("/") || n.matches("^[A-Za-z]:.*") || Arrays.asList(n.split("/")).contains("..");
            if (bad) {
                add("High", "Zip-slip path", p,
                        "Entry '" + shorten(e.name()) + "' tries to write outside the extraction folder.");
                return;
            }
        }
    }

    private void checkExecutables(Path p, List<CdEntry> entries) {
        List<String> hits = new ArrayList<>();
        for (CdEntry e : entries) {
            if (EXEC_EXTS.contains(extOf(e.name().toLowerCase(Locale.ROOT)))) hits.add(e.name());
        }
        if (!hits.isEmpty()) {
            add("Low", "Executable inside archive", p,
                    hits.size() + " program/script file(s), e.g. '" + shorten(hits.get(0)) + "'.");
        }
    }

    private static String shorten(String s) {
        return s.length() > 60 ? s.substring(0, 57) + "..." : s;
    }

    /** Unpacks the archive (recursively, up to a byte budget) to catch nested bombs and forged headers. */
    private void deepScan(Path p, long size) {
        Budget b = new Budget(budgetBytes);
        try (InputStream in = new BufferedInputStream(Files.newInputStream(p))) {
            walkNested(in, 0, b);
        } catch (IOException ignored) {
        }
        if (b.tooDeep) {
            add("High", "Nested archive bomb", p, "Archives are nested more than " + maxDepth + " levels deep.");
        } else if (b.total >= minBytes && (double) b.total / size > maxRatio) {
            add("High", "Zip bomb", p, (b.exhausted ? "Expands to at least " : "Expands to ")
                    + Sys.format(b.total) + " from a " + Sys.format(size)
                    + " file (header sizes may be forged).");
        }
    }

    private void walkNested(InputStream in, int depth, Budget b) throws IOException {
        ZipInputStream zis = new ZipInputStream(in);
        byte[] buf = new byte[64 * 1024];
        ZipEntry e;
        while (!b.exhausted && !b.tooDeep && !cancelled() && (e = zis.getNextEntry()) != null) {
            if (e.isDirectory()) continue;
            if (isArchiveName(e.getName())) {
                int level = depth + 1;
                if (level > maxDepth) {
                    b.tooDeep = true;
                    return;
                }
                try {
                    walkNested(zis, level, b);
                } catch (IOException ignored) {
                    // broken inner archive: keep going with the outer one
                }
                continue;
            }
            int n;
            while ((n = zis.read(buf)) > 0) {
                b.total += n;
                b.remaining -= n;
                if (b.remaining <= 0) {
                    b.exhausted = true;
                    return;
                }
                if (cancelled()) return;
            }
        }
    }

    private static boolean isArchiveName(String name) {
        String l = name.toLowerCase(Locale.ROOT);
        return l.endsWith(".zip") || l.endsWith(".jar") || l.endsWith(".war") || l.endsWith(".apk");
    }

    private void scanGzip(Path p, long size) {
        long total = 0;
        boolean exhausted = false;
        try (InputStream in = new GZIPInputStream(new BufferedInputStream(Files.newInputStream(p)))) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total >= budgetBytes) {
                    exhausted = true;
                    break;
                }
                if (cancelled()) return;
            }
        } catch (IOException ignored) {
        }
        if (total >= minBytes && (double) total / size > maxRatio) {
            add("High", "Gzip bomb", p, (exhausted ? "Expands to at least " : "Expands to ") + Sys.format(total)
                    + " from a " + Sys.format(size) + " file.");
        }
    }

    // ------------------------------------------------------- zip central directory

    private static List<CdEntry> readCentralDirectory(Path p, long size) throws IOException {
        try (FileChannel ch = FileChannel.open(p, StandardOpenOption.READ)) {
            int tail = (int) Math.min(size, 65_557L);
            ByteBuffer t = ByteBuffer.allocate(tail).order(ByteOrder.LITTLE_ENDIAN);
            readFully(ch, t, size - tail);

            int eocd = -1;
            for (int i = tail - 22; i >= 0; i--) {
                if (t.getInt(i) == 0x06054b50) {
                    eocd = i;
                    break;
                }
            }
            if (eocd < 0) return null;

            int count = t.getShort(eocd + 10) & 0xFFFF;
            long cdSize = t.getInt(eocd + 12) & 0xFFFFFFFFL;
            long cdOff = t.getInt(eocd + 16) & 0xFFFFFFFFL;
            if (count == 0xFFFF || cdSize == 0xFFFFFFFFL || cdOff == 0xFFFFFFFFL) return null; // zip64
            if (cdOff + cdSize > size || cdSize > 32L * 1024 * 1024) return null;

            ByteBuffer cd = ByteBuffer.allocate((int) cdSize).order(ByteOrder.LITTLE_ENDIAN);
            readFully(ch, cd, cdOff);

            List<CdEntry> list = new ArrayList<>();
            int pos = 0;
            for (int i = 0; i < count; i++) {
                if (pos + 46 > cd.capacity() || cd.getInt(pos) != 0x02014b50) break;
                long comp = cd.getInt(pos + 20) & 0xFFFFFFFFL;
                long uncomp = cd.getInt(pos + 24) & 0xFFFFFFFFL;
                int nameLen = cd.getShort(pos + 28) & 0xFFFF;
                int extraLen = cd.getShort(pos + 30) & 0xFFFF;
                int commentLen = cd.getShort(pos + 32) & 0xFFFF;
                long offset = cd.getInt(pos + 42) & 0xFFFFFFFFL;
                if (pos + 46 + nameLen > cd.capacity()) break;
                byte[] nameBytes = new byte[nameLen];
                cd.get(pos + 46, nameBytes);
                boolean z64 = comp == 0xFFFFFFFFL || uncomp == 0xFFFFFFFFL || offset == 0xFFFFFFFFL;
                list.add(new CdEntry(new String(nameBytes, StandardCharsets.UTF_8), nameLen, comp, uncomp, offset, z64));
                pos += 46 + nameLen + extraLen + commentLen;
            }
            return list;
        }
    }

    private static void readFully(FileChannel ch, ByteBuffer buf, long pos) throws IOException {
        while (buf.hasRemaining()) {
            int n = ch.read(buf, pos);
            if (n < 0) break;
            pos += n;
        }
    }
}
