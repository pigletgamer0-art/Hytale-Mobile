package com.winlator;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.GraphicsDrivers;
import com.winlator.core.FileUtils;
import com.winlator.xenvironment.RootFS;
import com.winlator.xenvironment.RootFSInstaller;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Hytale-focused launcher layered on top of the real Winlator runtime.
 * Hytale itself is never bundled. The user explicitly imports their own files.
 */
public final class HytaleMobileActivity extends MainActivity {
    private static final int PICK_HYTALE_FOLDER = 9401;
    private static final String CONTAINER_NAME = "Hytale Mobile";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private Button importButton;
    private Button launchButton;
    private ContainerManager containerManager;
    private Container hytaleContainer;
    private boolean creatingContainer;
    private boolean importing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showHytaleLauncher();
        handler.post(runtimeWatcher);
    }

    private final Runnable runtimeWatcher = new Runnable() {
        @Override public void run() {
            refreshRuntimeState();
            if (!isFinishing()) handler.postDelayed(this, 1000L);
        }
    };

    private void showHytaleLauncher() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(20), dp(24), dp(20));
        root.setBackgroundColor(Color.rgb(8, 13, 22));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = label("HYTALE MOBILE", 30, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title, lp(0, 0, 0, 5));

        TextView subtitle = label("Engine Alpha 0.6 · Wine + Box64", 14, Color.rgb(145, 190, 235));
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, lp(0, 0, 0, 18));

        status = label("Preparando runtime…", 14, Color.LTGRAY);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(14), dp(14), dp(14), dp(14));
        status.setBackground(panel());
        root.addView(status, lp(0, 0, 0, 16));

        importButton = action("Importar mi instalación de Hytale");
        importButton.setEnabled(false);
        importButton.setOnClickListener(v -> chooseHytaleFolder());
        root.addView(importButton, lp(0, 0, 0, 10));

        launchButton = action("INICIAR HYTALE");
        launchButton.setEnabled(false);
        launchButton.setOnClickListener(v -> launchHytale());
        root.addView(launchButton, lp(0, 0, 0, 10));

        Button controls = action("Controles móviles / ajustes avanzados");
        controls.setOnClickListener(v -> openAdvancedRuntime());
        root.addView(controls, lp(0, 0, 0, 14));

        TextView note = label(
                "No contiene Hytale. Importa una copia que ya tengas. El motor de compatibilidad es Winlator; Hytale Mobile es un proyecto comunitario no oficial.",
                12, Color.rgb(145, 150, 165));
        note.setGravity(Gravity.CENTER);
        root.addView(note, lp(0, 0, 0, 0));

        setContentView(scroll);
    }

    private void refreshRuntimeState() {
        if (status == null || importing) return;
        RootFS rootFS = RootFS.find(this);
        if (!rootFS.isValid() || rootFS.getVersion() < RootFSInstaller.LATEST_VERSION) {
            status.setText("Preparando el motor Windows…\nLa primera instalación extrae Wine, Box64 y los archivos del runtime. No cierres la app.");
            importButton.setEnabled(false);
            launchButton.setEnabled(false);
            return;
        }

        ensureHytaleContainer();
        if (hytaleContainer == null) {
            status.setText(creatingContainer
                    ? "Runtime listo. Creando contenedor optimizado para Hytale…"
                    : "Runtime listo. Preparando contenedor…");
            importButton.setEnabled(false);
            launchButton.setEnabled(false);
            return;
        }

        File client = findClient(gameDir());
        importButton.setEnabled(true);
        launchButton.setEnabled(client != null);
        if (client != null) {
            status.setText("✓ Motor Windows listo\n✓ Contenedor Hytale listo\n✓ HytaleClient.exe detectado\n\nYa puedes intentar iniciar el juego.");
        } else {
            status.setText("✓ Motor Windows listo\n✓ Contenedor Hytale listo\n\nFalta importar la carpeta que contiene HytaleClient.exe.");
        }
    }

    private void ensureHytaleContainer() {
        if (hytaleContainer != null || creatingContainer) return;
        if (containerManager == null) containerManager = new ContainerManager(this);
        for (Container c : containerManager.getContainers()) {
            if (CONTAINER_NAME.equals(c.getName())) {
                hytaleContainer = c;
                return;
            }
        }

        creatingContainer = true;
        try {
            JSONObject data = new JSONObject();
            data.put("name", CONTAINER_NAME);
            data.put("screenSize", "1280x720");
            data.put("envVars", Container.DEFAULT_ENV_VARS + " MESA_SHADER_CACHE_MAX_SIZE=1GB");
            data.put("graphicsDriver", GraphicsDrivers.getDefaultDriver(this));
            data.put("dxwrapper", Container.DEFAULT_DXWRAPPER);
            data.put("audioDriver", Container.DEFAULT_AUDIO_DRIVER);
            data.put("wincomponents", Container.DEFAULT_WINCOMPONENTS);
            data.put("drives", Container.DEFAULT_DRIVES);
            data.put("startupSelection", Container.STARTUP_SELECTION_ESSENTIAL);
            containerManager.createContainerAsync(data, container -> {
                creatingContainer = false;
                hytaleContainer = container;
                refreshRuntimeState();
            });
        } catch (Exception e) {
            creatingContainer = false;
            status.setText("No se pudo crear el contenedor: " + e.getMessage());
        }
    }

    private File gameDir() {
        if (hytaleContainer == null) return null;
        return new File(hytaleContainer.getRootDir(), ".wine/drive_c/Hytale");
    }

    private void chooseHytaleFolder() {
        if (hytaleContainer == null || importing) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, PICK_HYTALE_FOLDER);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_HYTALE_FOLDER) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri tree = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(
                            tree, data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {}
                importHytale(tree);
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void importHytale(Uri treeUri) {
        final File target = gameDir();
        if (target == null) return;
        importing = true;
        importButton.setEnabled(false);
        launchButton.setEnabled(false);
        status.setText("Importando Hytale…\nEsto puede tardar varios minutos.");

        new Thread(() -> {
            long[] counters = new long[]{0L, 0L};
            String error = null;
            try {
                FileUtils.delete(target);
                if (!target.mkdirs() && !target.isDirectory()) throw new Exception("No se pudo crear C:\\Hytale");
                String treeId = DocumentsContract.getTreeDocumentId(treeUri);
                Uri rootDoc = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeId);
                copyDirectory(rootDoc, target, counters);
                if (findClient(target) == null) throw new Exception("No encontré HytaleClient.exe dentro de la carpeta importada.");
            } catch (Throwable t) {
                error = t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
            }
            final String finalError = error;
            runOnUiThread(() -> {
                importing = false;
                if (finalError == null) {
                    status.setText("✓ Hytale importado\nArchivos copiados: " + counters[0] + "\nTamaño: " + human(counters[1]) + "\n\nListo para el primer intento de arranque.");
                } else {
                    status.setText("La importación no terminó correctamente.\n" + finalError);
                }
                refreshRuntimeState();
            });
        }, "HytaleImport").start();
    }

    private void copyDirectory(Uri directoryUri, File targetDir, long[] counters) throws Exception {
        String parentId = DocumentsContract.getDocumentId(directoryUri);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, parentId);
        ContentResolver resolver = getContentResolver();
        Cursor cursor = resolver.query(children, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        }, null, null, null);
        if (cursor == null) throw new Exception("Android no pudo leer la carpeta seleccionada.");

        try {
            int idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
            while (cursor.moveToNext()) {
                String docId = cursor.getString(idCol);
                String name = safeName(cursor.getString(nameCol));
                String mime = cursor.getString(mimeCol);
                if (name.isEmpty()) continue;
                Uri child = DocumentsContract.buildDocumentUriUsingTree(directoryUri, docId);
                File output = new File(targetDir, name);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    if (!output.mkdirs() && !output.isDirectory()) throw new Exception("No pude crear " + name);
                    copyDirectory(child, output, counters);
                } else {
                    copyFile(child, output, counters);
                }
            }
        } finally {
            cursor.close();
        }
    }

    private void copyFile(Uri source, File output, long[] counters) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(source);
             FileOutputStream out = new FileOutputStream(output)) {
            if (in == null) throw new Exception("No se pudo abrir " + output.getName());
            byte[] buffer = new byte[256 * 1024];
            int read;
            long bytes = 0L;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                bytes += read;
            }
            counters[0]++;
            counters[1] += bytes;
            if ((counters[0] & 15L) == 0L) {
                final long files = counters[0], total = counters[1];
                runOnUiThread(() -> status.setText("Importando Hytale…\n" + files + " archivos · " + human(total)));
            }
        }
    }

    private void launchHytale() {
        if (hytaleContainer == null) return;
        File client = findClient(gameDir());
        if (client == null) {
            status.setText("HytaleClient.exe no está disponible. Importa la instalación primero.");
            return;
        }

        // XServerDisplayActivity converts this real Unix path into a Wine/DOS path
        // and starts it through Winlator's Win64 execution pipeline.
        Intent intent = new Intent(this, XServerDisplayActivity.class);
        intent.putExtra("container_id", hytaleContainer.id);
        intent.putExtra("exec_path", client.getAbsolutePath());
        startActivity(intent);
    }

    private void openAdvancedRuntime() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    private static File findClient(File dir) {
        if (dir == null || !dir.isDirectory()) return null;
        File exact = new File(dir, "HytaleClient.exe");
        if (exact.isFile() && exact.length() > 0L) return exact;
        File[] children = dir.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (child.isFile() && child.getName().equalsIgnoreCase("HytaleClient.exe") && child.length() > 0L) return child;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                File found = findClient(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String safeName(String raw) {
        if (raw == null) return "";
        String name = raw.replace('/', '_').replace('\\', '_').replace('\u0000', '_').trim();
        if (name.equals(".") || name.equals("..")) return "_" + name.replace('.', '_');
        return name;
    }

    private static String human(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024.0) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024.0) return String.format("%.1f MB", mb);
        return String.format("%.2f GB", mb / 1024.0);
    }

    private TextView label(String value, float sp, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        return text;
    }

    private Button action(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setMinHeight(dp(52));
        return button;
    }

    private LinearLayout.LayoutParams lp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private GradientDrawable panel() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.rgb(18, 27, 41));
        g.setCornerRadius(dp(14));
        g.setStroke(dp(1), Color.rgb(48, 77, 110));
        return g;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(runtimeWatcher);
        super.onDestroy();
    }
}
