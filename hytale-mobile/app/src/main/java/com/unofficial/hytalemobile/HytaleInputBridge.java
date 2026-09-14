package com.unofficial.hytalemobile;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Java-side input bridge between the touch overlay and the future native
 * Win64 runtime. Alpha 0.3 intentionally keeps the transport independent:
 * controls already emit normalized packets, and a JNI/Wine backend can be
 * attached later without rewriting the UI.
 */
public final class HytaleInputBridge {
    public interface PacketListener {
        void onPacket(InputPacket packet);
    }

    public static final int TYPE_KEY = 1;
    public static final int TYPE_MOUSE_BUTTON = 2;
    public static final int TYPE_LOOK = 3;
    public static final int TYPE_MOVE = 4;
    public static final int TYPE_RELEASE_ALL = 5;

    public static final class InputPacket {
        public final long sequence;
        public final int type;
        public final String code;
        public final boolean down;
        public final float x;
        public final float y;
        public final long timeNanos;

        InputPacket(long sequence, int type, String code, boolean down, float x, float y) {
            this.sequence = sequence;
            this.type = type;
            this.code = code;
            this.down = down;
            this.x = x;
            this.y = y;
            this.timeNanos = System.nanoTime();
        }

        public String describe() {
            switch (type) {
                case TYPE_KEY:
                    return code + (down ? " ↓" : " ↑");
                case TYPE_MOUSE_BUTTON:
                    return code + (down ? " ↓" : " ↑");
                case TYPE_LOOK:
                    return String.format("MIRAR Δ %.0f / %.0f", x, y);
                case TYPE_MOVE:
                    return String.format("MOVE %.2f / %.2f", x, y);
                case TYPE_RELEASE_ALL:
                    return "INPUT · liberar todo";
                default:
                    return "INPUT";
            }
        }
    }

    private final Set<String> pressed = new HashSet<>();
    private PacketListener listener;
    private long sequence = 0;
    private InputPacket lastPacket;
    private float moveX;
    private float moveY;

    public void setPacketListener(PacketListener listener) {
        this.listener = listener;
    }

    public void key(String code, boolean down) {
        if (code == null || code.isEmpty() || "WASD".equals(code)) return;
        if ("MOUSE1".equals(code) || "MOUSE2".equals(code) || "MOUSE3".equals(code)) {
            mouseButton(code, down);
            return;
        }

        if (down) pressed.add(code);
        else pressed.remove(code);
        emit(TYPE_KEY, code, down, 0f, 0f);
    }

    public void mouseButton(String code, boolean down) {
        if (down) pressed.add(code);
        else pressed.remove(code);
        emit(TYPE_MOUSE_BUTTON, code, down, 0f, 0f);
    }

    public void look(float deltaX, float deltaY) {
        if (Math.abs(deltaX) + Math.abs(deltaY) < 0.01f) return;
        emit(TYPE_LOOK, "LOOK", false, deltaX, deltaY);
    }

    public void move(float x, float y) {
        float nx = clamp(x);
        float ny = clamp(y);
        if (Math.abs(nx - moveX) < 0.01f && Math.abs(ny - moveY) < 0.01f) return;
        moveX = nx;
        moveY = ny;
        emit(TYPE_MOVE, "MOVE", false, nx, ny);
    }

    public void releaseAll() {
        pressed.clear();
        moveX = 0f;
        moveY = 0f;
        emit(TYPE_RELEASE_ALL, "ALL", false, 0f, 0f);
    }

    public Set<String> getPressedSnapshot() {
        return Collections.unmodifiableSet(new HashSet<>(pressed));
    }

    public String getLastDescription() {
        return lastPacket == null ? "Input bridge listo" : lastPacket.describe();
    }

    public String diagnostics() {
        return "Bridge táctil Java: listo\n" +
                "Paquetes emitidos: " + sequence + "\n" +
                "Teclas activas: " + pressed.size() + "\n" +
                "Backend nativo/JNI: pendiente";
    }

    private void emit(int type, String code, boolean down, float x, float y) {
        InputPacket packet = new InputPacket(++sequence, type, code, down, x, y);
        lastPacket = packet;
        if (listener != null) listener.onPacket(packet);
    }

    private static float clamp(float v) {
        return Math.max(-1f, Math.min(1f, v));
    }
}
