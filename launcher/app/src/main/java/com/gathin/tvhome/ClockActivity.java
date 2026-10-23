package com.gathin.tvhome;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/**
 * The clock screensaver shown by Home itself after home_idle_clock_minutes without input. Some TVs' own
 * screens (CVTE's live TV) do not keep the screen on, so the system screensaver would cut into programmes;
 * this one only ever starts from Home. Any key returns to Home.
 */
public class ClockActivity extends Activity {

    private ClockScene scene;
    /** The iiSU skin's screensaver.gif scene, used instead of the clock when the theme has one. */
    private IisuScreensaver iisu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) iisu = IisuScreensaver.create(this);
        if (iisu != null) setContentView(iisu.view());
        else setContentView(R.layout.dream_clock);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE);
        if (iisu == null) scene = new ClockScene(getWindow().getDecorView());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (iisu != null) iisu.start();
        else scene.start();
    }

    @Override
    protected void onPause() {
        if (iisu != null) iisu.stop();
        else scene.stop();
        super.onPause();
        finish();   // never left behind in the back stack
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // The key that wakes the clock should not also act on Home, so it is consumed here.
        if (event.getAction() == KeyEvent.ACTION_UP) finish();
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        finish();
        return true;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
