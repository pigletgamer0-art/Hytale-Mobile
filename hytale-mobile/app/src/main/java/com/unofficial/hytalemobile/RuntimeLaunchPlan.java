package com.unofficial.hytalemobile;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the future Hytale Win64 bootstrap without executing it yet.
 *
 * Android 10+ does not allow execve() from the app's writable home, so ARM64
 * host executables such as Box64/PRoot must be packaged in the APK native
 * library directory. The writable runtime tree is reserved for the guest
 * rootfs, Wine prefix, logs and game data.
 */
public final class RuntimeLaunchPlan {
    private RuntimeLaunchPlan() {}

    public static final class Plan {
        public final List<String> command = new ArrayList<>();
        public final Map<String, String> environment = new LinkedHashMap<>();
        public File workingDirectory;
        public String warning;

        public String describe() {
            StringBuilder out = new StringBuilder();
            out.append("BOOTSTRAP WIN64 · Alpha 0.4\n\n");
            out.append("Directorio de trabajo:\n")
                    .append(workingDirectory == null ? "(sin definir)" : workingDirectory.getAbsolutePath())
                    .append("\n\n");

            out.append("Comando preparado:\n");
            if (command.isEmpty()) out.append("(todavía incompleto)\n");
            else {
                for (String part : command) out.append(part).append(' ');
                out.append('\n');
            }

            out.append("\nEntorno:\n");
            for (Map.Entry<String, String> entry : environment.entrySet()) {
                out.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
            }

            if (warning != null && !warning.isEmpty()) {
                out.append("\nEstado:\n").append(warning);
            }
            return out.toString();
        }
    }

    public static Plan create(Context context, File hytaleClient) {
        RuntimeProbe.Report report = RuntimeProbe.inspect(context);
        Plan plan = new Plan();
        File runtime = report.root;
        File rootfs = new File(runtime, "rootfs");
        File prefix = new File(runtime, "prefix");
        File home = new File(runtime, "home");
        File tmp = new File(runtime, "tmp");

        plan.workingDirectory = hytaleClient == null ? runtime : hytaleClient.getParentFile();
        plan.environment.put("HOME", home.getAbsolutePath());
        plan.environment.put("TMPDIR", tmp.getAbsolutePath());
        plan.environment.put("WINEPREFIX", prefix.getAbsolutePath());
        plan.environment.put("WINEDEBUG", "-all");
        plan.environment.put("BOX64_DYNAREC", "1");
        plan.environment.put("BOX64_NOBANNER", "1");

        if (report.prootPath != null) {
            plan.command.add(report.prootPath.getAbsolutePath());
            plan.command.add("-0");
            plan.command.add("-r");
            plan.command.add(rootfs.getAbsolutePath());
            plan.command.add("-b");
            plan.command.add("/dev");
            plan.command.add("-b");
            plan.command.add("/proc");
            plan.command.add("-b");
            plan.command.add("/sys");
        }

        if (report.box64Path != null) plan.command.add(report.box64Path.getAbsolutePath());
        if (report.wine64Path != null) plan.command.add(report.wine64Path.getAbsolutePath());
        if (hytaleClient != null) plan.command.add(hytaleClient.getAbsolutePath());

        if (!report.readyForBootstrap()) {
            plan.warning = "Bootstrap no ejecutable todavía. Falta: " + report.missingSummary() + ".";
        } else if (hytaleClient == null) {
            plan.warning = "Runtime base detectado, pero falta resolver HytaleClient.exe a una ruta local ejecutable por el contenedor.";
        } else {
            plan.warning = "Plan completo preparado. La siguiente etapa es ejecutar este bootstrap dentro de una sesión gráfica Android segura.";
        }
        return plan;
    }
}
