package com.alonstark.tricontroller;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity implements BluetoothHidManager.Listener, ControllerSurface.Listener {
    private static final int REQ_BT = 41;
    private BluetoothHidManager hid;
    private ControllerSurface surface;
    private Spinner profileSpinner, deviceSpinner;
    private TextView status, specialHint;
    private final List<BluetoothDevice> devices = new ArrayList<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        hid = new BluetoothHidManager(this, this);
        buildUi();
        requestBluetoothThenStart();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(18,18,20));
        LinearLayout bar = new LinearLayout(this); bar.setOrientation(LinearLayout.HORIZONTAL); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(10,6,10,6);

        TextView title = new TextView(this); title.setText("TriConsole Controller  α0.2"); title.setTextColor(Color.WHITE); title.setTextSize(18);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        profileSpinner = new Spinner(this);
        String[] profiles = new String[]{"Xbox","PlayStation","Nintendo"};
        profileSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, profiles));
        bar.addView(profileSpinner, new LinearLayout.LayoutParams(dp(145), dp(48)));

        deviceSpinner = new Spinner(this);
        bar.addView(deviceSpinner, new LinearLayout.LayoutParams(dp(220), dp(48)));

        Button refresh = button("↻"); refresh.setOnClickListener(v -> refreshDevices()); bar.addView(refresh);
        Button bt = button("Bluetooth"); bt.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))); bar.addView(bt);
        Button connect = button("Conectar"); connect.setOnClickListener(v -> connectSelected()); bar.addView(connect);
        Button disconnect = button("Desconectar"); disconnect.setOnClickListener(v -> hid.disconnect()); bar.addView(disconnect);
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(58)));

        status = new TextView(this); status.setText("Preparando Bluetooth HID…"); status.setTextColor(Color.LTGRAY); status.setPadding(14,2,14,2); status.setTextSize(13);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(26)));

        specialHint = new TextView(this); specialHint.setText(ControllerProfile.XBOX.specialHint()); specialHint.setTextColor(Color.rgb(150,190,255)); specialHint.setPadding(14,0,14,4); specialHint.setTextSize(12);
        root.addView(specialHint, new LinearLayout.LayoutParams(-1, dp(26)));

        surface = new ControllerSurface(this); surface.setListener(this);
        root.addView(surface, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        profileSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                ControllerProfile p = ControllerProfile.values()[position];
                surface.setProfile(p); hid.setProfile(p);
                specialHint.setText(p.specialHint());
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private Button button(String text){ Button b=new Button(this);b.setText(text);b.setAllCaps(false);return b; }

    private void requestBluetoothThenStart() {
        if (Build.VERSION.SDK_INT >= 31) {
            List<String> missing = new ArrayList<>();
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.BLUETOOTH_CONNECT);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.BLUETOOTH_SCAN);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            if (!missing.isEmpty()) { requestPermissions(missing.toArray(new String[0]), REQ_BT); return; }
        }
        hid.start(ControllerProfile.XBOX); refreshDevices();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT) requestBluetoothThenStart();
    }

    private void refreshDevices() {
        devices.clear(); devices.addAll(hid.getBondedDevices());
        List<String> names = new ArrayList<>();
        for (BluetoothDevice d : devices) {
            String n;
            try { n = d.getName(); if(n==null)n=d.getAddress(); } catch(Exception e){ n="Dispositivo emparejado"; }
            names.add(n);
        }
        if(names.isEmpty()) names.add("Sin dispositivos emparejados");
        deviceSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
    }

    private void connectSelected() {
        int i=deviceSpinner.getSelectedItemPosition();
        if(i>=0&&i<devices.size()) hid.connect(devices.get(i)); else onStatus("Empareja primero el PC/Android desde Bluetooth.");
    }

    @Override public void onStatus(String text) { runOnUiThread(() -> status.setText(text)); }
    @Override public void onReady(boolean ready) { if(ready) runOnUiThread(this::refreshDevices); }
    @Override public void onState(GamepadState state) { hid.sendGamepad(state); }
    @Override public void onTouchpadDelta(int dx, int dy, boolean pressed) { if(surface.getProfile()==ControllerProfile.PLAYSTATION) hid.sendMouse(pressed?1:0,dx,dy,0); }

    @Override public void onSpecialAction(ControllerSurface.SpecialAction action) {
        if (action == ControllerSurface.SpecialAction.SCREENSHOT) {
            boolean ok = hid.sendPrintScreen();
            if (ok) onStatus(surface.getProfile().title + ": captura enviada al host.");
            else onStatus("Conecta un host para usar la función de captura.");
        } else if (action == ControllerSurface.SpecialAction.TOUCHPAD_CLICK) {
            boolean ok = hid.sendMouseClick();
            if (!ok) onStatus("Conecta un host para usar el clic del touchpad.");
        }
    }

    @Override protected void onDestroy() { hid.stop(); super.onDestroy(); }
    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
}
