package io.github.marcelsavegnago.kidshome;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Forces a 1:1 aspect ratio so kid-home tiles stay square-ish in a GridView. */
public class SquareFrameLayout extends FrameLayout {
    public SquareFrameLayout(@NonNull Context context) {
        super(context);
    }

    public SquareFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SquareFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Prefer width from the grid column; fall back to height when width is UNSPECIFIED.
        // Slightly taller than wide so bold labels fit under large icons (square-ish, not strip).
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int size = width > 0 ? width : height;
        int wSpec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
        int hSpec = MeasureSpec.makeMeasureSpec(Math.round(size * 1.12f), MeasureSpec.EXACTLY);
        super.onMeasure(wSpec, hSpec);
    }
}
