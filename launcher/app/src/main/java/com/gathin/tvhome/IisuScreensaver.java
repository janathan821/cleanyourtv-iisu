package com.gathin.tvhome;

import android.content.Context;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextClock;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import androidx.annotation.RequiresApi;

/**
 * The iiSU skin's screensaver (iisu_skin plus a theme's screensaver.gif): the animation, fitted inside the screen,
 * on the skin's dotted backdrop, with the time in the bottom-left corner. Used by ClockDream and ClockActivity in
 * place of the clock scene; without the skin or the file they keep the classic clock.
 */
final class IisuScreensaver {

    static final String FILE = "screensaver.gif";
    /** Every minute the time shifts by up to this much, so a still corner never burns in. */
    private static final float DRIFT_DP = 14f;
    private static final long DRIFT_MS = 60_000L;

    private final FrameLayout root;
    private final TextClock clock;
    /** An AnimatedImageDrawable: the scene is only ever made on Android 9 and up (see create). */
    private final Drawable animation;
    private final float density;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private final Runnable drift = new Runnable() {
        @Override
        public void run() {
            float d = DRIFT_DP * density;
            clock.animate().translationX((random.nextFloat() * 2f - 1f) * d)
                    .translationY(-random.nextFloat() * d).setDuration(2000).start();
            handler.postDelayed(this, DRIFT_MS);
        }
    };

    /** The scene, or null for the classic clock: no skin, no screensaver.gif, or a file that does not decode. */
    @RequiresApi(Build.VERSION_CODES.P)
    static IisuScreensaver create(Context ctx) {
        Config cfg = Config.load(ctx);
        if (cfg.iisuSkin.isEmpty()) return null;
        File dir = Config.dir(ctx);
        File file = dir == null ? null : new File(dir, FILE);
        if (file == null || !file.isFile()) return null;
        try {
            Drawable d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file));
            if (!(d instanceof AnimatedImageDrawable)) return null;
            // The same look as Home: navy with a white clock in iisu dark, the pale page with a dark one in light.
            return new IisuScreensaver(ctx, new Iisu(ctx, cfg.iisuSkin), d);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private IisuScreensaver(Context ctx, Iisu iisu, Drawable animation) {
        this.animation = animation;
        density = ctx.getResources().getDisplayMetrics().density;
        root = new FrameLayout(ctx);
        root.setBackground(iisu.background());

        // Fitted, not filled: the whole square frame at 90% of the screen height, centred.
        ImageView image = new ImageView(ctx);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageDrawable(animation);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int size = Math.round((b - t) * 0.9f);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) image.getLayoutParams();
            if (lp.width != size) {
                lp.width = size;
                lp.height = size;
                lp.gravity = Gravity.CENTER;
                image.setLayoutParams(lp);
            }
        });

        clock = new TextClock(ctx);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTypeface(iisu.black);
        clock.setTextColor(iisu.text());
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 72f);
        clock.setIncludeFontPadding(false);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT);
        clp.setMargins(Math.round(64 * density), 0, 0, Math.round(48 * density));
        root.addView(clock, clp);
    }

    View view() {
        return root;
    }

    void start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ((AnimatedImageDrawable) animation).setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
            ((AnimatedImageDrawable) animation).start();
        }
        handler.removeCallbacks(drift);
        handler.postDelayed(drift, DRIFT_MS);
    }

    void stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ((AnimatedImageDrawable) animation).stop();
        handler.removeCallbacksAndMessages(null);
        clock.animate().cancel();
    }
}
