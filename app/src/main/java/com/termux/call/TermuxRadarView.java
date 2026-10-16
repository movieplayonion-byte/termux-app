package com.termux.app.call;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public class TermuxRadarView extends View {

    public static final int MODE_OUTGOING = 1;
    public static final int MODE_INCOMING = 2;
    public static final int MODE_ACTIVE   = 3;
    public static final int MODE_HOLD     = 4;

    private int currentMode = MODE_OUTGOING;
    private String initials = "RS";

    private float angleCw = 0f;
    private float angleCcw = 0f;
    private float pulsePhase = 0f;

    private final Paint paintArmor = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintTicks = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintDashed = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintArcs = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintSweep = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintCoreBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintCoreBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectArc = new RectF();
    private final RectF rectSweep = new RectF();

    public TermuxRadarView(Context context) {
        super(context);
        initPaints();
    }

    private void initPaints() {
        paintArmor.setStyle(Paint.Style.STROKE);
        paintArmor.setStrokeWidth(4f);
        paintArmor.setColor(Color.parseColor("#2D1219"));

        paintTicks.setStyle(Paint.Style.STROKE);

        paintDashed.setStyle(Paint.Style.STROKE);
        paintDashed.setStrokeWidth(4.5f);
        paintDashed.setPathEffect(new DashPathEffect(new float[]{18f, 12f}, 0f));

        paintArcs.setStyle(Paint.Style.STROKE);
        paintArcs.setStrokeWidth(9f);
        paintArcs.setStrokeCap(Paint.Cap.ROUND);

        paintSweep.setStyle(Paint.Style.FILL);
        paintCoreBg.setStyle(Paint.Style.FILL);

        paintCoreBorder.setStyle(Paint.Style.STROKE);
        paintCoreBorder.setStrokeWidth(5f);

        paintText.setColor(Color.WHITE);
        paintText.setTextAlign(Paint.Align.CENTER);
        paintText.setFakeBoldText(true);
    }

    public void setMode(int mode) {
        this.currentMode = mode;
        invalidate();
    }

    public void setInitials(String init) {
        if (init != null && !init.trim().isEmpty()) {
            this.initials = init.trim().toUpperCase();
        } else {
            this.initials = "ID";
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        float cx = w / 2f;
        float cy = h / 2f;
        float maxR = Math.min(w, h) * 0.44f;

        int ringMain, ringGlow, coreBg;
        float speed;

        if (currentMode == MODE_OUTGOING) {
            ringMain = Color.parseColor("#059669");
            ringGlow = Color.parseColor("#00E676");
            coreBg   = Color.parseColor("#042114");
            speed    = 2.2f;
        } else if (currentMode == MODE_INCOMING) {
            ringMain = Color.parseColor("#B91C1C");
            ringGlow = Color.parseColor("#FF1744");
            coreBg   = Color.parseColor("#26050B");
            speed    = 3.6f;
        } else if (currentMode == MODE_HOLD) {
            ringMain = Color.parseColor("#D97706");
            ringGlow = Color.parseColor("#FFD600");
            coreBg   = Color.parseColor("#1F1704");
            speed    = 0.8f;
        } else {
            ringMain = Color.parseColor("#7C3AED");
            ringGlow = Color.parseColor("#00E5FF");
            coreBg   = Color.parseColor("#130726");
            speed    = 2.0f;
        }

        canvas.drawCircle(cx, cy, maxR, paintArmor);
        for (int deg = 0; deg < 360; deg += 15) {
            double rad = Math.toRadians(deg);
            boolean major = (deg % 45 == 0);
            float rIn = major ? maxR * 0.89f : maxR * 0.93f;
            paintTicks.setStrokeWidth(major ? 4f : 2f);
            paintTicks.setColor(major ? ringGlow : ringMain);
            float x1 = cx + (float) (rIn * Math.cos(rad));
            float y1 = cy + (float) (rIn * Math.sin(rad));
            float x2 = cx + (float) (maxR * 0.99f * Math.cos(rad));
            float y2 = cy + (float) (maxR * 0.99f * Math.sin(rad));
            canvas.drawLine(x1, y1, x2, y2, paintTicks);
        }

        canvas.save();
        canvas.rotate(angleCw, cx, cy);
        paintDashed.setColor(ringMain);
        canvas.drawCircle(cx, cy, maxR * 0.82f, paintDashed);
        canvas.restore();

        float arcR = maxR * 0.71f;
        rectArc.set(cx - arcR, cy - arcR, cx + arcR, cy + arcR);
        paintArcs.setColor(ringGlow);
        canvas.save();
        canvas.rotate(angleCcw, cx, cy);
        for (int a = 0; a < 360; a += 120) {
            canvas.drawArc(rectArc, a + 10, 68, false, paintArcs);
        }
        paintSweep.setColor(ringGlow);
        paintSweep.setAlpha(255);
        canvas.drawCircle(cx + arcR, cy, 7f, paintSweep);
        canvas.drawCircle(cx - arcR, cy, 7f, paintSweep);
        canvas.restore();

        float pulseScale = 1.0f + 0.04f * (float) Math.sin(pulsePhase);
        float coreR = maxR * 0.50f * pulseScale;
        paintCoreBg.setColor(coreBg);
        paintCoreBorder.setColor(ringMain);
        canvas.drawCircle(cx, cy, coreR, paintCoreBg);
        canvas.drawCircle(cx, cy, coreR, paintCoreBorder);

        paintText.setColor(ringGlow);
        paintText.setTextSize(maxR * 0.34f);
        Paint.FontMetrics fm = paintText.getFontMetrics();
        float textY = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(initials, cx, textY, paintText);

        angleCw = (angleCw + speed) % 360f;
        angleCcw = (angleCcw - speed * 1.35f) % 360f;
        pulsePhase += 0.12f;

        postInvalidateOnAnimation();
    }
}
