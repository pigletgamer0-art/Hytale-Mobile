package com.unofficial.hytalemobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public final class TouchOverlayView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TouchOverlayView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(10, 14, 22));
        fill.setColor(Color.argb(85, 210, 230, 255));
        line.setColor(Color.argb(180, 220, 235, 255));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(4f);
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float r = Math.min(w, h) * 0.12f;

        text.setTextSize(Math.max(28f, h * 0.045f));
        canvas.drawText("Vista previa de controles táctiles · toca para volver", w / 2f, h * 0.10f, text);

        float lx = w * 0.16f;
        float ly = h * 0.70f;
        canvas.drawCircle(lx, ly, r, fill);
        canvas.drawCircle(lx, ly, r, line);
        canvas.drawCircle(lx, ly, r * 0.42f, line);

        drawButton(canvas, w * 0.82f, h * 0.72f, r * 0.58f, "SALTAR");
        drawButton(canvas, w * 0.70f, h * 0.82f, r * 0.50f, "USAR");
        drawButton(canvas, w * 0.88f, h * 0.48f, r * 0.46f, "ATACAR");

        RectF bar = new RectF(w * 0.36f, h * 0.86f, w * 0.64f, h * 0.94f);
        canvas.drawRoundRect(bar, 20f, 20f, fill);
        canvas.drawRoundRect(bar, 20f, 20f, line);
        text.setTextSize(Math.max(18f, h * 0.026f));
        canvas.drawText("HOTBAR", w / 2f, h * 0.915f, text);
    }

    private void drawButton(Canvas canvas, float x, float y, float radius, String label) {
        canvas.drawCircle(x, y, radius, fill);
        canvas.drawCircle(x, y, radius, line);
        text.setTextSize(Math.max(16f, radius * 0.30f));
        canvas.drawText(label, x, y + text.getTextSize() * 0.35f, text);
    }
}
