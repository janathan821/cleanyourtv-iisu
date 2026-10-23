package com.gathin.tvhome;

import android.os.Build;
import android.service.dreams.DreamService;

/**
 * Screensaver: the launcher's thin clock and date on black, over a slow ambient glow (ClockScene).
 * No network, no images: cheap enough for a 1 GB TV.
 */
public class ClockDream extends DreamService {

    private ClockScene scene;
    /** The iiSU skin's screensaver.gif scene, used instead of the clock when the theme has one. */
    private IisuScreensaver iisu;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) iisu = IisuScreensaver.create(this);
        if (iisu != null) {
            setContentView(iisu.view());
            return;
        }
        setContentView(R.layout.dream_clock);
        scene = new ClockScene(getWindow().getDecorView());
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (iisu != null) iisu.start();
        else scene.start();
    }

    @Override
    public void onDreamingStopped() {
        if (iisu != null) iisu.stop();
        else scene.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (scene != null) scene.stop();
        if (iisu != null) iisu.stop();
        super.onDetachedFromWindow();
    }
}
