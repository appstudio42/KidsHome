package io.github.marcelsavegnago.kidshome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/** Lightweight bar chart drawn with Canvas — no chart library. */
public class SimpleBarChartView extends View {
    public static final class Bar {
        public final String label;
        public final float value;

        public Bar(String label, float value) {
            this.label = label == null ? "" : label;
            this.value = Math.max(0f, value);
        }
    }

    private final List<Bar> bars = new ArrayList<>();
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public SimpleBarChartView(Context context) {
        super(context);
        init();
    }

    public SimpleBarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SimpleBarChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        barPaint.setColor(ContextCompat.getColor(getContext(), R.color.md_primary));
        labelPaint.setColor(ContextCompat.getColor(getContext(), R.color.md_on_surface_variant));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(sp(11f));
        axisPaint.setColor(ContextCompat.getColor(getContext(), R.color.md_outline_variant));
        axisPaint.setStrokeWidth(dp(1f));
        setMinimumHeight((int) dp(160f));
    }

    public void setBars(List<Bar> next) {
        bars.clear();
        if (next != null) {
            bars.addAll(next);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        float padL = dp(8f);
        float padR = dp(8f);
        float padT = dp(12f);
        float padB = dp(28f);
        float chartH = h - padT - padB;
        float chartW = w - padL - padR;
        canvas.drawLine(padL, h - padB, w - padR, h - padB, axisPaint);

        if (bars.isEmpty() || chartW <= 0f || chartH <= 0f) {
            return;
        }
        float max = 0f;
        for (Bar bar : bars) {
            max = Math.max(max, bar.value);
        }
        if (max <= 0f) {
            max = 1f;
        }
        float slot = chartW / bars.size();
        float barWidth = Math.max(dp(8f), slot * 0.55f);
        for (int i = 0; i < bars.size(); i++) {
            Bar bar = bars.get(i);
            float left = padL + i * slot + (slot - barWidth) / 2f;
            float barH = (bar.value / max) * chartH;
            float top = padT + (chartH - barH);
            rect.set(left, top, left + barWidth, padT + chartH);
            canvas.drawRoundRect(rect, dp(6f), dp(6f), barPaint);
            String label = bar.label;
            if (label.length() > 3) {
                label = label.substring(0, 3);
            }
            canvas.drawText(label, left + barWidth / 2f, h - dp(8f), labelPaint);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
