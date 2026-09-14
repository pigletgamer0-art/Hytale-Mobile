package com.alonstark.tricontroller;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppQosSettings;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

public final class BluetoothHidManager {
    public interface Listener {
        void onStatus(String text);
        void onReady(boolean ready);
    }

    // Report 1: 16 buttons + hat + LX/LY/RX/RY/LT/RT.
    // Report 2: 3-button relative mouse for PlayStation-style touchpad mode on PCs.
    // Report 3: standard keyboard report used for console-like capture shortcuts on PC/Android hosts.
    private static final byte[] REPORT_DESCRIPTOR = new byte[] {
        0x05,0x01, 0x09,0x05, (byte)0xA1,0x01, (byte)0x85,0x01,
        0x05,0x09, 0x19,0x01, 0x29,0x10, 0x15,0x00, 0x25,0x01,
        0x75,0x01, (byte)0x95,0x10, (byte)0x81,0x02,
        0x05,0x01, 0x09,0x39, 0x15,0x00, 0x25,0x07, 0x35,0x00,
        0x46,0x3B,0x01, 0x65,0x14, 0x75,0x04, (byte)0x95,0x01, (byte)0x81,0x42,
        0x75,0x04, (byte)0x95,0x01, (byte)0x81,0x03,
        0x09,0x30, 0x09,0x31, 0x09,0x33, 0x09,0x34, 0x09,0x32, 0x09,0x35,
        0x15,0x00, 0x26,(byte)0xFF,0x00, 0x75,0x08, (byte)0x95,0x06, (byte)0x81,0x02,
        (byte)0xC0,
        0x05,0x01, 0x09,0x02, (byte)0xA1,0x01, (byte)0x85,0x02, 0x09,0x01, (byte)0xA1,0x00,
        0x05,0x09, 0x19,0x01, 0x29,0x03, 0x15,0x00, 0x25,0x01, 0x75,0x01, (byte)0x95,0x03, (byte)0x81,0x02,
        0x75,0x05, (byte)0x95,0x01, (byte)0x81,0x03,
        0x05,0x01, 0x09,0x30, 0x09,0x31, 0x09,0x38, 0x15,(byte)0x81, 0x25,0x7F, 0x75,0x08, (byte)0x95,0x03, (byte)0x81,0x06,
        (byte)0xC0, (byte)0xC0,

        0x05,0x01, 0x09,0x06, (byte)0xA1,0x01, (byte)0x85,0x03,
        0x05,0x07, 0x19,(byte)0xE0, 0x29,(byte)0xE7, 0x15,0x00, 0x25,0x01,
        0x75,0x01, (byte)0x95,0x08, (byte)0x81,0x02,
        0x75,0x08, (byte)0x95,0x01, (byte)0x81,0x03,
        0x05,0x07, 0x19,0x00, 0x29,0x65, 0x15,0x00, 0x25,0x65,
        0x75,0x08, (byte)0x95,0x06, (byte)0x81,0x00,
        (byte)0xC0
    };

    private final Context context;
    private final Listener listener;
    private final BluetoothAdapter adapter;
    private BluetoothHidDevice hid;
    private BluetoothDevice host;
    private ControllerProfile profile = ControllerProfile.XBOX;
    private boolean registered = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public BluetoothHidManager(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        adapter = BluetoothAdapter.getDefaultAdapter();
    }

    public boolean hasRequiredPermission() {
        return Build.VERSION.SDK_INT < 31 || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    public void start(ControllerProfile initialProfile) {
        profile = initialProfile;
        if (adapter == null) {
            listener.onStatus("Este dispositivo no tiene Bluetooth compatible.");
            return;
        }
        if (!hasRequiredPermission()) {
            listener.onStatus("Falta permiso de Bluetooth.");
            return;
        }
        adapter.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE);
    }

    public void stop() {
        if (hid != null) {
            try { if (host != null) hid.disconnect(host); } catch (Exception ignored) {}
            try { if (registered) hid.unregisterApp(); } catch (Exception ignored) {}
            try { adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid); } catch (Exception ignored) {}
        }
        hid = null; host = null; registered = false;
    }

    public List<BluetoothDevice> getBondedDevices() {
        List<BluetoothDevice> out = new ArrayList<>();
        if (adapter == null || !hasRequiredPermission()) return out;
        Set<BluetoothDevice> set = adapter.getBondedDevices();
        if (set != null) out.addAll(set);
        return out;
    }

    public void setProfile(ControllerProfile newProfile) {
        if (newProfile == profile) return;
        profile = newProfile;
        if (hid == null) return;
        if (host != null) {
            try { hid.disconnect(host); } catch (Exception ignored) {}
            host = null;
        }
        if (registered) {
            try { hid.unregisterApp(); } catch (Exception ignored) {}
            registered = false;
        }
        registerCurrentProfile();
    }

    public void connect(BluetoothDevice device) {
        if (hid == null || !registered || device == null) {
            listener.onStatus("Bluetooth HID todavía no está listo.");
            return;
        }
        host = device;
        listener.onStatus("Conectando a " + safeName(device) + "…");
        try {
            if (!hid.connect(device)) listener.onStatus("No se pudo iniciar la conexión HID.");
        } catch (SecurityException e) {
            listener.onStatus("Bluetooth bloqueado por permisos.");
        }
    }

    public void disconnect() {
        if (hid != null && host != null) {
            try { hid.disconnect(host); } catch (Exception ignored) {}
        }
        host = null;
    }

    public boolean sendGamepad(GamepadState state) {
        if (hid == null || host == null) return false;
        try { return hid.sendReport(host, 1, state.toReport()); }
        catch (Exception e) { return false; }
    }

    public boolean sendMouse(int buttons, int dx, int dy, int wheel) {
        if (hid == null || host == null) return false;
        byte[] report = new byte[] {(byte)(buttons & 7), (byte)limit127(dx), (byte)limit127(dy), (byte)limit127(wheel)};
        try { return hid.sendReport(host, 2, report); }
        catch (Exception e) { return false; }
    }

    public boolean sendMouseClick() {
        if (hid == null || host == null) return false;
        BluetoothDevice target = host;
        boolean down;
        try { down = hid.sendReport(target, 2, new byte[] {1,0,0,0}); }
        catch (Exception e) { return false; }
        mainHandler.postDelayed(() -> {
            if (hid == null || target == null) return;
            try { hid.sendReport(target, 2, new byte[] {0,0,0,0}); } catch (Exception ignored) {}
        }, 35);
        return down;
    }

    public boolean sendPrintScreen() {
        if (hid == null || host == null) return false;
        BluetoothDevice target = host;
        byte[] down = new byte[] {0,0,0x46,0,0,0,0,0};
        byte[] up = new byte[] {0,0,0,0,0,0,0,0};
        boolean sent;
        try { sent = hid.sendReport(target, 3, down); }
        catch (Exception e) { return false; }
        mainHandler.postDelayed(() -> {
            if (hid == null || target == null) return;
            try { hid.sendReport(target, 3, up); } catch (Exception ignored) {}
        }, 45);
        return sent;
    }

    private final BluetoothProfile.ServiceListener serviceListener = new BluetoothProfile.ServiceListener() {
        @Override public void onServiceConnected(int profileId, BluetoothProfile proxy) {
            hid = (BluetoothHidDevice) proxy;
            registerCurrentProfile();
        }
        @Override public void onServiceDisconnected(int profileId) {
            hid = null; registered = false; host = null;
            listener.onReady(false);
            listener.onStatus("Servicio HID desconectado.");
        }
    };

    private void registerCurrentProfile() {
        if (hid == null) return;
        BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                profile.hidName,
                "Gamepad Bluetooth virtual para PC/Android",
                "TriConsole",
                BluetoothHidDevice.SUBCLASS1_COMBO,
                REPORT_DESCRIPTOR
        );
        BluetoothHidDeviceAppQosSettings qos = null;
        Executor executor = context.getMainExecutor();
        boolean ok = hid.registerApp(sdp, null, qos, executor, callback);
        listener.onStatus(ok ? "Registrando " + profile.title + "…" : "Android rechazó el registro HID.");
    }

    private final BluetoothHidDevice.Callback callback = new BluetoothHidDevice.Callback() {
        @Override public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean isRegistered) {
            registered = isRegistered;
            listener.onReady(isRegistered);
            listener.onStatus(isRegistered ? profile.title + " listo. Elige un dispositivo emparejado." : "Perfil HID no registrado.");
        }

        @Override public void onConnectionStateChanged(BluetoothDevice device, int state) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                host = device;
                listener.onStatus("Conectado a " + safeName(device) + " como " + profile.title + ".");
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                if (host != null && host.equals(device)) host = null;
                listener.onStatus("Control desconectado.");
            }
        }

        @Override public void onGetReport(BluetoothDevice device, byte type, byte id, int bufferSize) {
            if (hid == null) return;
            if (id == 1) hid.replyReport(device, type, id, new GamepadState().toReport());
            else if (id == 2) hid.replyReport(device, type, id, new byte[] {0,0,0,0});
            else if (id == 3) hid.replyReport(device, type, id, new byte[] {0,0,0,0,0,0,0,0});
        }

        @Override public void onSetProtocol(BluetoothDevice device, byte protocol) {}
        @Override public void onInterruptData(BluetoothDevice device, byte reportId, byte[] data) {}
        @Override public void onVirtualCableUnplug(BluetoothDevice device) { if (host != null && host.equals(device)) host = null; }
    };

    private String safeName(BluetoothDevice d) {
        try {
            String n = d.getName();
            return n == null ? d.getAddress() : n;
        } catch (SecurityException e) { return "dispositivo"; }
    }

    private static int limit127(int v) { return Math.max(-127, Math.min(127, v)); }
}
