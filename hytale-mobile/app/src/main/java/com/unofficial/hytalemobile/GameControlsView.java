package com.unofficial.hytalemobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Independent Hytale Mobile control overlay inspired by the workflow of
 * BattlyMobile2/Pojav custom controls: profile-driven buttons, joystick,
 * touch look area and an in-app layout editor. No Battly source is copied.
 */
public final class GameControlsView extends View {
    private static final String PREFS = "hytale_mobile_controls";
    private static final String KEY_LAYOUT = "layout_v2";

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Control> controls = new ArrayList<>();
    private final Map<Integer, Control> pointerControls = new HashMap<>();

    private Runnable exitListener;
    private HytaleInputBridge inputBridge = new HytaleInputBridge();
    private boolean editMode = false;
    private Control selected;
    private int editPointer = -1;
    private int joystickPointer = -1;
    private int lookPointer = -1;
    private float lastLookX;
    private float lastLookY;
    private float joystickX;
    private float joystickY;
    private String eventText = "INPUT BRIDGE · listo";

    public GameControlsView(Context context) {
        super(context);
        setFocusable(true);
        setBackgroundColor(Color.rgb(9, 14, 23));

        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(dp(2));
        border.setColor(Color.argb(210, 225, 238, 255));
        label.setColor(Color.WHITE);
        label.setTextAlign(Paint.Align.CENTER);
        label.setFakeBoldText(true);
        small.setColor(Color.rgb(170, 195, 225));
        small.setTextAlign(Paint.Align.CENTER);
        accent.setColor(Color.rgb(89, 177, 255));
        accent.setStyle(Paint.Style.STROKE);
        accent.setStrokeWidth(dp(3));

        attachBridgeListener();
        if (!loadLayout()) resetDefaults(false);
    }

    public void setExitListener(Runnable listener) {
        this.exitListener = listener;
    }

    public void setInputBridge(HytaleInputBridge bridge) {
        if (bridge == null) return;
        inputBridge.releaseAll();
        inputBridge = bridge;
        attachBridgeListener();
        eventText = inputBridge.getLastDescription();
        invalidate();
    }

    private void attachBridgeListener() {
        inputBridge.setPacketListener(packet -> {
            eventText = packet.describe();
            postInvalidate();
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawBackdrop(canvas);
        for (Control c : controls) drawControl(canvas, c);
        drawEditorChrome(canvas);
        drawEventStatus(canvas);
    }

    private void drawBackdrop(Canvas canvas) {
        Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        grid.setColor(Color.argb(editMode ? 35 : 16, 110, 160, 210));
        grid.setStrokeWidth(dp(1));
        int step = Math.round(dp(48));
        for (int x = 0; x < getWidth(); x += step) canvas.drawLine(x, 0, x, getHeight(), grid);
        for (int y = 0; y < getHeight(); y += step) canvas.drawLine(0, y, getWidth(), y, grid);

        small.setTextSize(dp(12));
        small.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(editMode ? "EDITOR DE CONTROLES" : "VISTA DE JUEGO · TOUCH", dp(16), dp(24), small);
        small.setTextAlign(Paint.Align.CENTER);
    }

    private void drawControl(Canvas canvas, Control c) {
        RectF r = rectFor(c);
        int alpha = Math.max(35, Math.min(255, (int) (255f * c.opacity)));
        boolean active = c.pressed || c.toggled;
        fill.setColor(active ? Color.argb(Math.min(230, alpha + 35), 70, 165, 240)
                : Color.argb(alpha, 32, 52, 76));

        float radius = c.type.equals("joystick") ? r.width() / 2f : dp(14);
        if (c.type.equals("joystick")) {
            canvas.drawCircle(r.centerX(), r.centerY(), r.width() / 2f, fill);
            canvas.drawCircle(r.centerX(), r.centerY(), r.width() / 2f, border);
            float knobR = r.width() * 0.21f;
            float max = r.width() * 0.28f;
            canvas.drawCircle(r.centerX() + joystickX * max, r.centerY() + joystickY * max, knobR, fill);
            canvas.drawCircle(r.centerX() + joystickX * max, r.centerY() + joystickY * max, knobR, border);
        } else {
            canvas.drawRoundRect(r, radius, radius, fill);
            canvas.drawRoundRect(r, radius, radius, border);
        }

        label.setTextSize(Math.max(dp(10), Math.min(dp(15), r.height() * 0.27f)));
        canvas.drawText(c.label, r.centerX(), r.centerY() - (label.ascent() + label.descent()) / 2f, label);

        if (editMode && c == selected) {
            RectF s = new RectF(r.left - dp(5), r.top - dp(5), r.right + dp(5), r.bottom + dp(5));
            canvas.drawRoundRect(s, radius + dp(5), radius + dp(5), accent);
        }
    }

    private void drawEditorChrome(Canvas canvas) {
        float h = dp(44);
        drawTool(canvas, new RectF(dp(8), dp(34), dp(68), dp(34) + h), "←");

        if (!editMode) {
            drawTool(canvas, new RectF(getWidth() - dp(82), dp(34), getWidth() - dp(8), dp(34) + h), "EDITAR");
            return;
        }

        float right = getWidth() - dp(8);
        float w = dp(58);
        drawTool(canvas, new RectF(right - w, dp(34), right, dp(34) + h), "✓");
        right -= w + dp(6);
        drawTool(canvas, new RectF(right - w, dp(34), right, dp(34) + h), "+");
        right -= w + dp(6);
        drawTool(canvas, new RectF(right - w, dp(34), right, dp(34) + h), "−");
        right -= w + dp(6);
        drawTool(canvas, new RectF(right - w, dp(34), right, dp(34) + h), "◐");
        right -= w + dp(6);
        drawTool(canvas, new RectF(right - dp(72), dp(34), right, dp(34) + h), "RESET");

        small.setTextSize(dp(11));
        String hint = selected == null ? "Toca un control y arrástralo" : selected.label + " · arrastra para mover · +/− tamaño · ◐ opacidad";
        canvas.drawText(hint, getWidth() / 2f, dp(94), small);
    }

    private void drawTool(Canvas canvas, RectF r, String textValue) {
        fill.setColor(Color.argb(220, 24, 42, 63));
        canvas.drawRoundRect(r, dp(10), dp(10), fill);
        canvas.drawRoundRect(r, dp(10), dp(10), border);
        label.setTextSize(dp(11));
        canvas.drawText(textValue, r.centerX(), r.centerY() - (label.ascent() + label.descent()) / 2f, label);
    }

    private void drawEventStatus(Canvas canvas) {
        if (editMode) return;
        RectF r = new RectF(getWidth() * 0.28f, dp(18), getWidth() * 0.72f, dp(55));
        fill.setColor(Color.argb(145, 13, 26, 40));
        canvas.drawRoundRect(r, dp(12), dp(12), fill);
        small.setTextSize(dp(10));
        canvas.drawText(eventText, r.centerX(), r.centerY() - (small.ascent() + small.descent()) / 2f, small);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        final int action = e.getActionMasked();
        final int index = e.getActionIndex();
        final int pointerId = e.getPointerId(index);
        final float x = e.getX(index);
        final float y = e.getY(index);

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (handleToolDown(x, y)) return true;
            if (editMode) {
                Control hit = hitControl(x, y);
                if (hit != null) {
                    selected = hit;
                    editPointer = pointerId;
                    invalidate();
                }
                return true;
            }

            Control hit = hitControl(x, y);
            if (hit != null) {
                pointerControls.put(pointerId, hit);
                if (hit.type.equals("joystick")) {
                    joystickPointer = pointerId;
                    updateJoystick(hit, x, y);
                } else {
                    press(hit, true);
                }
            } else if (lookPointer == -1) {
                lookPointer = pointerId;
                lastLookX = x;
                lastLookY = y;
            }
            invalidate();
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < e.getPointerCount(); i++) {
                int id = e.getPointerId(i);
                float px = e.getX(i);
                float py = e.getY(i);
                if (editMode && id == editPointer && selected != null) {
                    selected.x = clamp(px / Math.max(1f, getWidth()), 0.03f, 0.97f);
                    selected.y = clamp(py / Math.max(1f, getHeight()), 0.08f, 0.96f);
                    invalidate();
                    continue;
                }
                Control c = pointerControls.get(id);
                if (c != null && c.type.equals("joystick")) {
                    updateJoystick(c, px, py);
                } else if (!editMode && id == lookPointer) {
                    float dx = px - lastLookX;
                    float dy = py - lastLookY;
                    lastLookX = px;
                    lastLookY = py;
                    if (Math.abs(dx) + Math.abs(dy) > 0.5f) inputBridge.look(dx, dy);
                }
            }
            invalidate();
            return true;
        }

        if (action == MotionEvent.ACTION_CANCEL) {
            editPointer = -1;
            joystickPointer = -1;
            lookPointer = -1;
            joystickX = joystickY = 0f;
            pointerControls.clear();
            for (Control control : controls) control.pressed = false;
            inputBridge.releaseAll();
            saveLayout();
            invalidate();
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            if (editMode && pointerId == editPointer) {
                editPointer = -1;
                saveLayout();
                return true;
            }

            Control c = pointerControls.remove(pointerId);
            if (c != null) {
                if (c.type.equals("joystick")) {
                    joystickPointer = -1;
                    joystickX = joystickY = 0f;
                    inputBridge.move(0f, 0f);
                } else {
                    press(c, false);
                }
            }
            if (pointerId == lookPointer) lookPointer = -1;
            invalidate();
            return true;
        }
        return true;
    }

    private boolean handleToolDown(float x, float y) {
        float top = dp(34), bottom = top + dp(44);
        if (y < top || y > bottom) return false;

        if (x >= dp(8) && x <= dp(68)) {
            inputBridge.releaseAll();
            saveLayout();
            if (exitListener != null) exitListener.run();
            return true;
        }

        if (!editMode) {
            if (x >= getWidth() - dp(82)) {
                inputBridge.releaseAll();
                editMode = true;
                eventText = "EDITOR";
                invalidate();
                return true;
            }
            return false;
        }

        float right = getWidth() - dp(8);
        float w = dp(58);
        RectF done = new RectF(right - w, top, right, bottom);
        right -= w + dp(6);
        RectF plus = new RectF(right - w, top, right, bottom);
        right -= w + dp(6);
        RectF minus = new RectF(right - w, top, right, bottom);
        right -= w + dp(6);
        RectF opacity = new RectF(right - w, top, right, bottom);
        right -= w + dp(6);
        RectF reset = new RectF(right - dp(72), top, right, bottom);

        if (done.contains(x, y)) {
            editMode = false;
            selected = null;
            saveLayout();
            eventText = inputBridge.getLastDescription();
            invalidate();
            return true;
        }
        if (plus.contains(x, y) && selected != null) {
            selected.sizeDp = Math.min(132f, selected.sizeDp + 8f);
            saveLayout(); invalidate(); return true;
        }
        if (minus.contains(x, y) && selected != null) {
            selected.sizeDp = Math.max(34f, selected.sizeDp - 8f);
            saveLayout(); invalidate(); return true;
        }
        if (opacity.contains(x, y) && selected != null) {
            if (selected.opacity > 0.80f) selected.opacity = 0.65f;
            else if (selected.opacity > 0.50f) selected.opacity = 0.40f;
            else selected.opacity = 0.92f;
            saveLayout(); invalidate(); return true;
        }
        if (reset.contains(x, y)) {
            resetDefaults(true);
            invalidate();
            return true;
        }
        return false;
    }

    private void press(Control c, boolean down) {
        if (c.toggle && !down) return;
        if (c.toggle && down) {
            c.toggled = !c.toggled;
            c.pressed = false;
            inputBridge.key(c.key, c.toggled);
        } else {
            c.pressed = down;
            inputBridge.key(c.key, down);
        }
    }

    private void updateJoystick(Control c, float x, float y) {
        RectF r = rectFor(c);
        float radius = r.width() / 2f;
        float dx = (x - r.centerX()) / radius;
        float dy = (y - r.centerY()) / radius;
        float mag = (float) Math.sqrt(dx * dx + dy * dy);
        if (mag > 1f) { dx /= mag; dy /= mag; }
        if (mag < 0.18f) { dx = 0f; dy = 0f; }
        joystickX = dx;
        joystickY = dy;
        inputBridge.move(dx, dy);
    }

    private Control hitControl(float x, float y) {
        for (int i = controls.size() - 1; i >= 0; i--) {
            Control c = controls.get(i);
            RectF r = rectFor(c);
            if (c.type.equals("joystick")) {
                float dx = x - r.centerX(), dy = y - r.centerY();
                if (dx * dx + dy * dy <= (r.width() * r.width() / 4f)) return c;
            } else if (r.contains(x, y)) return c;
        }
        return null;
    }

    private RectF rectFor(Control c) {
        float px = c.x * getWidth();
        float py = c.y * getHeight();
        float base = dp(c.sizeDp);
        float width = c.type.equals("wide") ? base * 1.55f : base;
        float height = c.type.equals("wide") ? base * 0.68f : base;
        return new RectF(px - width / 2f, py - height / 2f, px + width / 2f, py + height / 2f);
    }

    private boolean loadLayout() {
        String raw = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAYOUT, null);
        if (raw == null || raw.isEmpty()) return false;
        try {
            JSONArray arr = new JSONArray(raw);
            controls.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Control c = new Control();
                c.id = o.getString("id");
                c.label = o.getString("label");
                c.key = o.getString("key");
                c.type = o.getString("type");
                c.x = (float) o.getDouble("x");
                c.y = (float) o.getDouble("y");
                c.sizeDp = (float) o.getDouble("sizeDp");
                c.opacity = (float) o.optDouble("opacity", 0.72);
                c.toggle = o.optBoolean("toggle", false);
                controls.add(c);
            }
            return !controls.isEmpty();
        } catch (Exception ignored) {
            controls.clear();
            return false;
        }
    }

    private void saveLayout() {
        try {
            JSONArray arr = new JSONArray();
            for (Control c : controls) {
                JSONObject o = new JSONObject();
                o.put("id", c.id);
                o.put("label", c.label);
                o.put("key", c.key);
                o.put("type", c.type);
                o.put("x", c.x);
                o.put("y", c.y);
                o.put("sizeDp", c.sizeDp);
                o.put("opacity", c.opacity);
                o.put("toggle", c.toggle);
                arr.put(o);
            }
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_LAYOUT, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void resetDefaults(boolean persist) {
        controls.clear();
        add("move", "MOVE", "WASD", "joystick", .15f, .72f, 116, .52f, false);
        add("jump", "SALTAR", "SPACE", "button", .86f, .70f, 76, .70f, false);
        add("attack", "ATACAR", "MOUSE1", "button", .90f, .48f, 68, .66f, false);
        add("use", "USAR", "MOUSE2", "button", .75f, .80f, 66, .64f, false);
        add("inv", "INV", "E", "button", .95f, .82f, 54, .60f, false);
        add("crouch", "AGACHAR", "CTRL", "wide", .14f, .46f, 68, .56f, true);
        add("sprint", "CORRER", "SHIFT", "wide", .14f, .31f, 68, .56f, true);
        add("esc", "ESC", "ESC", "wide", .07f, .14f, 55, .58f, false);
        add("chat", "CHAT", "T", "wide", .18f, .14f, 55, .58f, false);
        for (int i = 0; i < 9; i++) {
            float x = .34f + i * .041f;
            add("slot" + (i + 1), String.valueOf(i + 1), String.valueOf(i + 1), "button", x, .91f, 38, .54f, false);
        }
        inputBridge.releaseAll();
        if (persist) saveLayout();
    }

    private void add(String id, String text, String key, String type, float x, float y, float sizeDp, float opacity, boolean toggle) {
        Control c = new Control();
        c.id = id; c.label = text; c.key = key; c.type = type;
        c.x = x; c.y = y; c.sizeDp = sizeDp; c.opacity = opacity; c.toggle = toggle;
        controls.add(c);
    }

    private float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    private static final class Control {
        String id;
        String label;
        String key;
        String type;
        float x;
        float y;
        float sizeDp;
        float opacity;
        boolean toggle;
        boolean toggled;
        boolean pressed;
    }
}
