package com.termux.app.call;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

public class TermuxRadarView extends View {

    private final Paint armorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring1Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring3Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coreBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coreBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF arcRect1 = new RectF();
    private final RectF arcRect2 = new RectF();
    private final RectF sweepRect = new RectF();

    private int colorMain = Color.parseColor("#059669");
    private int colorGlow = Color.parseColor("#00E676");
    private int colorAlt = Color.parseColor("#10B981");
    private int colorCoreBg = Color.parseColor("#042114");

    private String initials = "ID";
    private float tickTime = 0f;
    private float ring1Angle = 0f;
    private float ring2Angle = 0f;
    private float ring3Angle = 0f;

    public TermuxRadarView(Context context) {
        super(context);
        initPaints();
    }

    private void initPaints() {
        armorPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        armorPaint.setColor(Color.parseColor("#090507"));
        armorPaint.setStrokeWidth(4f);

        tickPaint.setStyle(Paint.Style.STROKE);

        ring1Paint.setStyle(Paint.Style.STROKE);
        ring1Paint.setStrokeWidth(6.5f);
        ring1Paint.setStrokeCap(Paint.Cap.ROUND);

        ring2Paint.setStyle(Paint.Style.STROKE);
        ring2Paint.setStrokeWidth(8.5f);
        ring2Paint.setStrokeCap(Paint.Cap.BUTT);

        ring3Paint.setStyle(Paint.Style.STROKE);
        ring3Paint.setStrokeWidth(4.5f);
        ring3Paint.setPathEffect(new DashPathEffect(new float[]{14f, 9f, 5f, 9f}, 0));

        sweepPaint.setStyle(Paint.Style.FILL);

        coreBgPaint.setStyle(Paint.Style.FILL);

        coreBorderPaint.setStyle(Paint.Style.STROKE);
        coreBorderPaint.setStrokeWidth(5f);

        textPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);

        hudPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        hudPaint.setStyle(Paint.Style.STROKE);
        hudPaint.setStrokeWidth(3.5f);
    }

    public void setInitials(String init) {
        if (init != null && !init.trim().isEmpty()) {
            this.initials = init.trim().toUpperCase();
        } else {
            this.initials = "ID";
        }
        invalidate();
    }

    public void setMode(String mode) {
        if ("incoming".equalsIgnoreCase(mode)) {
            colorMain = Color.parseColor("#B91C1C");
            colorGlow = Color.parseColor("#FF1744");
            colorAlt = Color.parseColor("#F43F5E");
            colorCoreBg = Color.parseColor("#26050B");
        } else if ("active".equalsIgnoreCase(mode)) {
            colorMain = Color.parseColor("#7C3AED");
            colorGlow = Color.parseColor("#00E5FF");
            colorAlt = Color.parseColor("#A855F7");
            colorCoreBg = Color.parseColor("#130726");
        } else if ("hold".equalsIgnoreCase(mode)) {
            colorMain = Color.parseColor("#D97706");
            colorGlow = Color.parseColor("#FFD600");
            colorAlt = Color.parseColor("#F59E0B");
            colorCoreBg = Color.parseColor("#1F1704");
        } else {
            // outgoing default
            colorMain = Color.parseColor("#059669");
            colorGlow = Color.parseColor("#00E676");
            colorAlt = Color.parseColor("#10B981");
            colorCoreBg = Color.parseColor("#042114");
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

        // Update Bi-Directional Hacker Glitch Angles
        tickTime += 0.045f;

        // Ring 1 (Outer): Goes Right -> Reverses Left -> Goes Right (Oscillating Hacker Wave)
        float osc1 = (float) (Math.sin(tickTime * 1.9f) * 75f + Math.cos(tickTime * 0.8f) * 40f);
        ring1Angle = (tickTime * 32f) + osc1;

        // Ring 2 (Middle): Spins in Opposite Direction (Counter-Clockwise + Reverse Back-Kick)
        float osc2 = (float) (Math.sin(tickTime * 2.6f) * 65f);
        ring2Angle = -(tickTime * 48f) - osc2;

        // Ring 3 (Inner): Rapid Glitch Step-Snap
        float glitchSnap = ((int) (tickTime * 8f) % 5 == 0) ? -38f : 22f;
        ring3Angle += (float) (Math.cos(tickTime * 3.4f) * 9.5f) + (glitchSnap * 0.15f);

        // 0. Outer Dark Armor Circle & Degree Ticks
        canvas.drawCircle(cx, cy, maxR, armorPaint);
        for (int deg = 0; deg < 360; deg += 12) {
            double rad = Math.toRadians(deg);
            boolean major = (deg % 36 == 0);
            float innerTick = major ? maxR * 0.89f : maxR * 0.93f;
            tickPaint.setStrokeWidth(major ? 4f : 2f);
            tickPaint.setColor(major ? colorGlow : colorMain);
            float x1 = cx + (float) (innerTick * Math.cos(rad));
            float y1 = cy + (float) (innerTick * Math.sin(rad));
            float x2 = cx + (float) ((maxR * 0.99f) * Math.cos(rad));
            float y2 = cy + (float) ((maxR * 0.99f) * Math.sin(rad));
            canvas.drawLine(x1, y1, x2, y2, tickPaint);
        }

        // 1. RING 1 (Outer 4-Segment Arc - Oscillates Right/Left/Right)
        float r1 = maxR * 0.82f;
        arcRect1.set(cx - r1, cy - r1, cx + r1, cy + r1);
        ring1Paint.setColor(colorGlow);
        for (int start : new int[]{10, 100, 190, 280}) {
            canvas.drawArc(arcRect1, start + ring1Angle, 52f, false, ring1Paint);
        }

        // 2. RING 2 (Middle 3-Segment Heavy Arc - Opposite Spin)
        float r2 = maxR * 0.69f;
        arcRect2.set(cx - r2, cy - r2, cx + r2, cy + r2);
        ring2Paint.setColor(colorMain);
        for (int start : new int[]{35, 155, 275}) {
            canvas.drawArc(arcRect2, start + ring2Angle, 72f, false, ring2Paint);
        }

        // 3. Radar Sweep Wedges (Opposite Rotating Sectors)
        float rSweep = maxR * 0.78f;
        sweepRect.set(cx - rSweep, cy - rSweep, cx + rSweep, cy + rSweep);
        sweepPaint.setColor(colorGlow);
        sweepPaint.setAlpha(40);
        canvas.drawArc(sweepRect, ring1Angle, 55f, true, sweepPaint);
        sweepPaint.setColor(colorMain);
        sweepPaint.setAlpha(35);
        canvas.drawArc(sweepRect, ring2Angle + 180f, 45f, true, sweepPaint);

        // 4. RING 3 (Inner Glitch Dashed Circle)
        float r3 = maxR * 0.56f;
        canvas.save();
        canvas.rotate(ring3Angle, cx, cy);
        ring3Paint.setColor(colorAlt);
        canvas.drawCircle(cx, cy, r3, ring3Paint);
        canvas.restore();

        // 5. Center Core + Caller Initials
        float rCore = maxR * 0.44f;
        coreBgPaint.setColor(colorCoreBg);
        coreBorderPaint.setColor(colorMain);
        canvas.drawCircle(cx, cy, rCore, coreBgPaint);
        canvas.drawCircle(cx, cy, rCore, coreBorderPaint);

        textPaint.setColor(colorGlow);
        textPaint.setTextSize(rCore * 0.72f);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(initials, cx, textY, textPaint);

        // 6. Corner HUD Brackets
        hudPaint.setColor(colorMain);
        float bDist = maxR * 0.98f;
        float bLen = maxR * 0.16f;
        // Top-Left
        canvas.drawLine(cx - bDist, cy - bDist, cx - bDist + bLen, cy - bDist, hudPaint);
        canvas.drawLine(cx - bDist, cy - bDist, cx - bDist, cy - bDist + bLen, hudPaint);
        // Top-Right
        canvas.drawLine(cx + bDist, cy - bDist, cx + bDist - bLen, cy - bDist, hudPaint);
        canvas.drawLine(cx + bDist, cy - bDist, cx + bDist, cy - bDist + bLen, hudPaint);
        // Bottom-Left
        canvas.drawLine(cx - bDist, cy + bDist, cx - bDist + bLen, cy + bDist, hudPaint);
        canvas.drawLine(cx - bDist, cy + bDist, cx - bDist, cy + bDist - bLen, hudPaint);
        // Bottom-Right
        canvas.drawLine(cx + bDist, cy + bDist, cx + bDist - bLen, cy + bDist, hudPaint);
        canvas.drawLine(cx + bDist, cy + bDist, cx + bDist, cy + bDist - bLen, hudPaint);

        // Keep 60 FPS animation running
        postInvalidateOnAnimation();
    }
}
