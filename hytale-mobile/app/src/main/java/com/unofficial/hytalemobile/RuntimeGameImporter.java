package com.unofficial.hytalemobile;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Copies a user-selected Hytale installation from Android's Storage Access
 * Framework into the app-private runtime tree. The APK never contains Hytale
 * files; the user explicitly selects their own installation.
 */
public final class RuntimeGameImporter {
    private RuntimeGameImporter() {}

    public interface Listener {
        void onProgress(String message);
        void onFinished(Result result);
    }

    public static final class Result {
        public boolean success;
        public long filesCopied;
        public long bytesCopied;
        public File gameDir;
        public File clientExe;
        public String error;
    }

    public static File gameDir(Context context) {
        return new File(RuntimeProbe.runtimeRoot(context), "game");
    }

    public static File clientExe(Context context) {
        return new File(gameDir(context), "HytaleClient.exe");
    }

    public static boolean hasImportedClient(Context context) {
        File client = clientExe(context);
        return client.isFile() && client.length() > 0;
    }

    public static void importAsync(Context context, Uri treeUri, Listener listener) {
        new Thread(() -> {
            Result result = new Result();
            result.gameDir = gameDir(context);
            try {
                if (treeUri == null) throw new IllegalArgumentException("No hay carpeta seleccionada.");
                File target = result.gameDir;
                if (!target.exists() && !target.mkdirs()) throw new IllegalStateException("No se pudo crear la carpeta del runtime.");

                String rootId = DocumentsContract.getTreeDocumentId(treeUri);
                Uri rootDoc = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId);
                copyDirectory(context.getContentResolver(), rootDoc, target, result, listener);

                result.clientExe = clientExe(context);
                if (!result.clientExe.isFile() || result.clientExe.length() == 0) {
                    throw new IllegalStateException("La copia terminó, pero HytaleClient.exe no quedó en la raíz importada.");
                }
                result.success = true;
            } catch (Throwable t) {
                result.success = false;
                result.error = t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
            }
            if (listener != null) listener.onFinished(result);
        }, "HytaleRuntimeImport").start();
    }

    private static void copyDirectory(ContentResolver resolver, Uri directoryUri, File targetDir,
                                      Result result, Listener listener) throws Exception {
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IllegalStateException("No se pudo crear " + targetDir.getAbsolutePath());
        }

        String parentId = DocumentsContract.getDocumentId(directoryUri);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, parentId);
        Cursor cursor = resolver.query(childrenUri,
                new String[]{
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE
                }, null, null, null);
        if (cursor == null) throw new IllegalStateException("Android no devolvió la lista de archivos.");

        try {
            int idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
            int sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE);

            while (cursor.moveToNext()) {
                String childId = cursor.getString(idCol);
                String rawName = cursor.getString(nameCol);
                String mime = cursor.getString(mimeCol);
                long expectedSize = sizeCol >= 0 && !cursor.isNull(sizeCol) ? cursor.getLong(sizeCol) : -1L;
                String name = safeName(rawName);
                if (name.isEmpty()) continue;

                Uri childUri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, childId);
                File output = new File(targetDir, name);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    if (listener != null) listener.onProgress("Carpeta: " + name);
                    copyDirectory(resolver, childUri, output, result, listener);
                } else {
                    if (listener != null) listener.onProgress("Copiando: " + name + (expectedSize > 0 ? " · " + human(expectedSize) : ""));
                    copyFile(resolver, childUri, output, result);
                }
            }
        } finally {
            cursor.close();
        }
    }

    private static void copyFile(ContentResolver resolver, Uri source, File output, Result result) throws Exception {
        File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IllegalStateException("No se pudo crear carpeta destino.");

        try (InputStream in = resolver.openInputStream(source);
             FileOutputStream out = new FileOutputStream(output, false)) {
            if (in == null) throw new IllegalStateException("No se pudo abrir " + source);
            byte[] buffer = new byte[1024 * 256];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                result.bytesCopied += read;
            }
            out.getFD().sync();
        }
        result.filesCopied++;
    }

    private static String safeName(String name) {
        if (name == null) return "";
        String clean = name.replace('/', '_').replace('\\', '_').replace("..", "_").trim();
        return clean.equals(".") || clean.equals("..") ? "_" : clean;
    }

    private static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kib = bytes / 1024.0;
        if (kib < 1024) return String.format(java.util.Locale.US, "%.1f KB", kib);
        double mib = kib / 1024.0;
        if (mib < 1024) return String.format(java.util.Locale.US, "%.1f MB", mib);
        return String.format(java.util.Locale.US, "%.2f GB", mib / 1024.0);
    }
}
