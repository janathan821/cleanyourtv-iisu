package com.gathin.tvhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

/**
 * The iiSU-style skin (config.xml iisu_skin): a console-menu look in the spirit of the iiSU handheld frontend and the
 * Wii U / 3DS menus it borrows from. A pale (or navy) dotted backdrop, one big frosted panel the tiles scroll in, white
 * bezels round the tiles, a centred title pill for the focused app, a clock pill, button hints in the bottom corners,
 * a cyan-to-violet focus ring, a rounded heavy font (Nunito) and soft UI sounds.
 *
 * Everything is drawn from flat fills, gradients and one small dot bitmap: no blur, no video, nothing animated while
 * Home is idle, so a 1 GB TV draws it as cheaply as the classic look. None of iiSU's own art, fonts or sounds are used.
 */
final class Iisu {

    static final String LIGHT = "light", DARK = "dark";

    static final int CYAN = 0xFF38D3FF, VIOLET = 0xFF8C6BFF, PINK = 0xFFFF7FD4;

    /** A known skin name, or "" (the classic look) for anything else. */
    static String parse(String value) {
        if (value == null) return "";
        String v = value.trim();
        return LIGHT.equalsIgnoreCase(v) ? LIGHT : DARK.equalsIgnoreCase(v) ? DARK : "";
    }

    final boolean light;
    private final Context ctx;
    private final float density;
    final Typeface bold, extraBold, black;
    private SoundPool sounds;
    private int moveSound, openSound;
    private long lastMoveMs;

    Iisu(Context ctx, String skin) {
        this.ctx = ctx;
        light = LIGHT.equals(skin);
        density = ctx.getResources().getDisplayMetrics().density;
        bold = ctx.getResources().getFont(R.font.nunito_bold);
        extraBold = ctx.getResources().getFont(R.font.nunito_extrabold);
        black = ctx.getResources().getFont(R.font.nunito_black);
    }

    // ---- colours ---------------------------------------------------------------------------------

    int text() { return light ? 0xFF2E3348 : 0xFFF2F4FF; }
    int textDim() { return light ? 0xFF7A8098 : 0xFFA9B0D6; }
    private int panelFill() { return light ? 0xE6FFFFFF : 0xD9252B50; }
    private int panelEdge() { return light ? 0xFFFFFFFF : 0x597C8CE0; }
    /** The white (or slate) bezel round each tile's artwork. */
    int bezel() { return light ? 0xFFFFFFFF : 0xFF3A4275; }

    private int dp(float v) { return Math.round(v * density); }

    // ---- drawables -------------------------------------------------------------------------------

    /** The full-screen backdrop: a soft vertical gradient under a fine grid of dots. */
    Drawable background() {
        return new Backdrop(light ? 0xFFF3F4F8 : 0xFF161B36, light ? 0xFFDDE1EA : 0xFF2A2458,
                light ? 0x1F5A6380 : 0x267C8CE0, dp(12), Math.max(1f, 1.1f * density));
    }

    /** A frosted rounded panel: the grid's big panel, the clock and title pills and the hint chips. */
    GradientDrawable panel(float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(panelFill());
        d.setCornerRadius(radiusDp * density);
        d.setStroke(Math.max(1, dp(1.5f)), panelEdge());
        return d;
    }

    /** The top-bar pills: frosted at rest, the cyan-to-violet gradient while focused. */
    Drawable pill() {
        return pill(21);
    }

    /** The same, with this corner radius: half the height keeps it a pill (or a circle when square). */
    Drawable pill(float radiusDp) {
        GradientDrawable focused = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{CYAN, VIOLET});
        focused.setCornerRadius(dp(radiusDp));
        focused.setStroke(dp(2), 0xFFFFFFFF);
        GradientDrawable activated = panel(radiusDp);
        activated.setStroke(dp(2), CYAN);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{android.R.attr.state_activated}, activated);
        s.addState(new int[]{}, panel(radiusDp));
        return s;
    }

    ColorStateList pillText() {
        return new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{0xFFFFFFFF, text()});
    }

    // ---- views -----------------------------------------------------------------------------------

    /** Sets the skin's font on every TextView under view: black for the big numbers, extra bold for the rest. */
    void applyFonts(View view) {
        if (view instanceof TextView) {
            TextView t = (TextView) view;
            t.setTypeface(t.getTextSize() >= 30 * density ? black : extraBold);
            t.setLetterSpacing(Math.min(t.getLetterSpacing(), 0.06f));
        } else if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) applyFonts(g.getChildAt(i));
        }
    }

    /** Soft shadow under a panel or pill; on the dark skin the edge line does that job and costs nothing. */
    void lift(View v, float dp) {
        if (light) v.setElevation(dp * density);
    }

    /** The centred pill at the top that names the focused app, like iiSU's title bar. */
    TextView titlePill(FrameLayout root) {
        TextView t = new TextView(ctx);
        t.setBackground(panel(28));
        t.setTextColor(text());
        t.setTypeface(black);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        t.setMinWidth(dp(300));
        t.setMaxWidth(dp(340));
        t.setPadding(dp(28), 0, dp(28), 0);
        t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(56),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = dp(26);   // level with the clock pill: Home's 26dp top padding
        root.addView(t, lp);
        lift(t, 4);
        return t;
    }

    /**
     * A hint chip for one bottom corner, iiSU's "(A) Select" boxes: each row is a round badge naming the remote
     * button and what it does.
     */
    void hintChip(FrameLayout root, int gravity, String[][] rows) {
        LinearLayout chip = new LinearLayout(ctx);
        chip.setOrientation(LinearLayout.VERTICAL);
        chip.setBackground(panel(18));
        chip.setPadding(dp(14), dp(8), dp(16), dp(8));
        chip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        for (String[] row : rows) {
            LinearLayout line = new LinearLayout(ctx);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(0, dp(2), 0, dp(2));
            TextView badge = new TextView(ctx);
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setColor(text());
            badgeBg.setCornerRadius(dp(10));
            badge.setBackground(badgeBg);
            badge.setTextColor(light ? 0xFFFFFFFF : 0xFF1A1F3D);
            badge.setTypeface(black);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            badge.setGravity(Gravity.CENTER);
            badge.setMinWidth(dp(20));
            badge.setPadding(dp(6), 0, dp(6), 0);
            badge.setText(row[0]);
            line.addView(badge, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(20)));
            TextView label = new TextView(ctx);
            label.setTextColor(text());
            label.setTypeface(extraBold);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            label.setText(row[1]);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            llp.setMarginStart(dp(8));
            line.addView(label, llp);
            chip.addView(line);
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
        lp.setMargins(dp(40), 0, dp(40), dp(18));
        root.addView(chip, lp);
        lift(chip, 3);
    }

    /**
     * Reskins a dropdown (the Inputs, Settings and tile menus, which share one glass panel and row style): the frosted
     * panel, rows that light up in the gradient, and the skin's colours and font.
     */
    void skinPopup(View popupPanel) {
        GradientDrawable bg = panel(20);
        // It drops over the white grid panel: on the light skin a grey edge keeps it apart (a popup casts no shadow).
        if (light) bg.setStroke(dp(2), 0xFFD5DAE6);
        popupPanel.setBackground(bg);
        skinRows(popupPanel);
        applyFonts(popupPanel);
    }

    private void skinRows(View v) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            // The rows' second line (a port's status) is the dimmer one; keep it dimmer here too.
            boolean secondary = Color.alpha(t.getTextColors().getDefaultColor()) < 0xF0;
            t.setTextColor(secondary ? new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                    new int[]{0xD9FFFFFF, textDim()}) : pillText());
            if (t.getCompoundDrawableTintList() != null) t.setCompoundDrawableTintList(pillText());
        } else if (v instanceof android.widget.ImageView) {
            if (((android.widget.ImageView) v).getImageTintList() != null) {
                ((android.widget.ImageView) v).setImageTintList(pillText());
            }
        } else if (!(v instanceof ViewGroup) && !v.isFocusable() && v.getHeight() <= density * 2
                && v.getBackground() instanceof android.graphics.drawable.ColorDrawable) {
            v.setBackgroundColor(light ? 0x1F2E3348 : 0x26FFFFFF);   // a separator line
        }
        if (v.isFocusable() && v.getBackground() != null) v.setBackground(row());
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) skinRows(g.getChildAt(i));
        }
    }

    /** A dropdown row: clear at rest, the gradient while focused. */
    private Drawable row() {
        GradientDrawable focused = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{CYAN, VIOLET});
        focused.setCornerRadius(dp(13));
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(0));
        return s;
    }

    // ---- sounds ----------------------------------------------------------------------------------

    /** Loads the two sounds (about 14 KB of PCM in all); call from onStart, with {@link #releaseSounds} in onStop. */
    void loadSounds() {
        if (sounds != null) return;
        sounds = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        moveSound = sounds.load(ctx, R.raw.iisu_move, 1);
        openSound = sounds.load(ctx, R.raw.iisu_open, 1);
    }

    void releaseSounds() {
        if (sounds == null) return;
        sounds.release();
        sounds = null;
    }

    /** The cursor tick; a held D-pad key repeats faster than it should click, so ticks are at least 45 ms apart. */
    void playMove() {
        long now = android.os.SystemClock.uptimeMillis();
        if (sounds == null || now - lastMoveMs < 45) return;
        lastMoveMs = now;
        sounds.play(moveSound, 0.45f, 0.45f, 1, 0, 1f);
    }

    void playOpen() {
        if (sounds != null) sounds.play(openSound, 0.55f, 0.55f, 2, 0, 1f);
    }

    // ---- avatar ---------------------------------------------------------------------------------

    /** The avatar in the top-right pill: a little pixel-art TV with a face, on the skin's gradient. */
    Drawable avatar(int sizeDp) {
        Drawable d = new Avatar();
        d.setBounds(0, 0, dp(sizeDp), dp(sizeDp));
        return d;
    }

    /** O outline, W screen, E eyes and mouth, P cheeks, A antenna; '.' is see-through. */
    private static final String[] TV_FACE = {
            "..A......A..",
            "...A....A...",
            "....A..A....",
            ".OOOOOOOOOO.",
            ".OWWWWWWWWO.",
            ".OWWEWWEWWO.",
            ".OWWEWWEWWO.",
            ".OWPWWWWPWO.",
            ".OWWWEEWWWO.",
            ".OOOOOOOOOO.",
            "..OO....OO..",
    };

    private static final class Avatar extends Drawable {
        private final Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint px = new Paint();

        @Override
        protected void onBoundsChange(@NonNull Rect b) {
            circle.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom, new int[]{CYAN, VIOLET, PINK}, null,
                    Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(@NonNull Canvas c) {
            Rect b = getBounds();
            c.drawCircle(b.exactCenterX(), b.exactCenterY(), b.width() / 2f, circle);
            int cols = TV_FACE[0].length(), rows = TV_FACE.length;
            // Whole pixels, so the art stays crisp: about 60% of the circle, centred.
            float cell = (float) Math.floor(b.width() * 0.6f / cols);
            if (cell < 1f) cell = 1f;
            float left = b.exactCenterX() - cols * cell / 2f, top = b.exactCenterY() - rows * cell / 2f;
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    int color;
                    switch (TV_FACE[y].charAt(x)) {
                        case 'O': case 'E': color = 0xFF1E2240; break;
                        case 'W': color = 0xFFFFFFFF; break;
                        case 'P': color = 0xFFFF9BD6; break;
                        case 'A': color = 0xFFFFFFFF; break;
                        default: continue;
                    }
                    px.setColor(color);
                    c.drawRect(left + x * cell, top + y * cell, left + (x + 1) * cell, top + (y + 1) * cell, px);
                }
            }
        }

        @Override public int getIntrinsicWidth() { return getBounds().width(); }
        @Override public int getIntrinsicHeight() { return getBounds().height(); }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ---- backdrop --------------------------------------------------------------------------------

    /** Gradient plus a repeating dot tile; drawn only when Home's root redraws, never animated. */
    private static final class Backdrop extends Drawable {
        private final Paint gradient = new Paint();
        private final Paint dots = new Paint();
        private final int top, bottom;

        Backdrop(int top, int bottom, int dotColor, int cell, float radius) {
            this.top = top;
            this.bottom = bottom;
            Bitmap tile = Bitmap.createBitmap(cell, cell, Bitmap.Config.ARGB_8888);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(dotColor);
            new Canvas(tile).drawCircle(cell / 2f, cell / 2f, radius, p);
            dots.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        }

        @Override
        protected void onBoundsChange(@NonNull Rect b) {
            gradient.setShader(new LinearGradient(0, b.top, 0, b.bottom, top, bottom, Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(@NonNull Canvas c) {
            c.drawRect(getBounds(), gradient);
            c.drawRect(getBounds(), dots);
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
