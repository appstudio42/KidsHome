package io.github.marcelsavegnago.kidshome;

import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

/**
 * Wires the numeric PIN keypad + dots to a hidden EditText.
 */
public final class PinKeypadController {
    public interface Listener {
        void onPinChanged(String pin);
    }

    private final EditText input;
    private final LinearLayout dots;
    private final int maxLength;
    private Listener listener;

    public PinKeypadController(View root, EditText input, LinearLayout dots, int maxLength) {
        this.input = input;
        this.dots = dots;
        this.maxLength = Math.max(4, Math.min(8, maxLength));
        bindKey(root, R.id.pinKey0, "0");
        bindKey(root, R.id.pinKey1, "1");
        bindKey(root, R.id.pinKey2, "2");
        bindKey(root, R.id.pinKey3, "3");
        bindKey(root, R.id.pinKey4, "4");
        bindKey(root, R.id.pinKey5, "5");
        bindKey(root, R.id.pinKey6, "6");
        bindKey(root, R.id.pinKey7, "7");
        bindKey(root, R.id.pinKey8, "8");
        bindKey(root, R.id.pinKey9, "9");
        View back = root.findViewById(R.id.pinKeyBack);
        if (back != null) {
            back.setOnClickListener(v -> {
                HapticHelper.tick(v);
                CharSequence text = input.getText();
                if (text != null && text.length() > 0) {
                    input.setText(text.subSequence(0, text.length() - 1));
                    input.setSelection(input.length());
                    refreshDots();
                    notifyChanged();
                }
            });
        }
        rebuildDots(4);
        refreshDots();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void clear() {
        input.setText("");
        rebuildDots(4);
        refreshDots();
        notifyChanged();
    }

    public String getPin() {
        return input.getText() != null ? input.getText().toString() : "";
    }

    private void bindKey(View root, int id, String digit) {
        View key = root.findViewById(id);
        if (key == null) {
            return;
        }
        key.setOnClickListener(v -> {
            HapticHelper.tick(v);
            String current = getPin();
            if (current.length() >= maxLength) {
                return;
            }
            input.setText(current + digit);
            input.setSelection(input.length());
            int shown = Math.max(4, Math.min(maxLength, getPin().length()));
            rebuildDots(shown);
            refreshDots();
            notifyChanged();
        });
    }

    private void rebuildDots(int count) {
        if (dots == null) {
            return;
        }
        dots.removeAllViews();
        int size = (int) (14 * dots.getResources().getDisplayMetrics().density);
        int gap = (int) (10 * dots.getResources().getDisplayMetrics().density);
        for (int i = 0; i < count; i++) {
            ImageView dot = new ImageView(dots.getContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            if (i > 0) {
                lp.setMarginStart(gap);
            }
            dot.setLayoutParams(lp);
            dot.setImageResource(R.drawable.bg_pin_dot_empty);
            dots.addView(dot);
        }
    }

    private void refreshDots() {
        if (dots == null) {
            return;
        }
        int filled = getPin().length();
        if (filled > dots.getChildCount()) {
            rebuildDots(Math.min(maxLength, filled));
        }
        for (int i = 0; i < dots.getChildCount(); i++) {
            ImageView dot = (ImageView) dots.getChildAt(i);
            dot.setImageResource(i < filled ? R.drawable.bg_pin_dot_filled : R.drawable.bg_pin_dot_empty);
        }
    }

    private void notifyChanged() {
        if (listener != null) {
            listener.onPinChanged(getPin());
        }
    }
}
