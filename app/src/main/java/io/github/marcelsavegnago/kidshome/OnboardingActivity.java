package io.github.marcelsavegnago.kidshome;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * First-run wizard: welcome → PIN → child profile → apps → screen time → mode checklist → done.
 */
public class OnboardingActivity extends AppCompatActivity {
    private static final int STEPS = 6;

    private LinearProgressIndicator progress;
    private TextView stepLabel;
    private FrameLayout container;
    private MaterialButton backButton;
    private MaterialButton nextButton;
    private int step;

    private String pendingPin = "";
    private String pendingName = "";
    private String pendingAvatar = ChildAvatars.DEFAULT;
    private int pendingLimitMinutes = 60;
    private boolean finishing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs.migrateIfNeeded(this);
        if (Prefs.isOnboardingDone(this)) {
            goMain();
            return;
        }
        setContentView(R.layout.activity_onboarding);
        progress = findViewById(R.id.onboardingProgress);
        stepLabel = findViewById(R.id.onboardingStepLabel);
        container = findViewById(R.id.onboardingContainer);
        backButton = findViewById(R.id.onboardingBack);
        nextButton = findViewById(R.id.onboardingNext);

        backButton.setOnClickListener(v -> {
            if (step <= 0) {
                finish();
            } else {
                step--;
                showStep();
            }
        });
        nextButton.setOnClickListener(v -> onNext());
        showStep();
    }

    private void onNext() {
        if (!validateCurrentStep()) {
            return;
        }
        if (step >= STEPS - 1) {
            finishOnboarding();
            return;
        }
        step++;
        showStep();
    }

    private boolean validateCurrentStep() {
        if (step == 1) {
            View pin1 = container.findViewWithTag("pin1");
            View pin2 = container.findViewWithTag("pin2");
            String a = pin1 instanceof EditText && ((EditText) pin1).getText() != null
                    ? ((EditText) pin1).getText().toString() : "";
            String b = pin2 instanceof EditText && ((EditText) pin2).getText() != null
                    ? ((EditText) pin2).getText().toString() : "";
            if (!PinHasher.isValidPinFormat(a)) {
                Toast.makeText(this, R.string.pin_invalid, Toast.LENGTH_LONG).show();
                return false;
            }
            if (!TextUtils.equals(a, b)) {
                Toast.makeText(this, R.string.pin_mismatch, Toast.LENGTH_SHORT).show();
                return false;
            }
            pendingPin = a;
            return true;
        }
        if (step == 2) {
            View nameView = container.findViewWithTag("childName");
            if (nameView instanceof EditText && ((EditText) nameView).getText() != null) {
                pendingName = ((EditText) nameView).getText().toString().trim();
            }
            return true;
        }
        if (step == 4) {
            View slider = container.findViewWithTag("limitSlider");
            if (slider instanceof Slider) {
                pendingLimitMinutes = Math.round(((Slider) slider).getValue());
            }
            return true;
        }
        return true;
    }

    private void finishOnboarding() {
        if (finishing) {
            return;
        }
        finishing = true;
        nextButton.setEnabled(false);
        backButton.setEnabled(false);
        if (pendingPin.isEmpty()) {
            commitOnboarding();
            return;
        }
        nextButton.setText(R.string.pin_checking);
        PinTasks.setKidPin(this, pendingPin, saved -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (!saved) {
                finishing = false;
                nextButton.setEnabled(true);
                backButton.setEnabled(true);
                nextButton.setText(R.string.done);
                return;
            }
            commitOnboarding();
        });
    }

    private void commitOnboarding() {
        Prefs.setChildProfile(this, pendingName, pendingAvatar);
        Prefs.setDailyLimitMinutes(this, pendingLimitMinutes);
        Prefs.setScreenTimeEnabled(this, true);
        Prefs.ensureDefaultSiteSuggestions(this);
        Prefs.setOnboardingDone(this, true);
        SessionAuth.markUnlocked();
        Toast.makeText(this, R.string.onboarding_done_toast, Toast.LENGTH_LONG).show();
        startActivity(new Intent(this, SetupActivity.class));
        finish();
    }

    private void goMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void showStep() {
        progress.setProgress(step + 1);
        stepLabel.setText(getString(R.string.onboarding_step_fmt, step + 1, STEPS));
        backButton.setVisibility(step == 0 ? View.INVISIBLE : View.VISIBLE);
        nextButton.setText(step == STEPS - 1 ? R.string.done : R.string.next);
        container.removeAllViews();

        switch (step) {
            case 0:
                container.addView(textPage(R.string.onboarding_welcome_title, R.string.onboarding_welcome_body));
                break;
            case 1:
                container.addView(pinPage());
                break;
            case 2:
                container.addView(profilePage());
                break;
            case 3:
                container.addView(appsPage());
                break;
            case 4:
                container.addView(timePage());
                break;
            default:
                container.addView(textPage(R.string.onboarding_setup_title, R.string.onboarding_setup_body));
                break;
        }
        MotionHelper.fadeInUp(container, this);
    }

    private View textPage(int titleRes, int bodyRes) {
        LinearLayout layout = column();
        TextView title = new TextView(this);
        title.setText(titleRes);
        title.setTextAppearance(this, R.style.TextAppearance_KidsHome_Display);
        TextView body = new TextView(this);
        body.setText(bodyRes);
        body.setTextAppearance(this, R.style.TextAppearance_KidsHome_Body);
        body.setPadding(0, dp(12), 0, 0);
        layout.addView(title);
        layout.addView(body);
        return layout;
    }

    private View pinPage() {
        LinearLayout layout = column();
        TextView title = new TextView(this);
        title.setText(R.string.pin_create_title);
        title.setTextAppearance(this, R.style.TextAppearance_KidsHome_Title);
        layout.addView(title);
        TextView body = new TextView(this);
        body.setText(R.string.pin_create_message);
        body.setPadding(0, dp(8), 0, dp(12));
        layout.addView(body);
        layout.addView(outlinedField("pin1", R.string.pin_new_hint, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD));
        layout.addView(outlinedField("pin2", R.string.pin_confirm_hint, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD));
        return layout;
    }

    private View profilePage() {
        LinearLayout layout = column();
        TextView title = new TextView(this);
        title.setText(R.string.section_child_profile);
        title.setTextAppearance(this, R.style.TextAppearance_KidsHome_Title);
        layout.addView(title);
        layout.addView(outlinedField("childName", R.string.child_name_hint, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME));
        TextView avatar = new TextView(this);
        avatar.setTag("avatarPreview");
        avatar.setText(pendingAvatar);
        avatar.setTextSize(36f);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackgroundResource(R.drawable.bg_avatar_circle);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(64), dp(64));
        lp.topMargin = dp(16);
        avatar.setLayoutParams(lp);
        layout.addView(avatar);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(12), 0, 0);
        for (int i = 0; i < Math.min(ChildAvatars.ALL.length, 8); i++) {
            final String emoji = ChildAvatars.ALL[i];
            MaterialButton btn = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setText(emoji);
            btn.setMinHeight(dp(48));
            btn.setMinWidth(dp(48));
            btn.setPadding(0, 0, 0, 0);
            btn.setOnClickListener(v -> {
                pendingAvatar = emoji;
                avatar.setText(emoji);
            });
            row.addView(btn);
        }
        layout.addView(row);
        return layout;
    }

    private View appsPage() {
        LinearLayout layout = column();
        TextView title = new TextView(this);
        title.setText(R.string.manage_allowed_apps);
        title.setTextAppearance(this, R.style.TextAppearance_KidsHome_Title);
        layout.addView(title);
        TextView body = new TextView(this);
        body.setText(R.string.onboarding_apps_body);
        body.setPadding(0, dp(8), 0, dp(16));
        layout.addView(body);
        MaterialButton pick = new MaterialButton(this);
        pick.setText(R.string.manage_allowed_apps);
        pick.setMinHeight(dp(48));
        pick.setOnClickListener(v -> startActivity(new Intent(this, AllowedAppsActivity.class)));
        layout.addView(pick);
        return layout;
    }

    private View timePage() {
        LinearLayout layout = column();
        TextView title = new TextView(this);
        title.setText(R.string.screen_time_section);
        title.setTextAppearance(this, R.style.TextAppearance_KidsHome_Title);
        layout.addView(title);
        TextView label = new TextView(this);
        label.setTag("limitLabel");
        label.setText(getString(R.string.daily_limit_slider_fmt, pendingLimitMinutes));
        label.setPadding(0, dp(12), 0, 0);
        layout.addView(label);
        Slider slider = new Slider(this);
        slider.setTag("limitSlider");
        slider.setValueFrom(15f);
        slider.setValueTo(240f);
        slider.setStepSize(15f);
        slider.setValue(pendingLimitMinutes);
        slider.addOnChangeListener((s, value, fromUser) ->
                label.setText(getString(R.string.daily_limit_slider_fmt, Math.round(value))));
        layout.addView(slider);
        return layout;
    }

    private TextInputLayout outlinedField(String tag, int hintRes, int inputType) {
        TextInputLayout til = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        til.setHint(hintRes);
        TextInputEditText edit = new TextInputEditText(til.getContext());
        edit.setTag(tag);
        edit.setInputType(inputType);
        edit.setMaxLines(1);
        if ("childName".equals(tag) && pendingName != null) {
            edit.setText(pendingName);
        }
        til.addView(edit);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        til.setLayoutParams(lp);
        return til;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return layout;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
