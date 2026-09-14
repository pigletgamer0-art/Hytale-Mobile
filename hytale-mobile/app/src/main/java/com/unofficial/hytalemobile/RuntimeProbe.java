package com.unofficial.hytalemobile;

import android.content.Context;
import android.content.pm.FeatureInfo;
import android.os.Build;

import java.io.File;
import java.util.Locale;

/**
 * Runtime diagnostics for the future Win64 compatibility layer.
 *
 * Android 10+ blocks direct execve() from an app's writable home directory.
 * For that reason ARM64 host executables are expected in nativeLibraryDir
 * (embedded in the APK), while Wine/rootfs/prefix data stays under filesDir.
 */
public final class RuntimeProbe {
    private RuntimeProbe() {}

    public static final class Report {
        public boolean arm64;
        public boolean vulkan;
        public boolean box64;
        public boolean proot;
        public boolean wine64;
        public boolean graphicsBridge;
        public File root;
        public File nativeLibDir;
        public File box64Path;
        public File prootPath;
        public File wine64Path;

        public boolean readyForBootstrap() {
            return arm64 && vulkan && box64 && proot && wine64 && graphicsBridge;
        }

        public String missingSummary() {
            StringBuilder out = new StringBuilder();
            appendMissing(out, !arm64, "ARM64");
            appendMissing(out, !vulkan, "Vulkan");
            appendMissing(out, !box64, "Box64 embebido");
            appendMissing(out, !proot, "PRoot embebido");
            appendMissing(out, !wine64, "Wine64/rootfs");
            appendMissing(out, !graphicsBridge, "puente gráfico");
            return out.length() == 0 ? "ninguno" : out.toString();
        }
    }

    public static Report inspect(Context context) {
        ensureLayout(context);
        Report r = new Report();
        r.root = runtimeRoot(context);
        r.nativeLibDir = new File(context.getApplicationInfo().nativeLibraryDir);
        r.arm64 = supportsArm64();
        r.vulkan = supportsVulkan(context);

        r.box64Path = firstExisting(
                new File(r.nativeLibDir, "libbox64.so"),
                new File(r.nativeLibDir, "box64"));
        r.prootPath = firstExisting(
                new File(r.nativeLibDir, "libproot.so"),
                new File(r.nativeLibDir, "proot"));
        r.box64 = executableCandidate(r.box64Path);
        r.proot = executableCandidate(r.prootPath);

        r.wine64Path = firstExisting(
                new File(r.root, "rootfs/usr/bin/wine64"),
                new File(r.root, "rootfs/usr/bin/wine"),
                new File(r.root, "wine/bin/wine64"),
                new File(r.root, "wine/bin/wine"));
        r.wine64 = fileCandidate(r.wine64Path);

        r.graphicsBridge = fileCandidate(new File(r.nativeLibDir, "libvulkan_freedreno.so"))
                || fileCandidate(new File(r.nativeLibDir, "libturnip.so"))
                || fileCandidate(new File(r.nativeLibDir, "libzink.so"))
                || fileCandidate(new File(r.root, "rootfs/usr/lib/aarch64-linux-gnu/libvulkan.so.1"))
                || fileCandidate(new File(r.root, "rootfs/usr/lib/x86_64-linux-gnu/libvulkan.so.1"));
        return r;
    }

    public static String describe(Context context) {
        Report r = inspect(context);
        StringBuilder abi = new StringBuilder();
        for (int i = 0; i < Build.SUPPORTED_ABIS.length; i++) {
            if (i > 0) abi.append(", ");
            abi.append(Build.SUPPORTED_ABIS[i]);
        }

        return "RUNTIME ARM64/WIN64 · Alpha 0.4\n\n" +
                "ABI: " + abi + "\n" +
                "ARM64: " + yesNo(r.arm64) + "\n" +
                "Vulkan: " + yesNo(r.vulkan) + "\n" +
                "Box64 embebido: " + yesNo(r.box64) + "\n" +
                "PRoot embebido: " + yesNo(r.proot) + "\n" +
                "Wine64/rootfs: " + yesNo(r.wine64) + "\n" +
                "Puente gráfico: " + yesNo(r.graphicsBridge) + "\n\n" +
                "Binarios nativos del APK:\n" + r.nativeLibDir.getAbsolutePath() + "\n\n" +
                "Datos del runtime:\n" + r.root.getAbsolutePath() + "\n\n" +
                (r.readyForBootstrap()
                        ? "Componentes base detectados. Ya se puede preparar el bootstrap Win64."
                        : "Aún no está listo para ejecutar Hytale. Falta: " + r.missingSummary() + ".");
    }

    public static File runtimeRoot(Context context) {
        return new File(context.getFilesDir(), "hytale-runtime");
    }

    private static void ensureLayout(Context context) {
        File root = runtimeRoot(context);
        new File(root, "rootfs").mkdirs();
        new File(root, "prefix").mkdirs();
        new File(root, "home").mkdirs();
        new File(root, "tmp").mkdirs();
        new File(root, "logs").mkdirs();
        new File(root, "drivers").mkdirs();
    }

    private static boolean supportsArm64() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equalsIgnoreCase(abi)) return true;
        }
        return false;
    }

    private static boolean supportsVulkan(Context context) {
        try {
            FeatureInfo[] features = context.getPackageManager().getSystemAvailableFeatures();
            if (features != null) {
                for (FeatureInfo feature : features) {
                    if (feature != null && feature.name != null
                            && feature.name.toLowerCase(Locale.ROOT).contains("vulkan")) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static File firstExisting(File... files) {
        if (files == null) return null;
        for (File file : files) if (file != null && file.isFile() && file.length() > 0) return file;
        return null;
    }

    private static boolean executableCandidate(File file) {
        return file != null && file.isFile() && file.length() > 0;
    }

    private static boolean fileCandidate(File file) {
        return file != null && file.isFile() && file.length() > 0;
    }

    private static String yesNo(boolean value) {
        return value ? "sí" : "no";
    }

    private static void appendMissing(StringBuilder out, boolean missing, String name) {
        if (!missing) return;
        if (out.length() > 0) out.append(", ");
        out.append(name);
    }
}
