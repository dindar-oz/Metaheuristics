package experimentrunner;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Starts the worker as a process of its own that outlives the GUI, the CLI and the terminal or session they run in.
 * <p>
 * The worker never runs from the build output: it runs from a frozen copy of the classpath in runtime/, so that a
 * rebuild ({@code mvnw clean}, the IDE) can neither break a running worker nor change the code an experiment runs with
 * halfway. A copy is reused while the code is unchanged; every run records the copy it ran with.
 * <p>
 * On Windows the process is created through WMI (Win32_Process.Create), so that it does not belong to the job of the
 * starting process: closing the terminal, the IDE or the Claude session does not end it.
 */
public final class WorkerLauncher {

    private WorkerLauncher() {
    }

    /** Starts a worker for the workspace and waits until it holds the workspace; returns its pid. */
    public static long start(Workspace ws, String maxHeap) {
        if (ws.workerAlive())
            throw new IllegalStateException("a worker is already running on " + ws.root());
        Path runtime = snapshot(ws);
        String classpath = runtime.resolve("classes") + File.pathSeparator + runtime.resolve("lib") + File.separator + "*";
        Path java = Path.of(System.getProperty("java.home"), "bin", windows() ? "javaw.exe" : "java");
        // not headless: the worker shows a tray icon when it runs in a desktop session (and goes without one otherwise)
        List<String> command = List.of(java.toString(), "-Xmx" + (maxHeap == null ? defaultHeap() : maxHeap),
                "-Dexperimentrunner.runtime=" + runtime.getFileName(), "-cp", classpath,
                Worker.class.getName(), ws.root().toString());
        long pid = windows() ? startWindows(ws, command) : startPosix(ws, command);
        for (int i = 0; i < 100; i++) {
            if (ws.workerAlive())
                return pid;
            Json.sleep(200);
        }
        throw new IllegalStateException("the worker did not start; see " + ws.logFile());
    }

    // 60% of the memory of the machine, between 1 and 16 GB
    static String defaultHeap() {
        long bytes = 8L << 30;
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os)
            bytes = os.getTotalMemorySize();
        long mb = Math.max(1024, Math.min(16 * 1024, (long) (bytes * 0.6) >> 20));
        return mb + "m";
    }

    static boolean windows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private static long startWindows(Workspace ws, List<String> command) {
        StringBuilder line = new StringBuilder();
        for (String part : command)
            line.append(line.length() == 0 ? "" : " ").append(part.contains(" ") || part.contains(";") || part.contains("*")
                    ? "\"" + part + "\"" : part);
        String script = "$r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{CommandLine = '"
                + line.toString().replace("'", "''") + "'; CurrentDirectory = '" + ws.root().toString().replace("'", "''")
                + "'}\nWrite-Output $r.ProcessId\nexit $r.ReturnValue\n";
        try {
            Path file = Files.createTempFile("start-worker", ".ps1");
            Files.writeString(file, script, StandardCharsets.UTF_8);
            try {
                Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                        "-File", file.toString()).redirectErrorStream(true).start();
                String out;
                try (InputStream in = p.getInputStream()) {
                    out = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                }
                if (p.waitFor() != 0)
                    throw new IllegalStateException("could not start the worker: " + out);
                String[] lines = out.split("\\R");
                return Long.parseLong(lines[lines.length - 1].trim());
            } finally {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while starting the worker", e);
        }
    }

    private static long startPosix(Workspace ws, List<String> command) {
        List<String> full = new ArrayList<>(List.of("nohup"));
        full.addAll(command);
        try {
            Process p = new ProcessBuilder(full).directory(ws.root().toFile())
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(ws.logFile().toFile()))
                    .redirectErrorStream(true).redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null"))).start();
            return p.pid();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---- the frozen copy of the classpath

    /** runtime/r-&lt;time&gt;-&lt;hash&gt;: classes/ and lib/ of this JVM's classpath; reused while the code is the same. */
    static Path snapshot(Workspace ws) {
        List<Path> entries = new ArrayList<>();
        for (String e : System.getProperty("java.class.path").split(File.pathSeparator))
            if (!e.isBlank() && Files.exists(Path.of(e)))
                entries.add(Path.of(e).toAbsolutePath());
        String hash = hash(entries);
        try {
            Files.createDirectories(ws.runtimeDir());
            try (Stream<Path> existing = Files.list(ws.runtimeDir())) {
                Path found = existing.filter(p -> p.getFileName().toString().endsWith("-" + hash)
                        && Files.exists(p.resolve("complete"))).findFirst().orElse(null);
                if (found != null)
                    return found;
            }
            Path dir = ws.runtimeDir().resolve("r-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + hash);
            Path classes = Files.createDirectories(dir.resolve("classes")), lib = Files.createDirectories(dir.resolve("lib"));
            for (Path e : entries) {
                if (Files.isDirectory(e))
                    copyTree(e, classes);
                else
                    Files.copy(e, lib.resolve(e.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(dir.resolve("complete"), String.join(System.lineSeparator(),
                    entries.stream().map(Path::toString).toList()) + System.lineSeparator());
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> files = Files.walk(from)) {
            for (Path f : files.toList()) {
                Path target = to.resolve(from.relativize(f).toString());
                if (Files.isDirectory(f))
                    Files.createDirectories(target);
                else
                    Files.copy(f, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    // what the Eclipse compiler (the IDE) puts into a class whose source did not compile; joined at run time, so that
    // this class does not contain it
    private static final byte[] BROKEN = String.join(" ", "Unresolved", "compilation", "problem").getBytes(StandardCharsets.UTF_8);

    private static boolean contains(byte[] bytes, byte[] part) {
        outer:
        for (int i = 0; i + part.length <= bytes.length; i++) {
            for (int j = 0; j < part.length; j++)
                if (bytes[i + j] != part[j])
                    continue outer;
            return true;
        }
        return false;
    }

    // over the contents of the class directories and the names and sizes of the jars; refuses broken classes
    static String hash(List<Path> entries) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (Path e : entries) {
                if (Files.isDirectory(e)) {
                    try (Stream<Path> files = Files.walk(e)) {
                        for (Path f : files.filter(Files::isRegularFile).sorted().toList()) {
                            byte[] bytes = Files.readAllBytes(f);
                            if (f.toString().endsWith(".class") && contains(bytes, BROKEN))
                                throw new IllegalStateException(f + " is a class that failed to compile (an IDE build of "
                                        + "the output directory?): run 'mvnw clean compile' and start the worker again");
                            md.update(e.relativize(f).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                            md.update(bytes);
                        }
                    }
                } else {
                    md.update(e.getFileName().toString().getBytes(StandardCharsets.UTF_8));
                    md.update(Long.toString(Files.size(e)).getBytes(StandardCharsets.UTF_8));
                }
            }
            return HexFormat.of().formatHex(md.digest()).substring(0, 10);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- removing frozen copies that are no longer needed

    /**
     * Deletes the frozen copies of the code in runtime/ except the newest {@code keep} complete ones (the newest is the
     * one the desktop shortcut opens the window with) and the one the running worker uses. A copy is renamed before it
     * is deleted; Windows refuses the rename while a program (a window, a worker) has files of it open, so a copy in
     * use is skipped as a whole, never half deleted. A copy still being written (no 'complete' marker, changed in the
     * last 10 minutes) is skipped too. Returns one line per copy.
     */
    static List<String> cleanup(Workspace ws, int keep, boolean dryRun) {
        if (keep < 1)
            throw new IllegalArgumentException("keep must be at least 1: the newest copy opens the window");
        List<String> report = new ArrayList<>();
        if (!Files.isDirectory(ws.runtimeDir())) {
            report.add("no frozen copies");
            return report;
        }
        WorkerInfo w = ws.worker();
        String used = ws.workerAlive() && w != null ? w.runtime : null;
        List<Path> copies;
        try (Stream<Path> s = Files.list(ws.runtimeDir())) {
            copies = s.filter(p -> Files.isDirectory(p) && p.getFileName().toString().startsWith("r-"))
                    .sorted(java.util.Comparator.comparing((Path p) -> p.getFileName().toString()).reversed()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        int kept = 0;
        long freed = 0;
        for (Path copy : copies) {
            String name = copy.getFileName().toString();
            boolean complete = Files.exists(copy.resolve("complete"));
            long size = size(copy);
            String mb = String.format(Locale.ROOT, "%.1f MB", size / 1048576.0);
            if (name.equals(used)) {
                if (complete)
                    kept++;     // it counts among the newest it usually is
                report.add("kept    " + name + " (" + mb + "): the running worker uses it");
                continue;
            }
            if (complete && kept < keep) {
                kept++;
                report.add("kept    " + name + " (" + mb + "): " + (kept == 1 ? "the newest" : "among the newest " + keep));
                continue;
            }
            if (!complete && recentlyChanged(copy)) {
                report.add("kept    " + name + ": still being written");
                continue;
            }
            if (dryRun) {
                report.add("removes " + name + " (" + mb + ")");
                freed += size;
                continue;
            }
            Path trash = copy.resolveSibling("deleting-" + name);
            try {
                Files.move(copy, trash);
            } catch (IOException e) {
                report.add("kept    " + name + ": in use by a running program (a window or a worker)");
                continue;
            }
            try (Stream<Path> files = Files.walk(trash)) {
                for (Path f : files.sorted(java.util.Comparator.reverseOrder()).toList())
                    Files.delete(f);
                report.add("removed " + name + " (" + mb + ")");
                freed += size;
            } catch (IOException e) {
                report.add("could not finish removing " + trash.getFileName() + ": " + e.getMessage());
            }
        }
        report.add(String.format(Locale.ROOT, "%s %.1f MB", dryRun ? "would free" : "freed", freed / 1048576.0));
        return report;
    }

    private static long size(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).mapToLong(f -> {
                try {
                    return Files.size(f);
                } catch (IOException e) {
                    return 0;
                }
            }).sum();
        } catch (IOException e) {
            return 0;
        }
    }

    private static boolean recentlyChanged(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            long newest = files.mapToLong(f -> {
                try {
                    return Files.getLastModifiedTime(f).toMillis();
                } catch (IOException e) {
                    return 0;
                }
            }).max().orElse(0);
            return System.currentTimeMillis() - newest < 10 * 60_000;
        } catch (IOException e) {
            return true;
        }
    }

    // ---- scripts that start the CLI and the GUI from the workspace

    /**
     * runner.cmd (and runner.sh) in the workspace: start the CLI with this JVM's classpath, i.e. the build output, so
     * that a later start-worker freezes the code as it is then.
     */
    static void writeScripts(Workspace ws) {
        String cp = String.join(File.pathSeparator, java.util.Arrays.stream(System.getProperty("java.class.path")
                .split(File.pathSeparator)).filter(e -> !e.isBlank()).map(e -> Path.of(e).toAbsolutePath().toString()).toList());
        if (cp.contains(ws.runtimeDir().toString()))
            return;     // started from a frozen copy: keep the scripts pointing at the build output
        Path java = Path.of(System.getProperty("java.home"), "bin", windows() ? "java.exe" : "java");
        // Java as it is now, else JAVA_HOME, else the java on the PATH: the scripts survive a Java update. No
        // parenthesized blocks: a path like "Program Files (x86)" would end them.
        String findJava = "set \"JAVA=" + java + "\"\r\n"
                + "if not exist \"%JAVA%\" if defined JAVA_HOME if exist \"%JAVA_HOME%\\bin\\java.exe\" set \"JAVA=%JAVA_HOME%\\bin\\java.exe\"\r\n"
                + "set \"FOUND=\"\r\n"
                + "if not exist \"%JAVA%\" for /f \"delims=\" %%j in ('where java 2^>nul') do if not defined FOUND set \"FOUND=%%j\"\r\n"
                + "if not exist \"%JAVA%\" if defined FOUND set \"JAVA=%FOUND%\"\r\n";
        String cmd = "@echo off\r\nrem Experiment runner: runner.cmd help | runner.cmd gui\r\nsetlocal\r\n" + findJava
                + "if exist \"%JAVA%\" goto run\r\necho Java was not found: install a JDK 21 or newer, or set JAVA_HOME. 1>&2\r\n"
                + "exit /b 1\r\n:run\r\n\"%JAVA%\" -cp \"" + cp + "\" " + Cli.class.getName() + " --workspace \"%~dp0.\" %*\r\n";
        String sh = "#!/bin/sh\n# Experiment runner: ./runner.sh help | ./runner.sh gui\nJAVA=\"" + java.toString().replace('\\', '/') + "\"\n"
                + "if [ ! -x \"$JAVA\" ]; then if [ -n \"$JAVA_HOME\" ] && [ -x \"$JAVA_HOME/bin/java\" ]; then JAVA=\"$JAVA_HOME/bin/java\"; "
                + "else JAVA=java; fi; fi\nexec \"$JAVA\" -cp \"" + cp + "\" " + Cli.class.getName() + " --workspace \"$(dirname \"$0\")\" \"$@\"\n";
        // the window from the newest frozen copy of the code (stable while the IDE rebuilds), else the build output
        String app = "experimentrunner.gui.ExperimentRunnerApp";
        String open = "@echo off\r\nrem Opens the Experiment Runner window (the desktop shortcut runs this)\r\nsetlocal\r\n" + findJava
                + "set \"JAVAW=%JAVA:java.exe=javaw.exe%\"\r\n"
                + "if exist \"%JAVAW%\" goto found\r\n"
                + "start \"Experiment Runner\" cmd /c \"echo Java was not found: install a JDK 21 or newer, or set JAVA_HOME. & pause\"\r\n"
                + "exit /b 1\r\n:found\r\nset \"R=\"\r\n"
                + "for /f \"delims=\" %%d in ('dir /b /ad /o-n \"%~dp0runtime\\r-*\" 2^>nul') do if not defined R if exist "
                + "\"%~dp0runtime\\%%d\\complete\" set \"R=%%d\"\r\n"
                + "if not defined R goto build\r\n"
                + "start \"\" \"%JAVAW%\" -cp \"%~dp0runtime\\%R%\\classes;%~dp0runtime\\%R%\\lib\\*\" " + app + " \"%~dp0.\"\r\n"
                + "exit /b 0\r\n:build\r\n"
                + "start \"\" \"%JAVAW%\" -cp \"" + cp + "\" " + app + " \"%~dp0.\"\r\n";
        try {
            if (!cmd.equals(Summary.read(ws.root().resolve("runner.cmd"))))
                Json.writeString(ws.root().resolve("runner.cmd"), cmd);
            if (!sh.equals(Summary.read(ws.root().resolve("runner.sh"))))
                Json.writeString(ws.root().resolve("runner.sh"), sh);
            if (windows() && !open.equals(Summary.read(ws.root().resolve("open-runner.cmd"))))
                Json.writeString(ws.root().resolve("open-runner.cmd"), open);
        } catch (RuntimeException ignored) {
            // the scripts are a convenience
        }
    }

    /** "Experiment Runner" on the desktop: opens the window through open-runner.cmd. Returns the shortcut's path. */
    static Path desktopShortcut(Workspace ws) {
        if (!windows())
            throw new IllegalStateException("desktop shortcuts are made on Windows only; run 'runner.sh gui'");
        writeScripts(ws);
        Path ico = ws.root().resolve("runner.ico");
        writeIcon(ico);
        String script = "$desktop = [Environment]::GetFolderPath('Desktop')\n"
                + "$path = Join-Path $desktop 'Experiment Runner.lnk'\n"
                + "$s = (New-Object -ComObject WScript.Shell).CreateShortcut($path)\n"
                + "$s.TargetPath = '" + ws.root().resolve("open-runner.cmd").toString().replace("'", "''") + "'\n"
                + "$s.WorkingDirectory = '" + ws.root().toString().replace("'", "''") + "'\n"
                + "$s.IconLocation = '" + ico.toString().replace("'", "''") + ",0'\n"
                + "$s.WindowStyle = 7\n"
                + "$s.Description = 'Experiment Runner: queue, worker and journals of " + ws.root().toString().replace("'", "''") + "'\n"
                + "$s.Save()\nWrite-Output $path\n";
        return Path.of(powershell(script));
    }

    // an .ico holding PNG images of the runner's icon (Windows Vista and later read PNG in .ico)
    static void writeIcon(Path file) {
        int[] sizes = {16, 32, 48, 256};
        try {
            List<byte[]> pngs = new ArrayList<>();
            for (int size : sizes) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(experimentrunner.gui.WorkerTray.icon(size), "png", out);
                pngs.add(out.toByteArray());
            }
            java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(6 + 16 * sizes.length + pngs.stream().mapToInt(p -> p.length).sum())
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN);
            b.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
            int offset = 6 + 16 * sizes.length;
            for (int i = 0; i < sizes.length; i++) {
                b.put((byte) (sizes[i] % 256)).put((byte) (sizes[i] % 256)).put((byte) 0).put((byte) 0)
                        .putShort((short) 1).putShort((short) 32).putInt(pngs.get(i).length).putInt(offset);
                offset += pngs.get(i).length;
            }
            pngs.forEach(b::put);
            Files.write(file, b.array());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // runs a PowerShell script; returns the last line it printed
    private static String powershell(String script) {
        try {
            Path file = Files.createTempFile("experiment-runner", ".ps1");
            Files.writeString(file, script, StandardCharsets.UTF_8);
            try {
                Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                        "-File", file.toString()).redirectErrorStream(true).start();
                String out;
                try (InputStream in = p.getInputStream()) {
                    out = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                }
                if (p.waitFor() != 0)
                    throw new IllegalStateException("PowerShell failed: " + out);
                String[] lines = out.split("\\R");
                return lines[lines.length - 1].trim();
            } finally {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }
}
