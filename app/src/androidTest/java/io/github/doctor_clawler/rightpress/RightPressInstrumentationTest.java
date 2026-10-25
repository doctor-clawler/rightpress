package io.github.doctor_clawler.rightpress;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.Settings;
import android.test.InstrumentationTestCase;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Device-only checks; this class and the temporary test UI are absent from the delivered APK. */
@SuppressWarnings("deprecation")
public final class RightPressInstrumentationTest extends InstrumentationTestCase {
    private Instrumentation instrumentation;
    private UiAutomation automation;
    private MainActivity activity;
    private MouseLongPressService service;
    private Button target;
    private EditText editor;
    private ScrollView scrollTarget;
    private String emulatorKeyboard;
    private final AtomicInteger clicks = new AtomicInteger();
    private final AtomicInteger longClicks = new AtomicInteger();
    private final AtomicInteger leakedR = new AtomicInteger();
    private final AtomicInteger leakedK = new AtomicInteger();
    private final AtomicInteger leakedRDown = new AtomicInteger();
    private final AtomicInteger leakedRUp = new AtomicInteger();
    private final AtomicInteger leakedKDown = new AtomicInteger();
    private final AtomicInteger leakedKUp = new AtomicInteger();
    private final AtomicInteger semanticLongClickAttempts = new AtomicInteger();
    private static final int SEMANTIC_NORMAL = 0;
    private static final int SEMANTIC_HIDDEN_ACCEPT = 1;
    private static final int SEMANTIC_REJECT = 2;
    private static final int CTRL_ALT = KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON;

    @Override protected void setUp() throws Exception {
        super.setUp();
        instrumentation = getInstrumentation();
        automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        assertNotNull("UiAutomation must preserve the installed accessibility service", automation);
        // Hardware keyboard tests leave the system in navigation focus mode.
        // Give every independent temporary UI the same initial interaction mode.
        instrumentation.setInTouchMode(true);
        instrumentation.getTargetContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putInt("press_ms", 650).putBoolean("instant_enabled", true)
            .putBoolean("keyboard_enabled", true).putString("mouse_action", ActionCatalog.LONG_PRESS)
            .remove("shortcut_rules").commit();
        // Input tests opt into the current disclosure explicitly in their private fixture.
        // The production app can record this receipt only from its affirmative button.
        assertTrue(AccessibilityConsent.accept(instrumentation.getTargetContext()));
        launchActivity();
        instrumentation.runOnMainSync(() -> service = MouseLongPressService.getInstance());
        // am instrument force-stops the target process first. An enabled accessibility
        // service can consequently remain in AccessibilityManager's crashed set until
        // its exact component is toggled after this test process has started.
        if (service == null) rebindEnabledService();
        long deadline = SystemClock.uptimeMillis() + 10000;
        do {
            instrumentation.runOnMainSync(() -> service = MouseLongPressService.getInstance());
            if (service != null) break;
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        assertNotNull("Enable io.github.doctor_clawler.rightpress/.MouseLongPressService on the dedicated emulator before this test", service);
        instrumentation.runOnMainSync(() -> service.setShortcutRecording(false));
        setMode(false);
    }

    @Override protected void tearDown() throws Exception {
        if (service != null) {
            instrumentation.runOnMainSync(() -> service.setShortcutRecording(false));
            setMode(false);
        }
        if (activity != null) instrumentation.runOnMainSync(() -> activity.finish());
        if (instrumentation != null) {
            instrumentation.getTargetContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
                .edit().putInt("press_ms", 650).putBoolean("instant_enabled", true)
                .putBoolean("keyboard_enabled", true).putString("mouse_action", ActionCatalog.LONG_PRESS)
                .remove("shortcut_rules").commit();
        }
        super.tearDown();
    }

    public void testConsentCancelAndBackDoNotAcceptOrOpenSettings() throws Exception {
        clearConsent();
        Map<String, ?> settings = instrumentation.getTargetContext()
                .getSharedPreferences("settings", Context.MODE_PRIVATE).getAll();
        Instrumentation.ActivityMonitor monitor = blockAccessibilitySettings();
        try {
            showConsentFromHelp();
            assertFalse(AccessibilityConsent.isAccepted(instrumentation.getTargetContext()));
            assertEquals("Displaying the disclosure must not open Android settings", 0, monitor.getHits());
            uiClickText("취소");
            assertFalse("Cancel must not accept", AccessibilityConsent.isAccepted(instrumentation.getTargetContext()));
            showConsentFromHelp();
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            instrumentation.waitForIdleSync();
            assertFalse("Back must not accept", AccessibilityConsent.isAccepted(instrumentation.getTargetContext()));
            assertEquals("Neither rejection path may open Android settings", 0, monitor.getHits());
            assertEquals("Rejection must preserve input settings", settings, instrumentation.getTargetContext()
                    .getSharedPreferences("settings", Context.MODE_PRIVATE).getAll());
        } finally {
            instrumentation.removeMonitor(monitor);
            assertTrue(AccessibilityConsent.accept(instrumentation.getTargetContext()));
        }
    }

    public void testConsentAcceptPersistsAndOpensSettingsOnce() throws Exception {
        clearConsent();
        Map<String, ?> settings = instrumentation.getTargetContext()
                .getSharedPreferences("settings", Context.MODE_PRIVATE).getAll();
        Instrumentation.ActivityMonitor monitor = blockAccessibilitySettings();
        try {
            showConsentFromHelp();
            assertFalse("Showing the disclosure is not acceptance", AccessibilityConsent.isAccepted(instrumentation.getTargetContext()));
            uiClickText("동의하고 설정 열기");
            assertTrue("The affirmative button must persist current consent", AccessibilityConsent.isAccepted(instrumentation.getTargetContext()));
            assertEquals("Acceptance opens Android settings once", 1, monitor.getHits());
            assertEquals("Accepting must preserve existing input settings", settings, instrumentation.getTargetContext()
                    .getSharedPreferences("settings", Context.MODE_PRIVATE).getAll());
            launchActivity();
            assertTrue("Consent survives activity recreation", AccessibilityConsent.isAccepted(instrumentation.getTargetContext()));
            uiClickText("도움말");
            uiClickText("접근성 설정");
            assertEquals("Current consent allows subsequent settings access", 2, monitor.getHits());
            assertNull("Valid consent is not requested again", uiTextBounds("접근성 사용 안내", false));
        } finally {
            instrumentation.removeMonitor(monitor);
            assertTrue(AccessibilityConsent.accept(instrumentation.getTargetContext()));
        }
    }

    public void testConsentReceiptMustMatchCurrentDisclosureVersion() {
        Context context = instrumentation.getTargetContext();
        try {
            clearConsent();
            assertFalse("A new installation has no receipt", AccessibilityConsent.isAccepted(context));
            context.getSharedPreferences(AccessibilityConsent.PREFERENCES, Context.MODE_PRIVATE).edit()
                    .putInt(AccessibilityConsent.VERSION_KEY, AccessibilityConsent.DISCLOSURE_VERSION - 1).commit();
            assertFalse("An older disclosure is not current consent", AccessibilityConsent.isAccepted(context));
            context.getSharedPreferences(AccessibilityConsent.PREFERENCES, Context.MODE_PRIVATE).edit()
                    .putInt(AccessibilityConsent.VERSION_KEY, AccessibilityConsent.DISCLOSURE_VERSION + 1).commit();
            assertFalse("An unknown receipt is not current consent", AccessibilityConsent.isAccepted(context));
            context.getSharedPreferences(AccessibilityConsent.PREFERENCES, Context.MODE_PRIVATE).edit()
                    .putString(AccessibilityConsent.VERSION_KEY, "invalid").commit();
            assertFalse("A malformed receipt must fail closed", AccessibilityConsent.isAccepted(context));
            assertTrue(AccessibilityConsent.accept(context));
            assertTrue(AccessibilityConsent.isAccepted(context));
        } finally { assertTrue(AccessibilityConsent.accept(context)); }
    }

    public void testConsentGateRejectsUnconsentedServiceAndPreservesMode() throws Exception {
        Context context = instrumentation.getTargetContext();
        String originalServices = shell("settings get secure enabled_accessibility_services").trim();
        String originalEnabled = shell("settings get secure accessibility_enabled").trim();
        installHarness();
        clearConsent();
        try {
            instrumentation.runOnMainSync(() -> {
                service.enableMode(true);
                assertFalse("No overlay mode before consent", service.isModeEnabled());
                assertFalse("No key event may be inspected or consumed before consent", service.onKeyEvent(null));
                assertFalse("No action before consent", service.performShortcutAction(ActionCatalog.HOME, null));
                assertFalse("No window lookup before consent", service.longPressOnDown(100, 100));
                service.tap(100, 100);
                service.longPress(100, 100);
            });
            assertFalse("A blocked mode request must not change the stored choice", savedMode());
            assertFalse("A blocked mode request must not attach an overlay", captureOverlayAttached());
            assertEquals("No gesture may be submitted before consent", -1, activeGestureGeneration());
            assertTrue("A blocked action must leave the activity in front", activityHasFocus());
            // Simulate an existing ON preference and enabling the service directly in OS settings.
            context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("mode_enabled", true).commit();
            rebindEnabledService(false);
            ComponentName expected = new ComponentName(context, MouseLongPressService.class);
            long deadline = SystemClock.uptimeMillis() + 10000;
            boolean stillEnabled;
            do {
                stillEnabled = false;
                for (String entry : shell("settings get secure enabled_accessibility_services").trim().split(":")) {
                    if (expected.equals(ComponentName.unflattenFromString(entry))) stillEnabled = true;
                }
                if (!stillEnabled) break;
                SystemClock.sleep(50);
            } while (SystemClock.uptimeMillis() < deadline);
            assertFalse("Direct OS enable without consent must disable the service", stillEnabled);
            AtomicReference<MouseLongPressService> active = new AtomicReference<>();
            instrumentation.runOnMainSync(() -> active.set(MouseLongPressService.getInstance()));
            assertNull("Unconsented service must never become the active instance", active.get());
            assertTrue("Rejected connection must preserve the existing ON preference", savedMode());
        } finally {
            assertTrue(AccessibilityConsent.accept(context));
            shell("settings put secure enabled_accessibility_services " + originalServices);
            if (originalEnabled.equals("null")) shell("settings delete secure accessibility_enabled");
            else shell("settings put secure accessibility_enabled " + originalEnabled);
            long deadline = SystemClock.uptimeMillis() + 10000;
            do {
                instrumentation.runOnMainSync(() -> service = MouseLongPressService.getInstance());
                if (service != null) break;
                SystemClock.sleep(50);
            } while (SystemClock.uptimeMillis() < deadline);
            assertNotNull("The consented fixture must reconnect", service);
        }
        assertTrue("A valid connection restores the existing ON preference", modeEnabled());
    }

    private void clearConsent() {
        assertTrue(instrumentation.getTargetContext()
                .getSharedPreferences(AccessibilityConsent.PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit());
    }

    private Instrumentation.ActivityMonitor blockAccessibilitySettings() {
        return instrumentation.addMonitor(new IntentFilter(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true);
    }

    private void showConsentFromHelp() {
        uiClickText("도움말");
        uiClickText("접근성 설정");
        uiAssertText("접근성 사용 안내");
    }

    public void testMouseSecondaryProducesUnderlyingLongClick() throws Exception {
        installHarness();
        setMode(true);
        float[] center = centerOf(target);
        long downAt = secondaryDown(center);
        try {
            waitForCount(longClicks, 1, 1200);
            assertEquals("The underlying long-click must occur while the secondary button is still DOWN", 1, longClicks.get());
            assertTrue("DOWN must perform the target's semantic action", semanticLongClickAttempts.get() > 0);
        } finally {
            secondaryUp(downAt, center);
        }
        SystemClock.sleep(900);
        assertEquals("One secondary click must produce one underlying long-click", 1, longClicks.get());
        assertEquals("A secondary click must not also tap the target", 0, clicks.get());
        SystemClock.sleep(250);
        assertTrue("The capture mode must remain enabled after injection", modeEnabled());
    }

    public void testHiddenLongClickActionRunsOnDown() throws Exception {
        installHarness(SEMANTIC_HIDDEN_ACCEPT);
        AtomicReference<Boolean> advertised = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            AccessibilityNodeInfo node = target.createAccessibilityNodeInfo();
            advertised.set(node.isLongClickable()
                || node.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK));
        });
        assertFalse("Fixture must hide LONG_CLICK from both capability hints", advertised.get());
        setMode(true);
        float[] center = centerOf(target);
        long downAt = secondaryDown(center);
        try {
            waitForCount(longClicks, 1, 1200);
            assertEquals("A supported but unadvertised long-click must execute before UP", 1, longClicks.get());
            assertTrue("The hidden action must actually be attempted", semanticLongClickAttempts.get() > 0);
        } finally {
            secondaryUp(downAt, center);
        }
        SystemClock.sleep(900);
        assertEquals("UP must not duplicate a successful hidden action", 1, longClicks.get());
        assertEquals(0, clicks.get());
    }

    public void testRejectedLongClickFallsBackAfterRelease() throws Exception {
        installHarness(SEMANTIC_REJECT);
        setMode(true);
        float[] center = centerOf(target);
        long downAt = secondaryDown(center);
        try {
            waitForCount(semanticLongClickAttempts, 1, 1200);
            assertTrue("The immediate action must be attempted and explicitly rejected", semanticLongClickAttempts.get() > 0);
            SystemClock.sleep(750);
            assertEquals("Unsupported semantic action must not fire the touch fallback before UP", 0, longClicks.get());
            assertEquals(0, clicks.get());
        } finally {
            secondaryUp(downAt, center);
        }
        waitForCount(longClicks, 1, 2500);
        assertEquals("The released button must use one touch long-press fallback", 1, longClicks.get());
        assertEquals(0, clicks.get());
    }

    public void testInstantDisabledWaitsForRelease() throws Exception {
        instrumentation.getTargetContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putBoolean("instant_enabled", false).commit();
        installHarness();
        setMode(true);
        float[] center = centerOf(target);
        long downAt = secondaryDown(center);
        try {
            SystemClock.sleep(750);
            assertEquals("Disabling instant mode must skip the semantic action", 0, semanticLongClickAttempts.get());
            assertEquals("Compatibility mode must wait for button release", 0, longClicks.get());
            assertEquals(0, clicks.get());
        } finally {
            secondaryUp(downAt, center);
        }
        waitForCount(longClicks, 1, 2500);
        assertEquals("The released button must deliver a touch long-press", 1, longClicks.get());
        assertEquals(0, semanticLongClickAttempts.get());
        assertEquals(0, clicks.get());
    }

    public void testTopmostDialogPreventsLongClickOnCoveredTarget() throws Exception {
        installHarness();
        float[] coveredPoint = centerOf(target);
        AtomicReference<Dialog> dialog = new AtomicReference<>();
        AtomicReference<Button> dialogTarget = new AtomicReference<>();
        AtomicInteger dialogSemanticAttempts = new AtomicInteger();
        AtomicInteger dialogLongClicks = new AtomicInteger();
        AtomicInteger dialogClicks = new AtomicInteger();
        try {
            instrumentation.runOnMainSync(() -> {
                Dialog modal = new Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar);
                dialog.set(modal);
                modal.setCancelable(false);
                Button topTarget = new Button(activity);
                dialogTarget.set(topTarget);
                topTarget.setText("Topmost dialog long-click target");
                topTarget.setOnClickListener(view -> dialogClicks.incrementAndGet());
                topTarget.setOnLongClickListener(view -> {
                    dialogLongClicks.incrementAndGet();
                    return true;
                });
                topTarget.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                    @Override public boolean performAccessibilityAction(View host, int action, Bundle args) {
                        if (action == AccessibilityNodeInfo.ACTION_LONG_CLICK) {
                            dialogSemanticAttempts.incrementAndGet();
                            return false;
                        }
                        return super.performAccessibilityAction(host, action, args);
                    }
                });
                modal.setContentView(topTarget, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                modal.getWindow().setWindowAnimations(0);
                modal.show();
                modal.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            });
            instrumentation.waitForIdleSync();
            SystemClock.sleep(250);
            instrumentation.runOnMainSync(() -> {
                View topTarget = dialogTarget.get();
                int[] location = new int[2];
                topTarget.getLocationOnScreen(location);
                assertTrue("The real dialog must cover the underlying target's pointer position",
                    coveredPoint[0] >= location[0] && coveredPoint[0] < location[0] + topTarget.getWidth()
                    && coveredPoint[1] >= location[1] && coveredPoint[1] < location[1] + topTarget.getHeight());
            });
            setMode(true);
            long downAt = secondaryDown(coveredPoint);
            try {
                waitForCount(dialogSemanticAttempts, 1, 1200);
                assertTrue("The topmost dialog must receive and reject the immediate action",
                    dialogSemanticAttempts.get() > 0);
                SystemClock.sleep(750);
                assertEquals("A rejected dialog action must not reach a covered window", 0, semanticLongClickAttempts.get());
                assertEquals("The underlying target must not long-click on DOWN", 0, longClicks.get());
                assertEquals("The dialog touch fallback must wait for UP", 0, dialogLongClicks.get());
            } finally {
                secondaryUp(downAt, coveredPoint);
            }
            waitForCount(dialogLongClicks, 1, 2500);
            assertEquals("The release fallback must long-click the topmost dialog once", 1, dialogLongClicks.get());
            assertEquals("The fallback must not touch the covered target", 0, longClicks.get());
            assertEquals(0, semanticLongClickAttempts.get());
            assertEquals(0, clicks.get());
            assertEquals(0, dialogClicks.get());
        } finally {
            setMode(false);
            instrumentation.runOnMainSync(() -> {
                if (dialog.get() != null) dialog.get().dismiss();
            });
        }
    }

    public void testDefaultModeStartsEnabledAfterServiceReconnect() throws Exception {
        instrumentation.getTargetContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().remove("mode_enabled").commit();
        reconnectService();
        assertTrue("A newly connected service with no saved mode must automatically start", modeEnabled());
    }

    public void testEnabledModeSurvivesServiceReconnect() throws Exception {
        setMode(true);
        assertTrue("Explicit ON must be saved", savedMode());
        reconnectService();
        assertTrue("Lifecycle cleanup must preserve the user's saved ON state", savedMode());
        assertTrue("A reconnected service must restore ON without reopening the activity", modeEnabled());
    }

    public void testExplicitStopSurvivesServiceReconnect() throws Exception {
        setMode(true);
        setMode(false);
        assertFalse("Explicit OFF must be saved", savedMode());
        reconnectService();
        assertFalse("A reconnect must retain the user's explicit stop", savedMode());
        assertFalse("Automatic resume must not override a saved OFF state", modeEnabled());
    }

    public void testPrimaryClickAndPhysicalKeyboardPassThrough() throws Exception {
        installHarness();
        setMode(true);
        float[] center = centerOf(target);
        mouseClick(center[0], center[1], MotionEvent.BUTTON_PRIMARY);
        waitForCount(clicks, 1, 1800);
        int actualClicks = clicks.get();
        // Keep the original result fixed while collecting failure-only evidence.
        String failureState = actualClicks == 1 ? null : primaryPointerFailureState(center, actualClicks);
        assertEquals(failureState, 1, actualClicks);
        assertEquals(0, longClicks.get());
        instrumentation.runOnMainSync(() -> editor.requestFocus());
        keyStroke(KeyEvent.KEYCODE_A);
        keyStroke(KeyEvent.KEYCODE_B);
        waitForEditorText("ab", 3000);
        AtomicReference<String> value = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> value.set(editor.getText().toString()));
        assertEquals("Keyboard text must reach the focused underlying editor with mode on", "ab", value.get());
    }

    public void testSecondaryHeldFor250msStillLongClicks() throws Exception {
        installHarness();
        setMode(true);
        float[] center = centerOf(target);
        mouseClickHeld(center[0], center[1], MotionEvent.BUTTON_SECONDARY, 250);
        waitForCount(longClicks, 1, 2500);
        assertEquals("A real button released after 250ms must still produce a long-click", 1, longClicks.get());
        assertEquals("The delayed button release must not create an ordinary click", 0, clicks.get());
    }

    public void testMouseWheelScrollsUnderlyingView() throws Exception {
        installHarness();
        setMode(true);
        float[] center = centerOf(scrollTarget);
        long now = SystemClock.uptimeMillis();
        injectPointer(now, MotionEvent.ACTION_HOVER_MOVE, center[0], center[1], 0, InputDevice.SOURCE_MOUSE);
        injectPointer(now, MotionEvent.ACTION_SCROLL, center[0], center[1], 0, InputDevice.SOURCE_MOUSE, -1);
        SystemClock.sleep(900);
        AtomicInteger scrollY = new AtomicInteger();
        instrumentation.runOnMainSync(() -> scrollY.set(scrollTarget.getScrollY()));
        assertTrue("Mouse wheel must scroll the underlying view; scrollY=" + scrollY.get(), scrollY.get() > 0);
    }

    public void testShortcutConsumesRUpAfterModifiersReleased() throws Exception {
        installHarness();
        instrumentation.runOnMainSync(() -> editor.requestFocus());
        shortcutWithModifiersReleasedFirst();
        waitForMode(true, 3000);
        waitForConsumedKeysCleared(3000);
        assertTrue("Ctrl+Alt+R must enable mode", modeEnabled());
        assertEquals("Neither R down nor R up may leak to the editor", 0, leakedR.get());
        shortcutWithModifiersReleasedFirst();
        waitForMode(false, 3000);
        waitForConsumedKeysCleared(3000);
        assertFalse("The next shortcut must disable mode", modeEnabled());
        assertEquals("Releasing modifiers before R must preserve the key stream", 0, leakedR.get());
    }

    public void testOverlayExitAndActivityStatusRefresh() throws Exception {
        setMode(true);
        SystemClock.sleep(150);
        assertTrue("Activity must reflect mode activation", containsText("마우스 변환 켜짐"));
        Field overlayField = MouseLongPressService.class.getDeclaredField("overlay");
        overlayField.setAccessible(true);
        AtomicReference<float[]> exitPoint = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try {
                View overlay = (View) overlayField.get(service);
                int[] location = new int[2];
                overlay.getLocationOnScreen(location);
                float density = overlay.getResources().getDisplayMetrics().density;
                android.graphics.Insets safe = ((CaptureOverlay) overlay).safeInsets();
                exitPoint.set(new float[]{location[0] + overlay.getWidth() - safe.right - 60 * density,
                    location[1] + safe.top + 32 * density});
            } catch (IllegalAccessException ex) {
                throw new AssertionError(ex);
            }
        });
        touchTap(exitPoint.get()[0], exitPoint.get()[1]);
        SystemClock.sleep(200);
        assertFalse("Visible overlay exit must disable capture mode", modeEnabled());
        assertTrue("Activity status must refresh after overlay exit without resuming", containsText("마우스 변환 꺼짐"));
    }

    public void testRapidOffOnInvalidatesPendingGesture() throws Exception {
        installHarness();
        float[] center = centerOf(target);
        instrumentation.runOnMainSync(() -> {
            service.enableMode(true);
            service.longPress(center[0], center[1]);
            service.enableMode(false);
            service.enableMode(true);
        });
        SystemClock.sleep(1100);
        assertEquals("A pending gesture from the old mode session must not click", 0, clicks.get());
        assertEquals("A pending gesture from the old mode session must not long-click", 0, longClicks.get());
        mouseClick(center[0], center[1], MotionEvent.BUTTON_SECONDARY);
        waitForCount(longClicks, 1, 2500);
        assertEquals("New-session injection must still work after an old callback was cancelled", 1, longClicks.get());
    }

    public void testSavedDurationDisplayedAfterActivityRestart() {
        instrumentation.getTargetContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putInt("press_ms", 900).commit();
        instrumentation.runOnMainSync(() -> activity.finish());
        launchActivity();
        assertTrue("The saved 900ms duration must be displayed immediately", containsText("호환 터치: 900ms"));
    }

    public void testShortcutRulesPersistAddEditDisableAndDelete() {
        ShortcutStore store = shortcutStore();
        assertTrue("Keyboard mappings default to enabled", store.isKeyboardEnabled());
        assertEquals(ActionCatalog.LONG_PRESS, store.getMouseAction());
        assertEquals("A fresh store exposes the four starter mappings", 4, store.load().size());
        assertShortcut(store.load(), KeyEvent.KEYCODE_B, ActionCatalog.BACK);
        assertShortcut(store.load(), KeyEvent.KEYCODE_H, ActionCatalog.HOME);
        assertShortcut(store.load(), KeyEvent.KEYCODE_O, ActionCatalog.RECENTS);
        assertShortcut(store.load(), KeyEvent.KEYCODE_N, ActionCatalog.NOTIFICATIONS);

        store.upsert(new ShortcutRule("qa_custom", KeyEvent.KEYCODE_K,
            KeyEvent.META_CTRL_LEFT_ON | KeyEvent.META_ALT_LEFT_ON,
            ActionCatalog.LAUNCH_APP, "com.android.settings", true));
        ShortcutRule added = ruleById(shortcutStore(), "qa_custom");
        assertNotNull("A newly opened store must load the added mapping", added);
        assertEquals(CTRL_ALT, added.modifiers);
        assertEquals("com.android.settings", added.packageName);
        assertEquals(ActionCatalog.LAUNCH_APP, added.actionId);
        assertTrue(added.enabled);

        shortcutStore().upsert(new ShortcutRule("qa_custom", KeyEvent.KEYCODE_J,
            CTRL_ALT, ActionCatalog.TOGGLE_MOUSE, null, false));
        ShortcutRule edited = ruleById(shortcutStore(), "qa_custom");
        assertEquals("Editing a rule must replace it without adding a second row", 5, shortcutStore().load().size());
        assertEquals(KeyEvent.KEYCODE_J, edited.keyCode);
        assertEquals(ActionCatalog.TOGGLE_MOUSE, edited.actionId);
        assertFalse("The disabled state must survive reopening the store", edited.enabled);
        assertNull(edited.packageName);
        store.setKeyboardEnabled(false);
        store.setMouseAction(ActionCatalog.HOME);
        assertFalse(shortcutStore().isKeyboardEnabled());
        assertEquals(ActionCatalog.HOME, shortcutStore().getMouseAction());

        shortcutStore().delete("qa_custom");
        assertNull("Deleting a mapping must persist", ruleById(shortcutStore(), "qa_custom"));
        for (ShortcutRule rule : shortcutStore().load()) shortcutStore().delete(rule.id);
        assertTrue("An intentionally emptied list must not resurrect default mappings", shortcutStore().load().isEmpty());
    }

    public void testShortcutStoreRejectsDuplicateAndReservedChords() {
        ShortcutStore store = shortcutStore();
        expectInvalidRule(store, new ShortcutRule("qa_duplicate", KeyEvent.KEYCODE_B,
            CTRL_ALT, ActionCatalog.HOME, null, true));
        store.upsert(new ShortcutRule("qa_disabled", KeyEvent.KEYCODE_K,
            CTRL_ALT, ActionCatalog.TOGGLE_MOUSE, null, false));
        expectInvalidRule(store, new ShortcutRule("qa_disabled_duplicate", KeyEvent.KEYCODE_K,
            CTRL_ALT, ActionCatalog.HOME, null, true));
        expectInvalidRule(store, new ShortcutRule("qa_reserved", KeyEvent.KEYCODE_R,
            CTRL_ALT, ActionCatalog.HOME, null, true));
        expectInvalidRule(store, new ShortcutRule("qa_reserved_shift", KeyEvent.KEYCODE_R,
            CTRL_ALT | KeyEvent.META_SHIFT_ON, ActionCatalog.HOME, null, true));
        expectInvalidRule(store, new ShortcutRule("qa_plain_key", KeyEvent.KEYCODE_K,
            0, ActionCatalog.HOME, null, true));
        assertEquals("Rejected updates must leave saved mappings intact", 5, shortcutStore().load().size());
    }

    public void testCustomShortcutWorksWhileMouseOffAndConsumesRepeatAndRelease() throws Exception {
        shortcutStore().upsert(toggleRule(true));
        installHarness();
        instrumentation.runOnMainSync(() -> editor.requestFocus());
        assertFalse(modeEnabled());
        hardwareChordWithRepeat(37);
        waitForMode(true, 3000);
        waitForConsumedKeysCleared(3000);
        assertTrue("A custom chord with one explicit repeat must toggle exactly once while mouse capture was off", modeEnabled());
        assertEquals("Mapped K DOWN/repeat/UP must not leak after modifiers release", 0, leakedK.get());
        hardwareChord(37);
        waitForMode(false, 3000);
        waitForConsumedKeysCleared(3000);
        assertFalse("The next independent custom shortcut must toggle capture off", modeEnabled());
        assertEquals(0, leakedK.get());

        shortcutStore().upsert(toggleRule(false));
        hardwareChord(37);
        waitForCount(leakedKUp, 1, 3000);
        assertFalse("A disabled mapping must not run", modeEnabled());
        assertEquals("A disabled chord must deliver one DOWN", 1, leakedKDown.get());
        assertEquals("A disabled chord must deliver one UP", 1, leakedKUp.get());
        assertEquals("A disabled chord must reach the focused app", 2, leakedK.get());
    }

    public void testKeyboardMasterOffPreservesEmergencyMouseShortcut() throws Exception {
        shortcutStore().upsert(toggleRule(true));
        shortcutStore().setKeyboardEnabled(false);
        installHarness();
        instrumentation.runOnMainSync(() -> editor.requestFocus());
        hardwareChord(37);
        waitForCount(leakedKUp, 1, 3000);
        assertFalse("The keyboard master switch must disable custom mappings", modeEnabled());
        assertEquals("Disabled mappings must pass through to the app", 2, leakedK.get());
        assertEquals(1, leakedKDown.get());
        assertEquals(1, leakedKUp.get());
        shortcutWithModifiersReleasedFirst();
        waitForMode(true, 3000);
        waitForConsumedKeysCleared(3000);
        assertTrue("Ctrl+Alt+R must remain available while keyboard mappings are off", modeEnabled());
        shortcutWithModifiersReleasedFirst();
        waitForMode(false, 3000);
        waitForConsumedKeysCleared(3000);
        assertFalse(modeEnabled());
        assertEquals("The reserved shortcut must still consume its key stream", 0, leakedR.get());
    }

    public void testRecordingGuardPassesNormalAndMappedKeysWithoutActions() throws Exception {
        shortcutStore().upsert(toggleRule(true));
        installHarness();
        instrumentation.runOnMainSync(() -> {
            editor.requestFocus();
            service.setShortcutRecording(true);
        });
        try {
            keyStroke(KeyEvent.KEYCODE_A);
            waitForEditorText("a", 3000);
            AtomicReference<String> text = new AtomicReference<>();
            instrumentation.runOnMainSync(() -> text.set(editor.getText().toString()));
            assertEquals("Normal typing must reach the recording view", "a", text.get());
            hardwareChord(37);
            shortcutWithModifiersReleasedFirst();
            waitForCount(leakedKUp, 1, 3000);
            waitForCount(leakedRUp, 1, 3000);
            assertFalse("Neither mapped nor reserved actions may run during recording", modeEnabled());
            assertEquals("Both mapped-key edges must reach the focused recording view", 2, leakedK.get());
            assertEquals("The emergency chord must also be recordable without firing", 2, leakedR.get());
            assertEquals(1, leakedKDown.get());
            assertEquals(1, leakedKUp.get());
            assertEquals(1, leakedRDown.get());
            assertEquals(1, leakedRUp.get());
        } finally {
            instrumentation.runOnMainSync(() -> service.setShortcutRecording(false));
        }
        hardwareChord(37);
        waitForMode(true, 3000);
        waitForConsumedKeysCleared(3000);
        assertTrue("Finishing recording must restore normal shortcut dispatch", modeEnabled());
        assertEquals("The restored mapping must consume its key", 2, leakedK.get());
    }

    public void testAppLaunchShortcutOpensSettings() throws Exception {
        Context context = instrumentation.getTargetContext();
        assertNotNull("This emulator must expose Settings as a launchable app",
            context.getPackageManager().getLaunchIntentForPackage("com.android.settings"));
        shortcutStore().upsert(new ShortcutRule("qa_launch_settings", KeyEvent.KEYCODE_K,
            CTRL_ALT, ActionCatalog.LAUNCH_APP, "com.android.settings", true));
        installHarness();
        instrumentation.runOnMainSync(() -> editor.requestFocus());
        try {
            hardwareChord(37);
            assertTrue("The app-launch action must foreground a different real app",
                waitForForegroundPackage("com.android.settings", 5000));
            assertFalse("Launching an app must not implicitly enable mouse capture", modeEnabled());
        } finally {
            launchActivity();
        }
        assertTrue("The fixture must return to its owned activity",
            waitForForegroundPackage(context.getPackageName(), 5000));
    }

    public void testConfiguredMouseHomeRunsOnDownWithoutLongPressFallback() throws Exception {
        shortcutStore().setMouseAction(ActionCatalog.HOME);
        installHarness();
        setMode(true);
        assertTrue("The fixture must own foreground focus before HOME", activityHasFocus());
        Field generationField = MouseLongPressService.class.getDeclaredField("gestureGeneration");
        generationField.setAccessible(true);
        AtomicInteger generationBefore = new AtomicInteger();
        instrumentation.runOnMainSync(() -> {
            try { generationBefore.set(generationField.getInt(service)); }
            catch (IllegalAccessException ex) { throw new AssertionError(ex); }
        });
        float[] center = centerOf(target);
        long downAt = secondaryDown(center);
        try {
            long deadline = SystemClock.uptimeMillis() + 2500;
            while (activityHasFocus() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(40);
            assertFalse("HOME must leave the foreground activity before mouse UP", activityHasFocus());
            assertEquals("A configured system action must not attempt LONG_CLICK", 0, semanticLongClickAttempts.get());
            assertEquals(0, longClicks.get());
        } finally {
            secondaryUp(downAt, center);
        }
        SystemClock.sleep(1000);
        assertEquals("Releasing a system-action click must not inject a fallback long-press", 0, longClicks.get());
        assertEquals(0, semanticLongClickAttempts.get());
        instrumentation.runOnMainSync(() -> {
            try {
                assertEquals("HOME press/release must not schedule a synthetic gesture on the launcher",
                    generationBefore.get(), generationField.getInt(service));
            } catch (IllegalAccessException ex) { throw new AssertionError(ex); }
        });
        assertTrue("The configured mouse action must preserve capture mode", modeEnabled());
        setMode(false);
        launchActivity();
    }

    public void testScreenTransitionCancelsRelayBeforeGestureSubmission() throws Exception {
        installHarness();
        setMode(true);
        float[] center = centerOf(target);
        AtomicReference<Boolean> scheduled = new AtomicReference<>();
        AtomicReference<Boolean> accepted = new AtomicReference<>();
        AtomicReference<Boolean> injectingAfter = new AtomicReference<>();
        AtomicReference<Object> deferredAfter = new AtomicReference<>();
        AtomicInteger activeBefore = new AtomicInteger();
        instrumentation.runOnMainSync(() -> {
            service.tap(center[0], center[1]);
            scheduled.set((Boolean) serviceFieldOnMain("injecting"));
            activeBefore.set((Integer) serviceFieldOnMain("activeGestureGeneration"));
            accepted.set(service.performShortcutAction(ActionCatalog.HOME, null));
            injectingAfter.set((Boolean) serviceFieldOnMain("injecting"));
            deferredAfter.set(serviceFieldOnMain("deferredShortcut"));
        });
        try {
            assertTrue("The fixture must have scheduled a relay in the same main-thread turn", scheduled.get());
            assertEquals("The 70ms-delayed touch must not yet have been submitted", -1, activeBefore.get());
            assertTrue("The screen transition should be accepted", accepted.get());
            assertFalse("The scheduled relay must be cancelled before changing screens", injectingAfter.get());
            assertNull("An unsubmitted relay must not defer the transition", deferredAfter.get());
            SystemClock.sleep(1000);
            assertFalse("The accepted HOME action must leave the original activity", activityHasFocus());
            assertEquals("The cancelled relay must not click the old target", 0, clicks.get());
            assertEquals(0, longClicks.get());
            assertEquals(-1, activeGestureGeneration());
            assertTrue("The capture overlay must remain available after cancelling a queued relay", captureOverlayAttached());
        } finally {
            waitForGestureIdle(2500);
            setMode(false);
            launchActivity();
        }
    }

    public void testScreenTransitionWaitsForSubmittedGestureAndRejectsSecondPendingAction() throws Exception {
        instrumentation.getTargetContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putInt("press_ms", 1200).commit();
        installHarness();
        setMode(true);
        float[] center = centerOf(target);
        instrumentation.runOnMainSync(() -> service.longPress(center[0], center[1]));
        long submittedDeadline = SystemClock.uptimeMillis() + 1500;
        while (activeGestureGeneration() < 0 && SystemClock.uptimeMillis() < submittedDeadline) SystemClock.sleep(20);
        assertTrue("The fixture must observe a real submitted long-press", activeGestureGeneration() >= 0);
        AtomicReference<Boolean> firstAccepted = new AtomicReference<>();
        AtomicReference<Boolean> secondAccepted = new AtomicReference<>();
        AtomicReference<Object> queued = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            firstAccepted.set(service.performShortcutAction(ActionCatalog.HOME, null));
            queued.set(serviceFieldOnMain("deferredShortcut"));
            secondAccepted.set(service.performShortcutAction(ActionCatalog.RECENTS, null));
        });
        try {
            assertTrue("One transition may wait for the active touch to finish", firstAccepted.get());
            assertNotNull("The accepted transition must be queued while the gesture is active", queued.get());
            assertFalse("Only one screen transition may be queued", secondAccepted.get());
            long deadline = SystemClock.uptimeMillis() + 3000;
            int observedActive = 0;
            AtomicInteger active = new AtomicInteger();
            AtomicReference<Boolean> focused = new AtomicReference<>();
            do {
                instrumentation.runOnMainSync(() -> {
                    active.set((Integer) serviceFieldOnMain("activeGestureGeneration"));
                    focused.set(activity.hasWindowFocus());
                });
                if (active.get() < 0) break;
                observedActive++;
                assertTrue("The target must retain foreground focus until gesture completion/cancellation", focused.get());
                SystemClock.sleep(20);
            } while (SystemClock.uptimeMillis() < deadline);
            assertTrue("The deferred interval must include an active submitted touch", observedActive > 0);
            assertEquals("The submitted gesture must eventually reach its completion callback", -1, activeGestureGeneration());
            long focusDeadline = SystemClock.uptimeMillis() + 2500;
            while (activityHasFocus() && SystemClock.uptimeMillis() < focusDeadline) SystemClock.sleep(40);
            assertFalse("The queued HOME action must run after the gesture finishes", activityHasFocus());
            assertEquals("The ongoing long-press must finish on its original target before HOME", 1, longClicks.get());
            assertTrue("The overlay must be restored after the deferred transition", captureOverlayAttached());

            launchActivity();
            installHarness();
            float[] nextCenter = centerOf(target);
            mouseClick(nextCenter[0], nextCenter[1], MotionEvent.BUTTON_PRIMARY);
            waitForCount(clicks, 1, 2000);
            assertEquals("A follow-up real mouse click must work after the deferred transition", 1, clicks.get());
            assertTrue(modeEnabled());
        } finally {
            waitForGestureIdle(2500);
            setMode(false);
            launchActivity();
        }
    }

    /** Read only from a runOnMainSync callback; these are observations, not test hooks. */
    private Object serviceFieldOnMain(String name) {
        try {
            Field field = MouseLongPressService.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(service);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private int activeGestureGeneration() {
        AtomicInteger active = new AtomicInteger();
        instrumentation.runOnMainSync(() -> active.set((Integer) serviceFieldOnMain("activeGestureGeneration")));
        return active.get();
    }

    private void waitForGestureIdle(long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (activeGestureGeneration() >= 0 && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(30);
    }

    private boolean captureOverlayAttached() {
        AtomicReference<Boolean> attached = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            View overlay = (View) serviceFieldOnMain("overlay");
            attached.set(Boolean.TRUE.equals(serviceFieldOnMain("overlayAttached"))
                && overlay != null && overlay.isAttachedToWindow());
        });
        return attached.get();
    }

    public void testUiRecordsSavesEditsAndDeletesShortcut() throws Exception {
        uiClickText("키보드");
        uiClickText("＋ 단축키 추가");
        uiAssertText("단축키 추가");
        uiClickText("키 조합 입력");
        uiAssertText("키 조합 입력 중…");
        hardwareChord(37); // Ctrl+Alt+K
        uiAssertText("Ctrl+Alt+K  ·  다시 입력");
        uiClickText("저장");
        ShortcutRule added = ruleByChord(KeyEvent.KEYCODE_K);
        assertNotNull("Saving the recorder must create a real persisted rule", added);
        assertEquals(ActionCatalog.BACK, added.actionId);
        assertTrue(added.enabled);

        uiClickText("Ctrl+Alt+K, 뒤로, 편집");
        uiAssertText("단축키 편집");
        uiClickText("Ctrl+Alt+K  ·  다시 입력");
        hardwareChord(36); // Ctrl+Alt+J
        uiAssertText("Ctrl+Alt+J  ·  다시 입력");
        uiClickText("뒤로  ›");
        uiClickText("마우스 변환 켜기/끄기");
        uiClickText("저장");
        ShortcutRule edited = ruleById(shortcutStore(), added.id);
        assertNotNull(edited);
        assertEquals("Editing through the card must preserve its identity", added.id, edited.id);
        assertEquals(KeyEvent.KEYCODE_J, edited.keyCode);
        assertEquals(ActionCatalog.TOGGLE_MOUSE, edited.actionId);
        assertNull(ruleByChord(KeyEvent.KEYCODE_K));
        assertEquals(5, shortcutStore().load().size());

        uiClickText("Ctrl+Alt+J, 마우스 변환 켜기/끄기, 편집");
        uiAssertText("단축키 편집");
        uiClickText("삭제");
        assertNull("The editor's delete action must remove the persisted rule", ruleById(shortcutStore(), added.id));
        assertEquals(4, shortcutStore().load().size());
    }

    public void testUiDuplicateErrorAndRecorderCancelKeepRulesUnchanged() throws Exception {
        uiClickText("키보드");
        uiClickText("＋ 단축키 추가");
        uiClickText("키 조합 입력");
        hardwareChord(48); // Ctrl+Alt+B already maps to BACK: recording must suppress it.
        uiAssertText("Ctrl+Alt+B  ·  다시 입력");
        uiClickText("저장");
        uiAssertText("이미 등록된 조합 · 꺼진 단축키 포함");
        uiAssertText("단축키 추가");
        assertEquals("A duplicate error must leave all saved mappings unchanged", 4, shortcutStore().load().size());
        uiClickText("취소");

        uiClickText("＋ 단축키 추가");
        uiClickText("키 조합 입력");
        uiAssertText("키 조합 입력 중…");
        uiClickText("취소");
        assertEquals("Cancelling an active recorder must not save a partial rule", 4, shortcutStore().load().size());
        shortcutWithModifiersReleasedFirst();
        waitForMode(true, 3000);
        waitForConsumedKeysCleared(3000);
        assertTrue("Closing the recording dialog must release the service's recording guard", modeEnabled());
        shortcutWithModifiersReleasedFirst();
        waitForMode(false, 3000);
        waitForConsumedKeysCleared(3000);
        assertFalse(modeEnabled());
    }

    public void testUiAppPickerSavesAndLaunchesSelectedSettingsApp() throws Exception {
        String pickerLabel = settingsLauncherLabel();
        uiClickText("키보드");
        uiClickText("＋ 단축키 추가");
        uiClickText("키 조합 입력");
        hardwareChord(37);
        uiAssertText("Ctrl+Alt+K  ·  다시 입력");
        uiClickText("뒤로  ›");
        uiClickText("앱 실행");
        uiClickText("실행할 앱 선택  ›");
        uiAssertText("실행할 앱 선택");
        uiClickText(pickerLabel);
        uiClickText("저장");
        ShortcutRule created = ruleByChord(KeyEvent.KEYCODE_K);
        assertNotNull("The app picker must save a runnable shortcut", created);
        assertEquals(ActionCatalog.LAUNCH_APP, created.actionId);
        assertEquals("com.android.settings", created.packageName);
        try {
            hardwareChord(37);
            assertTrue("The UI-selected app must actually open from the recorded hardware chord",
                waitForForegroundPackage("com.android.settings", 5000));
        } finally {
            launchActivity();
        }
    }

    private ShortcutRule ruleByChord(int keyCode) {
        for (ShortcutRule rule : shortcutStore().load()) {
            if (rule.keyCode == keyCode && rule.modifiers == CTRL_ALT) return rule;
        }
        return null;
    }

    private String settingsLauncherLabel() {
        Context context = instrumentation.getTargetContext();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo candidate : context.getPackageManager().queryIntentActivities(launcher, 0)) {
            if ("com.android.settings".equals(candidate.activityInfo.packageName)) {
                return candidate.loadLabel(context.getPackageManager()).toString();
            }
        }
        throw new AssertionError("The dedicated emulator must list Settings in the real launcher app picker");
    }

    private void uiAssertText(String value) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            if (uiTextBounds(value, false) != null) return;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        captureUiFailure();
        fail("Visible UI text was not found: " + value);
    }

    private void uiClickText(String value) {
        // The UI flow uses Android's semantic click on the actual visible control.
        // Physical pointer relay is exercised separately by the mouse/touch tests;
        // do not let animated dialog coordinates obscure recorder/persistence tests.
        for (int attempt = 0; attempt < 30; attempt++) {
            AccessibilityNodeInfo root = automation.getRootInActiveWindow();
            if (root != null) {
                List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText(value);
                boolean accepted = false;
                try {
                    for (AccessibilityNodeInfo node : matches) {
                        node.refresh();
                        if (!value.equals(String.valueOf(node.getText()))
                                && !value.equals(String.valueOf(node.getContentDescription()))) continue;
                        if (!node.isVisibleToUser() || !node.isEnabled()) {
                            node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
                            continue;
                        }
                        AccessibilityNodeInfo clickable = AccessibilityNodeInfo.obtain(node);
                        try {
                            for (int depth = 0; clickable != null && depth < 4; depth++) {
                                if (clickable.isClickable() && clickable.isEnabled()) {
                                    accepted = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    break;
                                }
                                AccessibilityNodeInfo parent = clickable.getParent();
                                clickable.recycle(); clickable = parent;
                            }
                        } finally { if (clickable != null) clickable.recycle(); }
                        if (accepted) break;
                    }
                } finally {
                    for (AccessibilityNodeInfo node : matches) node.recycle();
                    root.recycle();
                }
                if (accepted) {
                    instrumentation.waitForIdleSync();
                    SystemClock.sleep(100);
                    return;
                }
            }
            if (attempt > 0 && attempt % 4 == 3) scrollActiveUiForward();
            SystemClock.sleep(100);
        }
        captureUiFailure();
        fail("Could not reach and click UI control: " + value);
    }

    private void captureUiFailure() {
        Bitmap screenshot = automation.takeScreenshot();
        if (screenshot == null) return;
        File file = new File(instrumentation.getTargetContext().getCacheDir(), "rightpress-ui-failure.png");
        try (FileOutputStream output = new FileOutputStream(file)) {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
        } catch (IOException failure) {
            android.util.Log.w("RightPressTest", "Could not save UI failure screenshot", failure);
        } finally { screenshot.recycle(); }
    }

    /** Resolves the current real window, including dialogs, without private UI fields. */
    private Rect uiTextBounds(String value, boolean reveal) {
        AccessibilityNodeInfo root = automation.getRootInActiveWindow();
        if (root == null) return null;
        List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText(value);
        Rect found = null;
        try {
            for (AccessibilityNodeInfo node : matches) {
                if (!value.equals(String.valueOf(node.getText()))
                        && !value.equals(String.valueOf(node.getContentDescription()))) continue;
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                if (node.isVisibleToUser() && !bounds.isEmpty()) {
                    found = bounds;
                    break;
                }
                if (reveal) node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
            }
        } finally {
            for (AccessibilityNodeInfo node : matches) node.recycle();
            root.recycle();
        }
        return found;
    }

    private void scrollActiveUiForward() {
        AccessibilityNodeInfo root = automation.getRootInActiveWindow();
        if (root == null) return;
        try { scrollFirstContainer(root, 0); }
        finally { root.recycle(); }
    }

    private boolean scrollFirstContainer(AccessibilityNodeInfo node, int depth) {
        if (depth > 20) return false;
        if (node.isScrollable() && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try { if (scrollFirstContainer(child, depth + 1)) return true; }
            finally { child.recycle(); }
        }
        return false;
    }

    private ShortcutStore shortcutStore() { return new ShortcutStore(instrumentation.getTargetContext()); }

    private ShortcutRule toggleRule(boolean enabled) {
        return new ShortcutRule("qa_toggle", KeyEvent.KEYCODE_K, CTRL_ALT,
            ActionCatalog.TOGGLE_MOUSE, null, enabled);
    }

    private ShortcutRule ruleById(ShortcutStore store, String id) {
        for (ShortcutRule rule : store.load()) if (id.equals(rule.id)) return rule;
        return null;
    }

    private void assertShortcut(List<ShortcutRule> rules, int keyCode, String actionId) {
        for (ShortcutRule rule : rules) {
            if (rule.keyCode == keyCode && rule.modifiers == CTRL_ALT && rule.enabled
                    && actionId.equals(rule.actionId)) return;
        }
        fail("Missing default shortcut " + keyCode + " -> " + actionId);
    }

    private void expectInvalidRule(ShortcutStore store, ShortcutRule rule) {
        try {
            store.upsert(rule);
            fail("Conflicting or reserved rule was accepted: " + rule.id);
        } catch (IllegalArgumentException expected) {
            assertNotNull("Invalid mappings should provide a user-facing reason", expected.getMessage());
        }
    }

    private boolean waitForForegroundPackage(String expected, long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        do {
            AccessibilityNodeInfo root = automation.getRootInActiveWindow();
            if (root != null) {
                boolean matched = expected.equals(String.valueOf(root.getPackageName()));
                root.recycle();
                if (matched) return true;
            }
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        return false;
    }

    private boolean activityHasFocus() {
        AtomicReference<Boolean> focused = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> focused.set(activity.hasWindowFocus()));
        return focused.get();
    }

    private void waitForMode(boolean expected, long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (modeEnabled() != expected && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(30);
    }

    private void launchActivity() {
        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class);
        // A null action passes IntentFilter's action check and would let the
        // Settings-blocking monitor intercept this launch, deadlocking startActivitySync.
        intent.setAction(Intent.ACTION_MAIN);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = (MainActivity) instrumentation.startActivitySync(intent);
        instrumentation.waitForIdleSync();
    }

    private void rebindEnabledService() throws IOException { rebindEnabledService(true); }

    private void rebindEnabledService(boolean expectAccepted) throws IOException {
        String originalServices = shell("settings get secure enabled_accessibility_services").trim();
        String originalEnabled = shell("settings get secure accessibility_enabled").trim();
        assertTrue("Unexpected accessibility service component-list format",
            originalServices.equals("null") || originalServices.isEmpty()
                || originalServices.matches("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+(:[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+)*"));
        assertTrue("Unexpected accessibility_enabled value",
            originalEnabled.equals("null") || originalEnabled.equals("0") || originalEnabled.equals("1"));
        ComponentName expected = new ComponentName(instrumentation.getTargetContext(), MouseLongPressService.class);
        StringBuilder retained = new StringBuilder();
        boolean found = false;
        for (String component : originalServices.split(":")) {
            if (expected.equals(ComponentName.unflattenFromString(component))) {
                found = true;
            } else if (!component.isEmpty() && !component.equals("null")) {
                if (retained.length() != 0) retained.append(':');
                retained.append(component);
            }
        }
        assertTrue("The emulator fixture must already have explicitly enabled " + expected.flattenToShortString(), found);
        try {
            // UiAutomation executes a command directly; shell quote characters would
            // become literal settings content. Validated component lists are one token.
            if (retained.length() == 0) {
                shell("settings delete secure enabled_accessibility_services");
            } else {
                shell("settings put secure enabled_accessibility_services " + retained);
            }
            long deadline = SystemClock.uptimeMillis() + 5000;
            AtomicReference<MouseLongPressService> remaining = new AtomicReference<>();
            do {
                instrumentation.runOnMainSync(() -> remaining.set(MouseLongPressService.getInstance()));
                if (remaining.get() == null) break;
                SystemClock.sleep(50);
            } while (SystemClock.uptimeMillis() < deadline);
            assertNull("The exact service must disconnect before the fixture restores it", remaining.get());
        } finally {
            // Preserve all other enabled services and restore the exact original list.
            shell("settings put secure enabled_accessibility_services " + originalServices);
            if (originalEnabled.equals("null")) {
                shell("settings delete secure accessibility_enabled");
            } else {
                shell("settings put secure accessibility_enabled " + originalEnabled);
            }
        }
        if (expectAccepted) assertEquals("The fixture must restore the enabled accessibility service list",
            originalServices, shell("settings get secure enabled_accessibility_services").trim());
    }

    private String shell(String command) throws IOException {
        ParcelFileDescriptor[] descriptors = automation.executeShellCommandRwe(command);
        descriptors[1].close();
        try {
            String output = readDescriptor(descriptors[0]);
            String errors = readDescriptor(descriptors[2]);
            if (!errors.trim().isEmpty()) throw new IOException("Shell command failed: " + command + "\n" + errors);
            return output;
        } finally {
            for (ParcelFileDescriptor descriptor : descriptors) {
                try { descriptor.close(); } catch (IOException ignored) { }
            }
        }
    }

    private String readDescriptor(ParcelFileDescriptor descriptor) throws IOException {
        try (ParcelFileDescriptor.AutoCloseInputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private void installHarness() {
        installHarness(SEMANTIC_NORMAL);
    }

    private void installHarness(int semanticBehavior) {
        instrumentation.runOnMainSync(() -> {
            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(60), dp(90), dp(60), dp(30));
            target = new Button(activity);
            target.setText("RightPress underlying click target");
            target.setOnClickListener(view -> clicks.incrementAndGet());
            target.setOnLongClickListener(view -> { longClicks.incrementAndGet(); return true; });
            target.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    if (semanticBehavior == SEMANTIC_HIDDEN_ACCEPT) {
                        info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK);
                        info.setLongClickable(false);
                    }
                }

                @Override public boolean performAccessibilityAction(View host, int action, Bundle arguments) {
                    if (action == AccessibilityNodeInfo.ACTION_LONG_CLICK) {
                        semanticLongClickAttempts.incrementAndGet();
                        if (semanticBehavior == SEMANTIC_REJECT) return false;
                        if (semanticBehavior == SEMANTIC_HIDDEN_ACCEPT) return host.performLongClick();
                    }
                    return super.performAccessibilityAction(host, action, arguments);
                }
            });
            root.addView(target, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(130)));
            editor = new EditText(activity) {
                @Override public boolean onKeyPreIme(int keyCode, KeyEvent event) {
                    // Observe real hardware edges after AccessibilityInputFilter,
                    // before the IME or editor shortcut machinery can consume a
                    // DOWN without forwarding it to an OnKeyListener. Consuming
                    // these fixture-only probe keys also prevents double counting.
                    if (keyCode == KeyEvent.KEYCODE_K || keyCode == KeyEvent.KEYCODE_R) {
                        recordProbeKey(keyCode, event);
                        return true;
                    }
                    return super.onKeyPreIme(keyCode, event);
                }
            };
            editor.setSingleLine(true);
            editor.setShowSoftInputOnFocus(false);
            editor.setHint("Hardware keyboard input target");
            root.addView(editor, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
            scrollTarget = new ScrollView(activity);
            TextView rows = new TextView(activity);
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < 80; i++) content.append("Underlying scroll target row ").append(i).append('\n');
            rows.setText(content);
            rows.setTextSize(20);
            scrollTarget.addView(rows);
            root.addView(scrollTarget, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230)));
            activity.setContentView(root);
        });
        instrumentation.waitForIdleSync();
        settleWindows();
    }

    private void recordProbeKey(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_R) {
            leakedR.incrementAndGet();
            if (event.getAction() == KeyEvent.ACTION_DOWN) leakedRDown.incrementAndGet();
            if (event.getAction() == KeyEvent.ACTION_UP) leakedRUp.incrementAndGet();
        } else if (keyCode == KeyEvent.KEYCODE_K) {
            leakedK.incrementAndGet();
            if (event.getAction() == KeyEvent.ACTION_DOWN) leakedKDown.incrementAndGet();
            if (event.getAction() == KeyEvent.ACTION_UP) leakedKUp.incrementAndGet();
        }
    }

    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

    private void setMode(boolean enabled) {
        instrumentation.runOnMainSync(() -> service.enableMode(enabled));
        instrumentation.waitForIdleSync();
        settleWindows();
    }

    private void settleWindows() {
        // Main-thread idleness does not mean the newly added input window has
        // reached AccessibilityManager/InputDispatcher. Wait for the observed
        // window-event stream to settle before injecting a physical pointer.
        try { automation.waitForIdle(250, 5000); }
        catch (TimeoutException timeout) { fail("Fixture windows did not settle before input: " + timeout); }
    }

    private boolean modeEnabled() {
        AtomicReference<Boolean> enabled = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> enabled.set(service.isModeEnabled()));
        return enabled.get();
    }

    private boolean savedMode() {
        return instrumentation.getTargetContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("mode_enabled", false);
    }

    private void reconnectService() throws IOException {
        MouseLongPressService previous = service;
        rebindEnabledService();
        long deadline = SystemClock.uptimeMillis() + 10000;
        do {
            instrumentation.runOnMainSync(() -> service = MouseLongPressService.getInstance());
            if (service != null && service != previous) break;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        assertNotNull("The accessibility service must reconnect", service);
        assertNotSame("Lifecycle checks require a new service instance", previous, service);
        instrumentation.waitForIdleSync();
        SystemClock.sleep(150);
    }

    private float[] centerOf(View view) {
        AtomicReference<float[]> center = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            assertTrue("The pointer target must be laid out before measuring", view.isLaidOut());
            assertTrue("The pointer target must have a nonempty size", view.getWidth() > 0 && view.getHeight() > 0);
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            center.set(new float[]{location[0] + view.getWidth() / 2f, location[1] + view.getHeight() / 2f});
        });
        return center.get();
    }

    /** Failure-only observation; does not retry input, wait for recovery, or change state. */
    private String primaryPointerFailureState(float[] injectedCenter, int observedClicks) {
        AtomicReference<String> state = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            Rect targetBounds = new Rect();
            boolean targetVisible = target.getGlobalVisibleRect(targetBounds);
            int[] targetLocation = new int[2];
            target.getLocationOnScreen(targetLocation);
            View overlay = (View) serviceFieldOnMain("overlay");
            Rect overlayBounds = new Rect();
            boolean overlayVisible = overlay != null && overlay.getGlobalVisibleRect(overlayBounds);
            state.set("Primary pointer failure: observedClicks=" + observedClicks
                + ", currentClicks=" + clicks.get() + ", longClicks=" + longClicks.get()
                + ", injectedCenter=" + injectedCenter[0] + "," + injectedCenter[1]
                + "; activityFocus=" + activity.hasWindowFocus()
                + ", finishing=" + activity.isFinishing() + ", destroyed=" + activity.isDestroyed()
                + "; targetAttached=" + target.isAttachedToWindow() + ", shown=" + target.isShown()
                + ", laidOut=" + target.isLaidOut() + ", windowFocus=" + target.hasWindowFocus()
                + ", visibleRect=" + targetVisible + ":" + targetBounds
                + ", location=" + targetLocation[0] + "," + targetLocation[1]
                + ", size=" + target.getWidth() + "x" + target.getHeight()
                + ", enabled=" + target.isEnabled() + ", pressed=" + target.isPressed()
                + "; mode=" + service.isModeEnabled()
                + ", overlayAttachedFlag=" + serviceFieldOnMain("overlayAttached")
                + ", overlayAttached=" + (overlay != null && overlay.isAttachedToWindow())
                + ", overlayShown=" + (overlay != null && overlay.isShown())
                + ", overlayRect=" + overlayVisible + ":" + overlayBounds
                + ", injecting=" + serviceFieldOnMain("injecting")
                + ", gestureGeneration=" + serviceFieldOnMain("gestureGeneration")
                + ", activeGestureGeneration=" + serviceFieldOnMain("activeGestureGeneration")
                + ", deferredShortcut=" + (serviceFieldOnMain("deferredShortcut") != null));
        });
        android.util.Log.e("RightPressTest", state.get());
        return state.get();
    }

    private boolean containsText(String value) {
        AtomicReference<Boolean> found = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> found.set(findText(activity.getWindow().getDecorView(), value)));
        return found.get();
    }

    private boolean findText(View view, String value) {
        if (view instanceof TextView && value.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) view;
            for (int i = 0; i < parent.getChildCount(); i++) if (findText(parent.getChildAt(i), value)) return true;
        }
        return false;
    }

    private void waitForCount(AtomicInteger counter, int expected, long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (counter.get() < expected && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(30);
    }

    private void waitForEditorText(String expected, long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        AtomicReference<String> actual = new AtomicReference<>();
        do {
            instrumentation.runOnMainSync(() -> actual.set(editor.getText().toString()));
            if (expected.equals(actual.get())) return;
            SystemClock.sleep(30);
        } while (SystemClock.uptimeMillis() < deadline);
    }

    private void waitForConsumedKeysCleared(long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        AtomicReference<Boolean> cleared = new AtomicReference<>();
        do {
            instrumentation.runOnMainSync(() -> cleared.set(((Set<?>) serviceFieldOnMain("consumedKeys")).isEmpty()));
            if (cleared.get()) return;
            SystemClock.sleep(30);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("The service must consume the final key UP and release its tracked key stream");
    }

    private void shortcutWithModifiersReleasedFirst() throws IOException {
        // Linux evdev KEY_LEFTCTRL=29, KEY_LEFTALT=56, KEY_R=19. InputReader
        // computes the modifier state, matching a connected hardware keyboard.
        hardwareChord(19);
    }

    private void hardwareChord(int linuxKeyCode) throws IOException {
        injectHardwareKeys(new int[][]{{29, 1}, {56, 1}, {linuxKeyCode, 1},
            {29, 0}, {56, 0}, {linuxKeyCode, 0}});
        SystemClock.sleep(120);
    }

    private void hardwareChordWithRepeat(int linuxKeyCode) throws IOException {
        // EV_KEY value 2 is one explicit repeat. Every edge, including modifier
        // releases before the main key UP, is written by the same root process.
        injectHardwareKeys(new int[][]{{29, 1}, {56, 1}, {linuxKeyCode, 1},
            {linuxKeyCode, 2}, {29, 0}, {56, 0}, {linuxKeyCode, 0}});
        SystemClock.sleep(120);
    }

    private void keyStroke(int code) throws IOException {
        int linuxCode;
        if (code == KeyEvent.KEYCODE_A) linuxCode = 30;
        else if (code == KeyEvent.KEYCODE_B) linuxCode = 48;
        else throw new IllegalArgumentException("Unsupported hardware test key: " + code);
        injectHardwareKeys(new int[][]{{linuxCode, 1}, {linuxCode, 0}});
    }

    private void ensureEmulatorKeyboard() throws IOException {
        if (emulatorKeyboard == null) {
            assertEquals("Evdev keyboard testing is restricted to an Android emulator", "1",
                shell("getprop ro.kernel.qemu").trim());
            assertTrue("The dedicated emulator must permit its built-in su for evdev input",
                shell("su 0 id").contains("uid=0("));
            String abi = shell("getprop ro.product.cpu.abi").trim();
            assertTrue("The input_event batch is restricted to a 64-bit little-endian emulator",
                abi.equals("arm64-v8a") || abi.equals("x86_64"));
            String devices = shell("getevent -lp");
            Matcher keyboard = Pattern.compile("add device \\d+: (/dev/input/event\\d+)\\s+name:\\s+\"qwerty2\"")
                .matcher(devices);
            assertTrue("The emulator must expose a qwerty2 keyboard", keyboard.find());
            emulatorKeyboard = keyboard.group(1);
        }
    }

    private void injectHardwareKeys(int[][] keys) throws IOException {
        ensureEmulatorKeyboard();
        // UiAutomation.injectInputEvent(KEY) skips AccessibilityInputFilter in
        // AOSP. Keep the real evdev path, but batch a complete stream so host
        // scheduling between separate su/sendevent processes cannot hold a key
        // down for seconds and manufacture unrelated automatic repeats.
        ByteBuffer events = ByteBuffer.allocate(keys.length * 2 * 24).order(ByteOrder.LITTLE_ENDIAN);
        for (int[] key : keys) {
            if (key.length != 2 || key[0] <= 0 || key[0] > 767 || key[1] < 0 || key[1] > 2) {
                throw new IllegalArgumentException("Invalid fixture evdev key edge");
            }
            putInputEvent(events, 1, key[0], key[1]); // EV_KEY
            putInputEvent(events, 0, 0, 0); // EV_SYN / SYN_REPORT
        }
        File input = File.createTempFile("rightpress-evdev-", ".bin", instrumentation.getTargetContext().getCacheDir());
        try {
            try (FileOutputStream output = new FileOutputStream(input)) { output.write(events.array()); }
            String path = input.getAbsolutePath();
            assertTrue("Only the owned fixture path may enter the root input command", path.matches("[A-Za-z0-9_./-]+"));
            long started = SystemClock.uptimeMillis();
            rootInputScript("set -e\ncat " + path + " > " + emulatorKeyboard + "\n");
            android.util.Log.i("RightPressTest", "evdev batch edges=" + keys.length
                + " write_ms=" + (SystemClock.uptimeMillis() - started));
        } finally {
            input.delete();
        }
    }

    private void putInputEvent(ByteBuffer output, int type, int code, int value) {
        // 64-bit Linux input_event: timeval (2 * int64), uint16 type/code, int32 value.
        // Timestamps are zero, matching sendevent; the input subsystem timestamps delivery.
        output.putLong(0L).putLong(0L).putShort((short) type).putShort((short) code).putInt(value);
    }

    private void rootInputScript(String script) throws IOException {
        // Feed the script over stdin: UiAutomation does not interpret shell quotes
        // in its command argument. This launches one root shell for the whole stream.
        ParcelFileDescriptor[] descriptors = automation.executeShellCommandRwe("su 0 sh");
        try {
            try (ParcelFileDescriptor.AutoCloseOutputStream input = new ParcelFileDescriptor.AutoCloseOutputStream(descriptors[1])) {
                input.write(script.getBytes(StandardCharsets.UTF_8));
            }
            String output = readDescriptor(descriptors[0]);
            String errors = readDescriptor(descriptors[2]);
            if (!errors.trim().isEmpty() || !output.trim().isEmpty()) {
                throw new IOException("Evdev batch write failed: " + output + errors);
            }
        } finally {
            for (ParcelFileDescriptor descriptor : descriptors) {
                try { descriptor.close(); } catch (IOException ignored) { }
            }
        }
    }

    /** Uses the public mouse ACTION_DOWN/buttonState route handled by the app. */
    private void mouseClick(float x, float y, int button) {
        mouseClickHeld(x, y, button, 20);
    }

    private void mouseClickHeld(float x, float y, int button, long holdMs) {
        long downAt = SystemClock.uptimeMillis();
        injectPointer(downAt, MotionEvent.ACTION_HOVER_MOVE, x, y, 0, InputDevice.SOURCE_MOUSE);
        injectPointer(downAt, MotionEvent.ACTION_DOWN, x, y, button, InputDevice.SOURCE_MOUSE);
        SystemClock.sleep(holdMs);
        injectPointer(downAt, MotionEvent.ACTION_UP, x, y, 0, InputDevice.SOURCE_MOUSE);
    }

    private long secondaryDown(float[] center) {
        long downAt = SystemClock.uptimeMillis();
        injectPointer(downAt, MotionEvent.ACTION_HOVER_MOVE, center[0], center[1], 0, InputDevice.SOURCE_MOUSE);
        injectPointer(downAt, MotionEvent.ACTION_DOWN, center[0], center[1], MotionEvent.BUTTON_SECONDARY, InputDevice.SOURCE_MOUSE);
        return downAt;
    }

    private void secondaryUp(long downAt, float[] center) {
        injectPointer(downAt, MotionEvent.ACTION_UP, center[0], center[1], 0, InputDevice.SOURCE_MOUSE);
    }

    private void touchTap(float x, float y) {
        long downAt = SystemClock.uptimeMillis();
        injectPointer(downAt, MotionEvent.ACTION_DOWN, x, y, 0, InputDevice.SOURCE_TOUCHSCREEN);
        SystemClock.sleep(20);
        injectPointer(downAt, MotionEvent.ACTION_UP, x, y, 0, InputDevice.SOURCE_TOUCHSCREEN);
    }

    private void injectPointer(long downAt, int action, float x, float y, int buttons, int source) {
        injectPointer(downAt, action, x, y, buttons, source, 0);
    }

    private void injectPointer(long downAt, int action, float x, float y, int buttons, int source, float wheel) {
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = 0;
        properties.toolType = source == InputDevice.SOURCE_MOUSE ? MotionEvent.TOOL_TYPE_MOUSE : MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords coordinates = new MotionEvent.PointerCoords();
        coordinates.x = x;
        coordinates.y = y;
        coordinates.pressure = action == MotionEvent.ACTION_UP ? 0 : 1;
        coordinates.size = 1;
        if (wheel != 0) coordinates.setAxisValue(MotionEvent.AXIS_VSCROLL, wheel);
        MotionEvent event = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action, 1,
            new MotionEvent.PointerProperties[]{properties}, new MotionEvent.PointerCoords[]{coordinates},
            0, buttons, 1, 1, 0, 0, source, 0);
        try { assertTrue("Pointer injection failed: " + action, automation.injectInputEvent(event, true)); }
        finally { event.recycle(); }
    }
}
