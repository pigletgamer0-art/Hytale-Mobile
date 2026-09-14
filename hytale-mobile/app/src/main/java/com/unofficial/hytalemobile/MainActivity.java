package com.unofficial.hytalemobile;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

public final class MainActivity extends Activity {
    private static final int PICK_GAME_FOLDER = 1001;
    private static final String PREFS = "hytale_mobile";
    private static final String KEY_GAME_TREE = "game_tree";

    private TextView status;
    private Uri gameTree;
    private final HytaleInputBridge inputBridge = new HytaleInputBridge();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setImmersive();
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_GAME_TREE, null);
        if (saved != null) {
            try { gameTree = Uri.parse(saved); } catch (Exception ignored) { gameTree = null; }
        }
        showLauncher();
    }

    private void showLauncher() {
        setImmersive();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(18), dp(24), dp(18));
        root.setBackgroundColor(Color.rgb(10, 14, 22));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("HYTALE MOBILE", 30, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap(0, 0, 0, 8));

        TextView subtitle = text("Port comunitario no oficial · Alpha 0.4", 15, Color.rgb(155, 190, 230));
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, matchWrap(0, 0, 0, 22));

        status = text("", 15, Color.LTGRAY);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(14), dp(14), dp(14), dp(14));
        status.setBackground(panelDrawable());
        root.addView(status, matchWrap(0, 0, 0, 18));

        Button select = button("Seleccionar carpeta de Hytale");
        select.setOnClickListener(v -> chooseFolder());
        root.addView(select, matchWrap(0, 0, 0, 10));

        Button importGame = button("Importar instalación al runtime");
        importGame.setOnClickListener(v -> importGameToRuntime());
        root.addView(importGame, matchWrap(0, 0, 0, 10));

        Button diagnose = button("Diagnóstico del teléfono");
        diagnose.setOnClickListener(v -> status.setText(deviceReport()));
        root.addView(diagnose, matchWrap(0, 0, 0, 10));

        Button runtime = button("Runtime ARM64/Win64 · estado");
        runtime.setOnClickListener(v -> status.setText(RuntimeProbe.describe(this)));
        root.addView(runtime, matchWrap(0, 0, 0, 10));

        Button bootstrap = button("Bootstrap Win64 · plan");
        bootstrap.setOnClickListener(v -> {
            File client = RuntimeGameImporter.hasImportedClient(this) ? RuntimeGameImporter.clientExe(this) : null;
            status.setText(RuntimeLaunchPlan.create(this, client).describe());
        });
        root.addView(bootstrap, matchWrap(0, 0, 0, 10));

        Button controls = button("Controles móviles · configurar");
        controls.setOnClickListener(v -> showControls());
        root.addView(controls, matchWrap(0, 0, 0, 10));

        Button inputStatus = button("Input bridge · estado");
        inputStatus.setOnClickListener(v -> status.setText(inputBridge.diagnostics()));
        root.addView(inputStatus, matchWrap(0, 0, 0, 10));

        Button launch = button("Iniciar Hytale");
        launch.setOnClickListener(v -> tryLaunch());
        root.addView(launch, matchWrap(0, 0, 0, 16));

        TextView note = text(
                "Esta APK no contiene archivos de Hytale. Alpha 0.4 añade la importación real de una instalación elegida por el usuario a la zona privada del runtime y corrige el diseño del runtime para Android 10+: Box64/PRoot deberán ir embebidos como binarios nativos del APK. Todavía no incluye esos binarios ni ejecuta HytaleClient.exe.",
                12, Color.rgb(150, 150, 160));
        note.setGravity(Gravity.CENTER);
        root.addView(note, matchWrap(0, 0, 0, 0));

        setContentView(scroll);
        updateFolderStatus();
    }

    private void chooseFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(i, PICK_GAME_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_GAME_FOLDER || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try { getContentResolver().takePersistableUriPermission(uri, flags); } catch (SecurityException ignored) {}
        gameTree = uri;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_GAME_TREE, uri.toString()).apply();
        updateFolderStatus();
    }

    private void updateFolderStatus() {
        if (status == null) return;
        boolean imported = RuntimeGameImporter.hasImportedClient(this);
        if (gameTree == null) {
            status.setText("Estado: esperando una instalación local de Hytale.\nSelecciona la carpeta que contiene HytaleClient.exe." +
                    (imported ? "\n\n✓ Ya existe una instalación importada en el runtime." : ""));
            return;
        }
        ScanResult result = scanTopLevel(gameTree);
        if (result.clientFound) {
            status.setText("✓ Instalación detectada\nHytaleClient.exe encontrado.\nArchivos visibles: " + result.count +
                    (imported ? "\n\n✓ Copia privada del runtime disponible." : "\n\nSiguiente paso: Importar instalación al runtime."));
        } else if (result.error != null) {
            status.setText("Carpeta guardada, pero Android no pudo inspeccionarla.\n" + result.error);
        } else {
            status.setText("Carpeta seleccionada, pero no encontré HytaleClient.exe en el nivel principal.\nArchivos visibles: " + result.count);
        }
    }

    private void importGameToRuntime() {
        if (gameTree == null) {
            status.setText("Primero selecciona la carpeta de Hytale.");
            return;
        }
        ScanResult scan = scanTopLevel(gameTree);
        if (!scan.clientFound) {
            status.setText("La carpeta seleccionada no tiene HytaleClient.exe en su raíz.");
            return;
        }

        status.setText("Preparando importación…\nNo cierres la app durante la copia.");
        RuntimeGameImporter.importAsync(this, gameTree, new RuntimeGameImporter.Listener() {
            @Override
            public void onProgress(String message) {
                runOnUiThread(() -> {
                    if (status != null) status.setText("Importando Hytale al runtime…\n" + message);
                });
            }

            @Override
            public void onFinished(RuntimeGameImporter.Result result) {
                runOnUiThread(() -> {
                    if (status == null) return;
                    if (result.success) {
                        status.setText("✓ Importación terminada\nArchivos copiados: " + result.filesCopied +
                                "\nDatos copiados: " + human(result.bytesCopied) +
                                "\n\nHytaleClient.exe:\n" + result.clientExe.getAbsolutePath());
                    } else {
                        status.setText("La importación falló.\n" + result.error);
                    }
                });
            }
        });
    }

    private ScanResult scanTopLevel(Uri treeUri) {
        ScanResult out = new ScanResult();
        Cursor cursor = null;
        try {
            String treeId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeId);
            cursor = getContentResolver().query(children, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
            if (cursor == null) { out.error = "El proveedor de archivos no devolvió una lista."; return out; }
            while (cursor.moveToNext()) {
                String name = cursor.getString(0);
                out.count++;
                if (name != null && name.equalsIgnoreCase("HytaleClient.exe")) out.clientFound = true;
            }
        } catch (Exception e) { out.error = e.getClass().getSimpleName() + ": " + e.getMessage(); }
        finally { if (cursor != null) cursor.close(); }
        return out;
    }

    private void tryLaunch() {
        if (!RuntimeGameImporter.hasImportedClient(this)) {
            status.setText("HytaleClient.exe está seleccionado, pero todavía falta importarlo al runtime privado.\nUsa: Importar instalación al runtime.");
            return;
        }

        File client = RuntimeGameImporter.clientExe(this);
        RuntimeProbe.Report report = RuntimeProbe.inspect(this);
        if (!report.readyForBootstrap()) {
            status.setText("Hytale ya está importado y tiene una ruta local real.\n\n" +
                    "Todavía no puedo iniciar el proceso Win64 porque falta: " + report.missingSummary() + ".\n\n" +
                    "La Alpha 0.4 ya prepara el comando y el entorno del bootstrap sin fingir una ejecución que aún no existe.");
            return;
        }

        status.setText(RuntimeLaunchPlan.create(this, client).describe() +
                "\n\nEl runtime base está detectado. Falta conectar la sesión gráfica/input al proceso antes de habilitar la ejecución real.");
    }

    private String deviceReport() {
        String[] abis = Build.SUPPORTED_ABIS;
        StringBuilder abi = new StringBuilder();
        for (int i = 0; i < abis.length; i++) { if (i > 0) abi.append(", "); abi.append(abis[i]); }
        boolean arm64 = false;
        for (String item : abis) if ("arm64-v8a".equals(item)) arm64 = true;
        return "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\nDispositivo: " + Build.MANUFACTURER + " " + Build.MODEL + "\nABI: " + abi + "\nARM64: " + (arm64 ? "sí" : "no") + "\nObjetivo del port: ARM64 + Vulkan/Zink + capa Win64";
    }

    private void showControls() {
        setImmersive();
        status = null;
        GameControlsView controls = new GameControlsView(this);
        controls.setInputBridge(inputBridge);
        controls.setExitListener(this::showLauncher);
        setContentView(controls);
    }

    @Override public void onBackPressed() { showLauncher(); }

    private TextView text(String value, float sp, int color) { TextView t = new TextView(this); t.setText(value); t.setTextSize(sp); t.setTextColor(color); return t; }
    private Button button(String label) { Button b = new Button(this); b.setText(label); b.setTextSize(15f); b.setAllCaps(false); b.setMinHeight(dp(50)); return b; }
    private LinearLayout.LayoutParams matchWrap(int l, int t, int r, int b) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); p.setMargins(dp(l), dp(t), dp(r), dp(b)); return p; }
    private GradientDrawable panelDrawable() { GradientDrawable g = new GradientDrawable(); g.setColor(Color.rgb(20, 27, 40)); g.setCornerRadius(dp(14)); g.setStroke(dp(1), Color.rgb(50, 75, 105)); return g; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void setImmersive() { getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE); }

    private static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kib = bytes / 1024.0;
        if (kib < 1024) return String.format(java.util.Locale.US, "%.1f KB", kib);
        double mib = kib / 1024.0;
        if (mib < 1024) return String.format(java.util.Locale.US, "%.1f MB", mib);
        return String.format(java.util.Locale.US, "%.2f GB", mib / 1024.0);
    }

    private static final class ScanResult { boolean clientFound; int count; String error; }
}
