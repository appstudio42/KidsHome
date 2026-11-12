package io.github.marcelsavegnago.kidshome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * Circular remaining-time indicator. Progress 1 = full (green), 0 = empty (red).
 */
public class RemainingTimeRing extends View {
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private float progress = 1f;
    private float strokeWidthPx;

    public RemainingTimeRing(Context context) {
        super(context);
        init(context);
    }

    public RemainingTimeRing(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public RemainingTimeRing(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        strokeWidthPx = 6f * context.getResources().getDisplayMetrics().density;
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidthPx);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(ContextCompat.getColor(context, R.color.time_ring_track));
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidthPx);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(ContextCompat.getColor(context, R.color.time_ring_good));
    }

    /** @param value 0..1 remaining fraction */
    public void setProgress(float value) {
        progress = Math.max(0f, Math.min(1f, value));
        int colorRes;
        if (progress > 0.4f) {
            colorRes = R.color.time_ring_good;
        } else if (progress > 0.15f) {
            colorRes = R.color.time_ring_warn;
        } else {
            colorRes = R.color.time_ring_low;
        }
        progressPaint.setColor(ContextCompat.getColor(getContext(), colorRes));
        invalidate();
    }

    public float getProgress() {
        return progress;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float pad = strokeWidthPx / 2f + 1f;
        arc.set(pad, pad, getWidth() - pad, getHeight() - pad);
        canvas.drawArc(arc, -90f, 360f, false, trackPaint);
        canvas.drawArc(arc, -90f, -360f * progress, false, progressPaint);
    }
}
