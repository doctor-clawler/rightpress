package io.github.doctor_clawler.rightpress;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Accessibility-authorized input replacement, with a persistent user choice.
 * Public APIs do not provide a reliable way to suppress just the secondary
 * button while letting every other global mouse event reach arbitrary apps.
 */
public final class MouseLongPressService extends AccessibilityService {
    static final String ACTION_STATE_CHANGED = "io.github.doctor_clawler.rightpress.STATE_CHANGED";
    private static MouseLongPressService instance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Handler gestureCallbacks = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private CaptureOverlay overlay;
    private boolean modeEnabled;
    private boolean injecting;
    private boolean overlayAttached;
    private boolean shortcutRecording;
    private ShortcutStore shortcutStore;
    private final Set<Long> consumedKeys = new HashSet<>();
    private int gestureGeneration;
    private int activeGestureGeneration = -1;
    private Runnable deferredShortcut;

    public static MouseLongPressService getInstance() { return instance; }
    public boolean isModeEnabled() { return modeEnabled; }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        // OS settings can enable a service without visiting the app. Reject that
        // path before installing event filters, reading windows or adding overlays.
        if (!AccessibilityConsent.isAccepted(this)) {
            // Tear down a stale in-memory session without changing the saved ON/OFF choice.
            setModeEnabled(false);
            consumedKeys.clear();
            shortcutRecording = false;
            activeGestureGeneration = -1;
            deferredShortcut = null;
            if (instance == this) instance = null;
            notifyStateChanged();
            Toast.makeText(this, "RightPress 앱에서 접근성 안내에 먼저 동의하세요", Toast.LENGTH_LONG).show();
            disableSelf();
            return;
        }
        instance = this;
        shortcutStore = new ShortcutStore(this);
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            setServiceInfo(info);
        }
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        setModeEnabled(getSharedPreferences("settings", MODE_PRIVATE)
                .getBoolean("mode_enabled", true));
        notifyStateChanged();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    // This callback interrupts accessibility feedback, not the user's mode choice.
    @Override public void onInterrupt() { }
    @Override public void onDestroy() {
        setModeEnabled(false);
        consumedKeys.clear();
        shortcutRecording = false;
        activeGestureGeneration = -1;
        deferredShortcut = null;
        if (instance == this) instance = null;
        notifyStateChanged();
        super.onDestroy();
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (!AccessibilityConsent.isAccepted(this)) return false;
        long key = ((long) event.getDeviceId() << 32) | (event.getKeyCode() & 0xffffffffL);
        // Drain the entire stream we consumed, even if modifiers were released,
        // settings changed, or a recording dialog opened before this key's UP.
        if (consumedKeys.contains(key)) {
            if (event.getAction() == KeyEvent.ACTION_UP) consumedKeys.remove(key);
            return true;
        }
        if (shortcutRecording || event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0) return false;
        if (event.getKeyCode() == KeyEvent.KEYCODE_R && event.isCtrlPressed() && event.isAltPressed()) {
            consumedKeys.add(key);
            enableMode(!modeEnabled);
            return true;
        }
        // Meta/Sym/Function combinations belong to the system or target app;
        // lock states and left/right variants do not change a stored chord.
        if (event.isMetaPressed() || event.isSymPressed() || event.isFunctionPressed()) return false;
        int modifiers = ShortcutStore.modifiersOf(event);
        if ((modifiers & (KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON)) == 0) return false;
        ShortcutStore store = shortcuts();
        if (!store.isKeyboardEnabled()) return false;
        for (ShortcutRule rule : store.load()) {
            if (rule.enabled && rule.keyCode == event.getKeyCode() && rule.modifiers == modifiers) {
                consumedKeys.add(key);
                performShortcutAction(rule.actionId, rule.packageName);
                return true;
            }
        }
        return false;
    }

    public void setShortcutRecording(boolean recording) { shortcutRecording = recording; }

    private ShortcutStore shortcuts() {
        if (shortcutStore == null) shortcutStore = new ShortcutStore(this);
        return shortcutStore;
    }

    /** Executes one whitelisted action, or accepts one deferred action while a touch finishes.
     * Keyboard shortcuts work even with mouse mode off. */
    public boolean performShortcutAction(String actionId, String packageName) {
        if (!AccessibilityConsent.isAccepted(this)) return false;
        if (!ActionCatalog.isKeyboardAction(actionId)) return false;
        try {
            Intent launch = null;
            if (ActionCatalog.LAUNCH_APP.equals(actionId)) {
                launch = packageName == null ? null : getPackageManager().getLaunchIntentForPackage(packageName);
                if (launch == null) {
                    Toast.makeText(this, "앱 실행 실패 · 앱 다시 선택", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
            if (!ActionCatalog.TOGGLE_MOUSE.equals(actionId)) {
                if (activeGestureGeneration >= 0) {
                    // Public APIs cannot safely cancel an already submitted touch.
                    // Keep at most one requested transition until its final UP/CANCEL.
                    if (deferredShortcut != null) return false;
                    deferredShortcut = () -> performShortcutAction(actionId, packageName);
                    return true;
                }
                cancelPendingRelay();
            }
            switch (actionId) {
                case ActionCatalog.BACK: return performGlobalAction(GLOBAL_ACTION_BACK);
                case ActionCatalog.HOME: return performGlobalAction(GLOBAL_ACTION_HOME);
                case ActionCatalog.RECENTS: return performGlobalAction(GLOBAL_ACTION_RECENTS);
                case ActionCatalog.NOTIFICATIONS: return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
                case ActionCatalog.QUICK_SETTINGS: return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
                case ActionCatalog.TOGGLE_MOUSE:
                    enableMode(!modeEnabled);
                    return true;
                case ActionCatalog.LAUNCH_APP:
                    startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return true;
                default: return false;
            }
        } catch (RuntimeException ex) {
            Toast.makeText(this, ActionCatalog.LAUNCH_APP.equals(actionId)
                ? "앱 실행 실패 · 앱 다시 선택"
                : "현재 화면에서 실행 불가", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private void cancelPendingRelay() {
        if (!injecting) return;
        gestureGeneration++;
        main.removeCallbacksAndMessages(null);
        injecting = false;
        if (overlay != null) overlay.resetInput();
        if (modeEnabled) showOverlay();
    }

    public void enableMode(boolean enable) {
        if (!AccessibilityConsent.isAccepted(this)) return;
        getSharedPreferences("settings", MODE_PRIVATE).edit()
                .putBoolean("mode_enabled", enable).apply();
        setModeEnabled(enable);
    }

    private void setModeEnabled(boolean enable) {
        enable = enable && AccessibilityConsent.isAccepted(this);
        if (enable == modeEnabled) return;
        gestureGeneration++;
        main.removeCallbacksAndMessages(null);
        injecting = false;
        if (overlay != null) overlay.resetInput();
        modeEnabled = enable;
        if (enable) {
            showOverlay();
        } else {
            hideOverlay();
        }
        notifyStateChanged();
    }

    private void notifyStateChanged() {
        sendBroadcast(new Intent(ACTION_STATE_CHANGED).setPackage(getPackageName()));
    }

    private void showOverlay() {
        if (!AccessibilityConsent.isAccepted(this) || !modeEnabled || injecting || overlayAttached || windowManager == null) return;
        if (overlay == null) overlay = new CaptureOverlay(this, this);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        );
        params.setFitInsetsTypes(android.view.WindowInsets.Type.systemBars()
                | android.view.WindowInsets.Type.displayCutout());
        params.setTitle("RightPress mouse capture mode");
        try {
            windowManager.addView(overlay, params);
            overlayAttached = true;
        } catch (Exception ex) {
            modeEnabled = false;
            android.util.Log.e("RightPress", "Overlay creation failed", ex);
        }
    }

    private void hideOverlay() {
        if (overlayAttached && windowManager != null && overlay != null) {
            try { windowManager.removeViewImmediate(overlay); }
            catch (Exception ex) { android.util.Log.w("RightPress", "Overlay removal failed", ex); }
        }
        overlayAttached = false;
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }

    public void tap(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);
        dispatch(path, 70L);
    }

    public void longPress(float x, float y) {
        int ms = getSharedPreferences("settings", MODE_PRIVATE).getInt("press_ms", 650);
        Path path = new Path();
        path.moveTo(x, y);
        dispatch(path, Math.max(400, Math.min(1200, ms)));
    }

    /** True means this physical click is consumed, not that a global action succeeded. */
    public boolean handleSecondaryDown(float x, float y) {
        if (!AccessibilityConsent.isAccepted(this) || !modeEnabled || injecting) return true;
        String actionId = shortcuts().getMouseAction();
        if (ActionCatalog.LONG_PRESS.equals(actionId)) return longPressOnDown(x, y);
        performShortcutAction(actionId, null);
        // A failed BACK/HOME/etc. must never become an unrelated touch on UP.
        return true;
    }

    /** Executes the target's semantic action before mouse UP can cancel a touch.
     * Only bounds and actions are used; text is neither read nor retained. */
    public boolean longPressOnDown(float x, float y) {
        if (!AccessibilityConsent.isAccepted(this) || !modeEnabled || injecting) return false;
        if (!getSharedPreferences("settings", MODE_PRIVATE).getBoolean("instant_enabled", true)) return false;
        try {
            AccessibilityWindowInfo target = null;
            Rect bounds = new Rect();
            int ownWindow = overlay == null ? -1 : overlay.createAccessibilityNodeInfo().getWindowId();
            for (AccessibilityWindowInfo window : getWindows()) {
                if (window.getId() == ownWindow) continue;
                window.getBoundsInScreen(bounds);
                if (bounds.contains((int) x, (int) y)
                        && (target == null || window.getLayer() > target.getLayer())) {
                    target = window;
                }
            }
            // Never look through a dialog, keyboard or other service's overlay.
            if (target == null || target.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) return false;
            AccessibilityNodeInfo node = target.getRoot();
            List<AccessibilityNodeInfo> path = new ArrayList<>();
            int visited = 0;
            while (node != null) {
                node.getBoundsInScreen(bounds);
                if (!node.isVisibleToUser() || !bounds.contains((int) x, (int) y)) break;
                if (path.size() >= 40) return false;
                path.add(node);
                AccessibilityNodeInfo frontChild = null;
                for (int i = 0; i < node.getChildCount(); i++) {
                    if (++visited > 256) return false;
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child == null || !child.isVisibleToUser()) continue;
                    child.getBoundsInScreen(bounds);
                    if (bounds.contains((int) x, (int) y)
                            && (frontChild == null || child.getDrawingOrder() >= frontChild.getDrawingOrder())) {
                        frontChild = child;
                    }
                }
                node = frontChild;
            }
            for (int i = path.size() - 1; i >= 0; i--) {
                node = path.get(i);
                // WebView can accept LONG_CLICK without advertising it in its action list.
                if (node.isEnabled() && node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) return true;
            }
        } catch (RuntimeException ex) {
            // A window can disappear during hit-testing. Use the release fallback.
            android.util.Log.w("RightPress", "Immediate action unavailable");
        }
        return false;
    }

    public void swipe(Path path, long ms) { dispatch(path, Math.max(160, Math.min(1200, ms))); }

    public void scroll(float x, float y, float vertical, float horizontal) {
        Path p = new Path();
        int maxX = getResources().getDisplayMetrics().widthPixels;
        int maxY = getResources().getDisplayMetrics().heightPixels;
        float sx = Math.max(dp(32), Math.min(maxX - dp(32), x));
        float sy = Math.max(dp(110), Math.min(maxY - dp(100), y));
        float length = dp(230);
        float ex = sx;
        float ey = sy;
        if (Math.abs(horizontal) > Math.abs(vertical)) {
            ex = Math.max(dp(32), Math.min(maxX - dp(32), sx + Math.signum(horizontal) * length));
        } else {
            ey = Math.max(dp(80), Math.min(maxY - dp(80), sy + Math.signum(vertical) * length));
        }
        p.moveTo(sx, sy);
        p.lineTo(ex, ey);
        swipe(p, 230);
    }

    private void dispatch(Path path, long durationMs) {
        if (!AccessibilityConsent.isAccepted(this) || !modeEnabled || injecting || activeGestureGeneration >= 0) return;
        injecting = true;
        final int generation = ++gestureGeneration;
        hideOverlay();
        // The target must be exposed for hit-testing before dispatchGesture().
        main.postDelayed(() -> {
            if (!AccessibilityConsent.isAccepted(this) || !modeEnabled || generation != gestureGeneration) return;
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, durationMs));
            boolean ok;
            try {
                activeGestureGeneration = generation;
                ok = dispatchGesture(builder.build(), new GestureResultCallback() {
                    @Override public void onCompleted(GestureDescription gestureDescription) { gestureFinished(generation); }
                    @Override public void onCancelled(GestureDescription gestureDescription) { gestureFinished(generation); }
                }, gestureCallbacks);
            } catch (Exception ex) {
                android.util.Log.e("RightPress", "Gesture dispatch failed", ex);
                ok = false;
            }
            if (!ok) gestureFinished(generation);
        }, 70L);
    }

    private void gestureFinished(int generation) {
        if (instance != this || activeGestureGeneration != generation) return;
        activeGestureGeneration = -1;
        Runnable action = deferredShortcut;
        deferredShortcut = null;
        if (action != null) {
            cancelPendingRelay();
            action.run();
        } else {
            restoreSoon(generation);
        }
    }

    private void restoreSoon(int generation) {
        if (generation != gestureGeneration) return;
        main.postDelayed(() -> {
            if (generation != gestureGeneration) return;
            injecting = false;
            if (modeEnabled) showOverlay();
        }, 100L);
    }
}
