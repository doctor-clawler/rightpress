package io.github.doctor_clawler.rightpress;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

import java.util.ArrayList;

/** Receives mouse events while the saved capture mode is enabled. */
public final class CaptureOverlay extends View {
    static final int EXIT_TOUCH_HEIGHT_DP = 70;
    private final MouseLongPressService service;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean primaryDown;
    private boolean secondaryDown;
    private boolean secondaryHandled;
    private float startX, startY;
    private long startAt;
    private float pointerX, pointerY;
    private final ArrayList<float[]> samples = new ArrayList<>();

    public CaptureOverlay(Context context, MouseLongPressService service) {
        super(context);
        this.service = service;
        setBackgroundColor(Color.TRANSPARENT);
        setFocusable(false);
        setClickable(true);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
    Insets safeInsets() {
        // Accessibility overlays sit above system bars, so their root insets may
        // be zero. WindowMetrics retains the display's actual protected bounds.
        WindowMetrics metrics = getContext().getSystemService(WindowManager.class).getCurrentWindowMetrics();
        Rect bounds = metrics.getBounds();
        Insets safe = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        int[] location = new int[2];
        getLocationOnScreen(location);
        return Insets.of(Math.max(0, bounds.left + safe.left - location[0]),
                Math.max(0, bounds.top + safe.top - location[1]),
                Math.max(0, location[0] + getWidth() - (bounds.right - safe.right)),
                Math.max(0, location[1] + getHeight() - (bounds.bottom - safe.bottom)));
    }

    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        invalidate();
        return super.onApplyWindowInsets(insets);
    }

    private boolean onExit(float x, float y) {
        Insets safe = safeInsets();
        return x >= getWidth() - safe.right - dp(120) && x <= getWidth() - safe.right
                && y >= safe.top && y <= safe.top + dp(EXIT_TOUCH_HEIGHT_DP);
    }

    private boolean onExitScreen(float x, float y) {
        int[] location = new int[2];
        getLocationOnScreen(location);
        return onExit(x - location[0], y - location[1]);
    }

    void resetInput() {
        primaryDown = false;
        secondaryDown = false;
        secondaryHandled = false;
        samples.clear();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        Insets safe = safeInsets();
        float w = getWidth() - safe.right;
        float top = safe.top;
        paint.setColor(0xDF0F172A);
        c.drawRoundRect(w - dp(117), top + dp(8), w - dp(8), top + dp(51), dp(15), dp(15), paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(dp(15));
        paint.setFakeBoldText(true);
        c.drawText("마우스 끄기", w - dp(100), top + dp(36), paint);
        paint.setFakeBoldText(false);
    }

    @Override public boolean onHoverEvent(MotionEvent event) {
        pointerX = event.getRawX();
        pointerY = event.getRawY();
        return true;
    }

    @Override public boolean onGenericMotionEvent(MotionEvent event) {
        pointerX = event.getRawX();
        pointerY = event.getRawY();
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_BUTTON_PRESS) {
            if (event.getActionButton() == MotionEvent.BUTTON_SECONDARY) {
                beginSecondary(pointerX, pointerY);
                return true;
            }
            if (event.getActionButton() == MotionEvent.BUTTON_PRIMARY && !primaryDown) {
                if (onExit(event.getX(), event.getY())) {
                    service.enableMode(false);
                    return true;
                }
                beginPrimary(pointerX, pointerY);
                return true;
            }
        } else if (action == MotionEvent.ACTION_BUTTON_RELEASE) {
            if (event.getActionButton() == MotionEvent.BUTTON_SECONDARY && secondaryDown) {
                endSecondary(pointerX, pointerY);
                return true;
            }
            if (event.getActionButton() == MotionEvent.BUTTON_PRIMARY && primaryDown) {
                endPrimary(pointerX, pointerY);
                return true;
            }
            return true;
        } else if (action == MotionEvent.ACTION_SCROLL) {
            float v = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            float h = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
            if (v != 0 || h != 0) service.scroll(pointerX, pointerY, v, h);
            return true;
        } else if (action == MotionEvent.ACTION_MOVE && primaryDown) {
            remember(pointerX, pointerY);
            return true;
        }
        return true;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        pointerX = event.getRawX();
        pointerY = event.getRawY();
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (onExit(event.getX(), event.getY())) { service.enableMode(false); return true; }
                if ((event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0) {
                    beginSecondary(pointerX, pointerY);
                } else if (!primaryDown) {
                    beginPrimary(pointerX, pointerY);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (primaryDown) remember(pointerX, pointerY);
                return true;
            case MotionEvent.ACTION_UP:
                if (secondaryDown) endSecondary(pointerX, pointerY);
                else if (primaryDown) endPrimary(pointerX, pointerY);
                return true;
            case MotionEvent.ACTION_CANCEL:
                resetInput();
                return true;
            default:
                return true;
        }
    }

    private void beginSecondary(float x, float y) {
        if (secondaryDown) return; // DOWN and BUTTON_PRESS describe the same click.
        if (onExitScreen(x, y)) { service.enableMode(false); return; }
        primaryDown = false;
        samples.clear();
        secondaryDown = true;
        secondaryHandled = service.handleSecondaryDown(x, y);
    }

    private void endSecondary(float x, float y) {
        secondaryDown = false;
        boolean handled = secondaryHandled;
        secondaryHandled = false;
        // Only unsupported long-press targets need a synthetic touch. Its start must wait
        // until UP because physical release cancels an in-flight injected touch.
        if (!handled) service.longPress(x, y);
    }

    private void beginPrimary(float x, float y) {
        primaryDown = true;
        startX = x;
        startY = y;
        startAt = SystemClock.uptimeMillis();
        samples.clear();
        remember(x, y);
    }

    private void remember(float x, float y) {
        if (samples.size() < 100) samples.add(new float[]{x, y});
    }

    private void endPrimary(float x, float y) {
        primaryDown = false;
        if (onExitScreen(x, y)) {
            samples.clear();
            service.enableMode(false);
            return;
        }
        remember(x, y);
        float dx = x - startX;
        float dy = y - startY;
        if (Math.hypot(dx, dy) < dp(12)) {
            service.tap(x, y);
        } else {
            Path p = new Path();
            p.moveTo(startX, startY);
            for (float[] point : samples) p.lineTo(point[0], point[1]);
            p.lineTo(x, y);
            service.swipe(p, SystemClock.uptimeMillis() - startAt);
        }
        samples.clear();
    }
}
