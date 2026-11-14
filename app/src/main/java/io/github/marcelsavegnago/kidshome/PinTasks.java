package io.github.marcelsavegnago.kidshome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs PIN derivation off the main thread.
 *
 * <p>{@link PinHasher#DEFAULT_ITERATIONS} PBKDF2-HMAC-SHA1 is a long stretch
 * of CPU. The escape screen, PIN dialogs, and onboarding wait on the result,
 * so the work happens here and the same success or failure path continues on
 * the main thread. One worker keeps a save and a check from interleaving.
 */
public final class PinTasks {
    private static final String TAG = "KidsHome.Pin";

    public interface Callback {
        void onComplete(boolean success);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "kidshome-pin");
        thread.setPriority(Thread.NORM_PRIORITY);
        return thread;
    });

    private PinTasks() {
    }

    public static void verifyKidPin(Context context, String pin, Callback callback) {
        Context app = context.getApplicationContext();
        String attempt = pin == null ? "" : pin;
        EXECUTOR.execute(() -> {
            boolean ok = false;
            try {
                ok = Prefs.verifyKidPin(app, attempt);
            } catch (RuntimeException e) {
                Log.e(TAG, "PIN verification failed", e);
            }
            deliver(callback, ok);
        });
    }

    public static void setKidPin(Context context, String pin, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            boolean ok = false;
            try {
                Prefs.setKidPin(app, pin);
                ok = true;
            } catch (RuntimeException e) {
                Log.e(TAG, "PIN save failed", e);
            }
            deliver(callback, ok);
        });
    }

    private static void deliver(Callback callback, boolean success) {
        if (callback == null) {
            return;
        }
        new Handler(Looper.getMainLooper()).post(() -> callback.onComplete(success));
    }
}
