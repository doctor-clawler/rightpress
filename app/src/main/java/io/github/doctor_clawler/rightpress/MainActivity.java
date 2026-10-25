package io.github.doctor_clawler.rightpress;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Local settings UI. Input is recorded only while the explicit recorder is active. */
public final class MainActivity extends Activity {
    private static final int INK = 0xFFF0F2F5, MUTED = 0xFFA3ABB7, BLUE = 0xFF4B82FF;
    private static final int BG = 0xFF14171C, RAIL = 0xFF0F1217, LINE = 0xFF343B46;
    private static final int SURFACE = 0xFF222832, BRIGHT = 0xFF87AEFF;
    private ShortcutStore store;
    private TextView connection, mouseStatus, keyboardStatus, modeValue;
    private Button setup;
    private LinearLayout mouseTab, keyboardTab;
    private ConsoleIconView mouseTabIcon, keyboardTabIcon;
    private TextView mouseTabLabel, keyboardTabLabel;
    private Switch mouseSwitch, keyboardSwitch;
    private MouseDiagramView mouseDiagram;
    private LinearLayout content;
    private ScrollView page;
    private boolean tablet, wide;
    private int selectedTab;
    private boolean refreshing;
    private Editor editor;
    private AlertDialog consentDialog;
    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refresh(); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new ShortcutStore(this);
        selectedTab = state == null ? 0 : state.getInt("tab", 0);
        int width = getResources().getConfiguration().screenWidthDp;
        tablet = width >= 600;
        wide = width >= 1100;
        LinearLayout outer = tablet ? row() : column();
        outer.setGravity(Gravity.TOP);
        outer.setBackgroundColor(BG);
        outer.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            outer.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        setContentView(outer);
        getWindow().getInsetsController().setSystemBarsAppearance(0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        LinearLayout nav = buildNavigation();
        if (tablet) outer.addView(nav, new LinearLayout.LayoutParams(dp(92), -1));
        LinearLayout workspace = column();
        int side = tablet ? Math.max(32, (width - 92 - 1080) / 2) : 22;
        workspace.setPadding(dp(side), dp(12), dp(side), 0);
        if (tablet) outer.addView(workspace, new LinearLayout.LayoutParams(0, -1, 1));
        else outer.addView(workspace, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout brand = column();
        brand.addView(text("RightPress", tablet ? 34 : 30, INK, true));
        TextView edition = mono("INPUT CONSOLE", 14, MUTED);
        edition.setLetterSpacing(.14f);
        brand.addView(edition);
        // Reserve the overlay exit's top-right hit region; all controls begin below it.
        brand.setMinimumHeight(dp(CaptureOverlay.EXIT_TOUCH_HEIGHT_DP - 12));
        workspace.addView(brand, fill());
        LinearLayout service = row();
        connection = text("접근성 확인 중", 14, MUTED, false);
        service.addView(connection, new LinearLayout.LayoutParams(0, -2, 1));
        Button help = button("도움말", BG, MUTED);
        help.setOnClickListener(v -> showHelp());
        help.setMinWidth(0); help.setMinimumWidth(0);
        service.addView(help, new LinearLayout.LayoutParams(dp(76), dp(48)));
        workspace.addView(service, fill());
        setup = button("접근성 서비스 켜기", 0xFF2E63D6, Color.WHITE);
        setup.setOnClickListener(v -> openSettings());
        workspace.addView(setup, fill());
        divider(workspace);
        page = new ScrollView(this);
        page.setFillViewport(true);
        page.setClipToPadding(false);
        page.setVerticalScrollBarEnabled(false);
        content = column();
        content.setPadding(0, dp(20), 0, dp(28));
        page.addView(content);
        workspace.addView(page, new LinearLayout.LayoutParams(-1, 0, 1));
        if (!tablet) outer.addView(nav, new LinearLayout.LayoutParams(-1, -2));
        render();
    }

    private LinearLayout buildNavigation() {
        LinearLayout nav = tablet ? column() : row();
        nav.setBackgroundColor(RAIL);
        nav.setGravity(tablet ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(10), tablet ? dp(20) : 0, dp(10), tablet ? dp(20) : 0);
        if (tablet) {
            ConsoleIconView mark = new ConsoleIconView(this, "brand");
            mark.setTintColor(0xFFC4CAD3);
            nav.addView(mark, new LinearLayout.LayoutParams(dp(42), dp(42)));
            gap(nav, 44);
        }
        mouseTab = navItem("마우스", "mouse", 0);
        keyboardTab = navItem("키보드", "keyboard", 1);
        if (tablet) {
            nav.addView(mouseTab, new LinearLayout.LayoutParams(-1, dp(78)));
            gap(nav, 12);
            nav.addView(keyboardTab, new LinearLayout.LayoutParams(-1, dp(78)));
            nav.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
            TextView version = mono("0.5", 14, MUTED); version.setGravity(Gravity.CENTER); nav.addView(version);
        } else {
            nav.addView(mouseTab, new LinearLayout.LayoutParams(0, dp(72), 1));
            nav.addView(keyboardTab, new LinearLayout.LayoutParams(0, dp(72), 1));
        }
        return nav;
    }

    private LinearLayout navItem(String label, String symbol, int tab) {
        LinearLayout item = column(); item.setGravity(Gravity.CENTER);
        ConsoleIconView icon = new ConsoleIconView(this, symbol);
        item.addView(icon, new LinearLayout.LayoutParams(dp(25), dp(25)));
        gap(item, 4);
        TextView name = text(label, 14, MUTED, false); name.setGravity(Gravity.CENTER); item.addView(name);
        item.setFocusable(true);
        item.setOnClickListener(v -> { selectedTab = tab; render(); });
        if (tab == 0) { mouseTabIcon = icon; mouseTabLabel = name; }
        else { keyboardTabIcon = icon; keyboardTabLabel = name; }
        return item;
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("tab", selectedTab);
        super.onSaveInstanceState(out);
    }
    @Override protected void onResume() {
        super.onResume();
        registerReceiver(stateReceiver, new IntentFilter(MouseLongPressService.ACTION_STATE_CHANGED), Context.RECEIVER_NOT_EXPORTED);
        refresh();
    }
    @Override protected void onPause() {
        if (editor != null) editor.dialog.dismiss();
        unregisterReceiver(stateReceiver);
        super.onPause();
    }

    private void render() {
        mouseTab.setBackground(focusBackground(selectedTab == 0 ? 0xFF202B42 : RAIL));
        keyboardTab.setBackground(focusBackground(selectedTab == 1 ? 0xFF202B42 : RAIL));
        mouseTabLabel.setTextColor(selectedTab == 0 ? BRIGHT : MUTED);
        keyboardTabLabel.setTextColor(selectedTab == 1 ? BRIGHT : MUTED);
        mouseTabIcon.setTintColor(selectedTab == 0 ? BRIGHT : MUTED);
        keyboardTabIcon.setTintColor(selectedTab == 1 ? BRIGHT : MUTED);
        mouseTab.setSelected(selectedTab == 0);
        keyboardTab.setSelected(selectedTab == 1);
        content.removeAllViews();
        mouseSwitch = null; keyboardSwitch = null; mouseStatus = null; keyboardStatus = null; mouseDiagram = null;
        if (selectedTab == 0) renderMouse(); else renderKeyboard();
        refresh();
        page.scrollTo(0, 0);
    }

    private void sectionHeading(String section, String title, Switch control) {
        LinearLayout heading = row();
        LinearLayout label = column();
        TextView index = mono(section, 14, MUTED); index.setLetterSpacing(.1f);
        label.addView(index); gap(label, 4);
        label.addView(text(title, tablet ? 26 : 23, INK, false));
        heading.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        modeValue = mono("ON", 14, BRIGHT);
        LinearLayout.LayoutParams modePosition = new LinearLayout.LayoutParams(-2, -2);
        modePosition.setMargins(dp(8), 0, dp(12), 0);
        heading.addView(modeValue, modePosition);
        heading.addView(control, new LinearLayout.LayoutParams(dp(56), dp(48)));
        content.addView(heading, fill());
    }

    private void renderMouse() {
        mouseSwitch = toggle("마우스 변환");
        sectionHeading("01 / MOUSE", "마우스 변환", mouseSwitch);
        mouseStatus = text("", 14, MUTED, false);
        gap(content, 4); content.addView(mouseStatus);
        mouseSwitch.setOnCheckedChangeListener((v, enabled) -> {
            if (refreshing) return;
            MouseLongPressService svc = MouseLongPressService.getInstance();
            if (svc == null) { refresh(); openSettings(); }
            else { svc.enableMode(enabled); refresh(); }
        });
        View.OnClickListener choose = v -> chooseAction(ActionCatalog.mouseActions(), store.getMouseAction(), id -> {
            store.setMouseAction(id); render();
        });
        LinearLayout stage = tablet ? row() : column();
        stage.setPadding(0, dp(12), 0, dp(22));
        LinearLayout illustration = column(); illustration.setGravity(Gravity.CENTER);
        mouseDiagram = new MouseDiagramView(this);
        mouseDiagram.setOnClickListener(choose);
        boolean shortWindow = getResources().getConfiguration().screenHeightDp <= 700;
        int compactHeight = getResources().getConfiguration().fontScale > 1.15f ? 110 : 150;
        int diagramHeight = tablet ? (wide ? 280 : 370) : shortWindow ? compactHeight : 250;
        illustration.addView(mouseDiagram, new LinearLayout.LayoutParams(-1, dp(diagramHeight)));
        TextView caption = text("오른쪽 버튼을 선택해 동작 변경", 14, MUTED, false);
        caption.setGravity(Gravity.CENTER); illustration.addView(caption, fill());
        LinearLayout actionArea = column();
        String actionId = store.getMouseAction();
        if (tablet) {
            ConsoleIconView symbol = new ConsoleIconView(this, actionId); symbol.setTintColor(BRIGHT);
            actionArea.addView(symbol, new LinearLayout.LayoutParams(dp(42), dp(42)));
            gap(actionArea, 20);
        }
        LinearLayout actionText = column();
        actionText.addView(text("우클릭 동작", 14, MUTED, false));
        gap(actionText, 6);
        actionText.addView(text(ActionCatalog.title(actionId), tablet ? 30 : 25, INK, false));
        Button change = button(tablet ? "동작 변경  ›" : "변경  ›", BG, BRIGHT);
        change.setContentDescription("우클릭 동작 변경");
        change.setOnClickListener(choose); change.setBackground(outlineBackground(BG));
        if (tablet) {
            actionArea.addView(actionText, fill()); gap(actionArea, 12);
            actionArea.addView(text(ActionCatalog.LONG_PRESS.equals(actionId) ? "지원되는 요소는 누르는 순간 실행" : "누르는 순간 실행", 14, MUTED, false));
            gap(actionArea, 24);
            actionArea.addView(change, new LinearLayout.LayoutParams(dp(175), -2));
            stage.addView(illustration, new LinearLayout.LayoutParams(0, -2, 1.2f));
            View gutter = new View(this); stage.addView(gutter, new LinearLayout.LayoutParams(dp(wide ? 44 : 20), 1));
            stage.addView(actionArea, new LinearLayout.LayoutParams(0, -2, .85f));
        } else {
            stage.addView(illustration, fill()); gap(stage, 20);
            LinearLayout actionRow = row();
            actionRow.addView(actionText, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout.LayoutParams changePosition = new LinearLayout.LayoutParams(dp(92), -2); changePosition.setMarginStart(dp(12));
            actionRow.addView(change, changePosition); stage.addView(actionRow, fill());
        }
        content.addView(stage, fill());
        divider(content);
        if (ActionCatalog.LONG_PRESS.equals(actionId)) {
            gap(content, 18); content.addView(text("세부 설정", 14, MUTED, false));
            LinearLayout settings = wide ? row() : column();
            LinearLayout instantArea = row(); instantArea.setPadding(0, dp(18), 0, dp(18));
            LinearLayout instantLabels = column();
            instantLabels.addView(text("즉시 실행", 16, INK, false)); gap(instantLabels, 5);
            instantLabels.addView(text("미지원 화면: 버튼을 뗀 뒤 터치", 14, MUTED, false));
            instantArea.addView(instantLabels, new LinearLayout.LayoutParams(0, -2, 1));
            Switch instant = toggle("우클릭 즉시 실행");
            instant.setChecked(getSharedPreferences("settings", MODE_PRIVATE).getBoolean("instant_enabled", true));
            instant.setOnCheckedChangeListener((v, enabled) -> getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("instant_enabled", enabled).apply());
            LinearLayout.LayoutParams switchPosition = new LinearLayout.LayoutParams(dp(56), dp(48)); switchPosition.setMarginStart(dp(12));
            instantArea.addView(instant, switchPosition);
            LinearLayout range = column(); range.setPadding(0, dp(20), 0, dp(20));
            TextView duration = mono("", 20, INK);
            range.addView(duration, fill()); gap(range, 4);
            range.addView(text("터치로 대신 처리할 때 적용", 14, MUTED, false));
            SeekBar seek = new SeekBar(this);
            seek.setContentDescription("호환 터치 유지 시간"); seek.setMinHeight(dp(48)); seek.setPadding(dp(4), 0, dp(4), 0);
            seek.setMax(16);
            seek.setProgress(Math.max(0, Math.min(16, (getSharedPreferences("settings", MODE_PRIVATE).getInt("press_ms", 650) - 400) / 50)));
            duration.setText("호환 터치: " + (400 + seek.getProgress() * 50) + "ms");
            seek.setProgressTintList(ColorStateList.valueOf(BLUE));
            seek.setProgressBackgroundTintList(ColorStateList.valueOf(LINE));
            GradientDrawable thumb = round(0xFFC4CAD3, 2);
            thumb.setSize(dp(16), dp(25)); thumb.setStroke(dp(2), BG);
            seek.setThumb(thumb); seek.setThumbOffset(dp(8));
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar bar, int value, boolean user) {
                    int ms = 400 + value * 50; duration.setText("호환 터치: " + ms + "ms");
                    if (user) getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("press_ms", ms).apply();
                }
                public void onStartTrackingTouch(SeekBar bar) { }
                public void onStopTrackingTouch(SeekBar bar) { }
            });
            range.addView(seek, fill());
            LinearLayout limits = row();
            limits.addView(mono("400 ms", 14, MUTED), new LinearLayout.LayoutParams(0, -2, 1));
            limits.addView(mono("1,200 ms", 14, MUTED)); range.addView(limits, fill());
            if (wide) {
                settings.addView(instantArea, new LinearLayout.LayoutParams(0, -2, 1));
                settings.addView(new View(this), new LinearLayout.LayoutParams(dp(42), 1));
                settings.addView(range, new LinearLayout.LayoutParams(0, -2, 1.1f));
            } else { settings.addView(instantArea, fill()); divider(settings); settings.addView(range, fill()); }
            content.addView(settings, fill()); divider(content);
        }
        LinearLayout fixed = tablet ? row() : column(); fixed.setPadding(0, dp(20), 0, 0);
        LinearLayout fixedText = column();
        fixedText.addView(text("마우스 변환 켜기 / 끄기", 16, INK, false)); gap(fixedText, 4);
        fixedText.addView(text("키보드 단축키와 별도 작동", 14, MUTED, false));
        if (tablet) fixed.addView(fixedText, new LinearLayout.LayoutParams(0, -2, 1));
        else { fixed.addView(fixedText, fill()); gap(fixed, 12); }
        fixed.addView(mono("Ctrl + Alt + R", 16, 0xFFC4CAD3)); content.addView(fixed, fill());
    }

    private void renderKeyboard() {
        keyboardSwitch = toggle("키보드 단축키");
        sectionHeading("02 / KEYBOARD", "키보드 단축키", keyboardSwitch);
        keyboardSwitch.setOnCheckedChangeListener((v, checked) -> {
            if (!refreshing) { store.setKeyboardEnabled(checked); refresh(); }
        });
        keyboardStatus = text("", 14, MUTED, false);
        gap(content, 8); content.addView(keyboardStatus, fill()); gap(content, tablet ? 24 : 16);
        List<ShortcutRule> rules = store.load();
        LinearLayout tools = row();
        tools.addView(text("단축키 · " + rules.size(), 16, INK, false), new LinearLayout.LayoutParams(0, -2, 1));
        Button add = button("＋ 단축키 추가", 0xFF2E63D6, Color.WHITE);
        add.setOnClickListener(v -> openEditor(null)); tools.addView(add);
        content.addView(tools, fill()); gap(content, tablet ? 18 : 12); divider(content);
        if (rules.isEmpty()) {
            gap(content, 28); content.addView(text("등록된 단축키 없음", 18, INK, false));
            gap(content, 8); content.addView(text("Ctrl/Alt + 키 조합 등록", 14, MUTED, false)); gap(content, 28);
        }
        int index = 0;
        for (ShortcutRule rule : rules) {
            LinearLayout item = row(); item.setPadding(0, dp(8), 0, dp(8));
            TextView number = mono(String.format(java.util.Locale.ROOT, "%02d", ++index), 14, MUTED);
            item.addView(number, new LinearLayout.LayoutParams(dp(tablet ? 38 : 28), -2));
            LinearLayout details = column(); details.setPadding(dp(4), dp(12), dp(8), dp(12)); details.setMinimumHeight(dp(tablet ? 80 : 74));
            details.addView(mono(ShortcutStore.chordLabel(rule.keyCode, rule.modifiers), tablet ? 24 : 20, rule.enabled ? INK : MUTED), fill());
            gap(details, 5); details.addView(text(ruleDescription(rule) + "  ›", 14, MUTED, false));
            details.setContentDescription(ShortcutStore.chordLabel(rule.keyCode, rule.modifiers) + ", " + ruleDescription(rule) + ", 편집");
            details.setFocusable(true); details.setBackground(focusBackground(BG));
            details.setOnClickListener(v -> {
                for (ShortcutRule current : store.load()) if (current.id.equals(rule.id)) { openEditor(current); break; }
            });
            item.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
            Switch enabled = toggle(ShortcutStore.chordLabel(rule.keyCode, rule.modifiers) + " 사용");
            enabled.setChecked(rule.enabled);
            enabled.setOnCheckedChangeListener((v, checked) -> {
                store.upsert(new ShortcutRule(rule.id, rule.keyCode, rule.modifiers, rule.actionId, rule.packageName, checked));
                ((TextView) details.getChildAt(0)).setTextColor(checked ? INK : MUTED);
            });
            item.addView(enabled, new LinearLayout.LayoutParams(dp(56), dp(48)));
            content.addView(item, fill()); divider(content);
        }
        gap(content, 24);
        content.addView(text("• Ctrl+Alt+R: 마우스 변환 전용\n• 미등록 키: 원래대로 입력", 14, MUTED, false));
    }

    private void refresh() {
        if (connection == null) return;
        MouseLongPressService svc = MouseLongPressService.getInstance();
        boolean connected = svc != null;
        refreshing = true;
        connection.setText(connected ? "● 접근성 연결됨" : "○ 접근성 꺼짐");
        connection.setTextColor(connected ? BRIGHT : MUTED);
        setup.setVisibility(connected ? View.GONE : View.VISIBLE);
        if (mouseSwitch != null) {
            mouseSwitch.setChecked(connected && svc.isModeEnabled());
            mouseDiagram.setActive(mouseSwitch.isChecked());
            modeValue.setText(mouseSwitch.isChecked() ? "ON" : "OFF");
            modeValue.setTextColor(mouseSwitch.isChecked() ? BRIGHT : MUTED);
            mouseStatus.setText(!connected ? "접근성 연결 필요" : svc.isModeEnabled() ? "마우스 변환 켜짐" : "마우스 변환 꺼짐");
        }
        if (keyboardSwitch != null) {
            keyboardSwitch.setChecked(store.isKeyboardEnabled());
            modeValue.setText(keyboardSwitch.isChecked() ? "ON" : "OFF");
            modeValue.setTextColor(keyboardSwitch.isChecked() ? BRIGHT : MUTED);
            keyboardStatus.setText(!connected ? "설정 저장됨 · 실행은 접근성 연결 필요" : store.isKeyboardEnabled() ? "단축키 켜짐 · 마우스와 별도 작동" : "단축키 꺼짐 · Ctrl+Alt+R 사용 가능");
        }
        refreshing = false;
        if (editor != null && svc != null) svc.setShortcutRecording(editor.recording);
    }

    private void openEditor(ShortcutRule rule) {
        if (editor != null) return;
        editor = new Editor(rule);
        editor.show();
    }

    private final class Editor {
        final ShortcutRule original;
        final AlertDialog dialog;
        final Button record, action, app;
        final TextView hint, error;
        int key, modifiers;
        String actionId, packageName;
        boolean recording;
        Editor(ShortcutRule rule) {
            original = rule;
            key = rule == null ? KeyEvent.KEYCODE_UNKNOWN : rule.keyCode;
            modifiers = rule == null ? 0 : rule.modifiers;
            actionId = rule == null ? ActionCatalog.BACK : rule.actionId;
            packageName = rule == null ? null : rule.packageName;
            LinearLayout form = column();
            form.setPadding(dp(16), dp(8), dp(16), dp(12));
            form.addView(text("1  키 조합", 14, MUTED, true)); gap(form, 8);
            record = button("", 0xFF171B22, BRIGHT);
            form.addView(record, fill());
            hint = text("", 14, MUTED, false);
            form.addView(hint); gap(form, 12);
            form.addView(text("2  동작", 14, MUTED, true)); gap(form, 8);
            action = button("", SURFACE, INK);
            action.setOnClickListener(v -> { setRecording(false); update(); chooseAction(ActionCatalog.keyboardActions(), actionId, id -> { actionId = id; update(); }); });
            form.addView(action, fill()); gap(form, 8);
            app = button("", SURFACE, INK);
            app.setOnClickListener(v -> { setRecording(false); update(); chooseApp(this); });
            form.addView(app, fill());
            error = text("", 14, 0xFFFFB4AB, false);
            error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            record.setOnClickListener(v -> { setRecording(true); error.setText(""); update(); record.requestFocus(); });
            gap(form, 10); form.addView(error);
            ScrollView scroll = new ScrollView(MainActivity.this); scroll.addView(form);
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this)
                    .setTitle(rule == null ? "단축키 추가" : "단축키 편집").setView(scroll)
                    .setPositiveButton("저장", null).setNegativeButton("취소", null);
            if (rule != null) builder.setNeutralButton("삭제", (d, which) -> { store.delete(rule.id); render(); });
            dialog = builder.create();
            dialog.setOnDismissListener(d -> { setRecording(false); if (editor == this) editor = null; });
            dialog.setOnKeyListener((d, keyCode, event) -> {
                if (!recording) return false;
                if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) { setRecording(false); update(); }
                    return true;
                }
                if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0) return true;
                if (KeyEvent.isModifierKey(keyCode)) return true;
                int mods = ShortcutStore.modifiersOf(event);
                String invalid = (event.getMetaState() & (KeyEvent.META_META_ON | KeyEvent.META_SYM_ON | KeyEvent.META_FUNCTION_ON)) != 0
                    ? "Ctrl·Alt·Shift 조합만 지원" : ShortcutStore.validateChord(keyCode, mods);
                if (invalid != null) { error.setText(invalid); return true; }
                key = keyCode; modifiers = mods;
                setRecording(false); error.setText(""); update();
                return true;
            });
            update();
        }
        void show() {
            dialog.show();
            styleDialog(dialog);
            update();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                setRecording(false); update();
                try {
                    store.upsert(new ShortcutRule(original == null ? UUID.randomUUID().toString() : original.id,
                            key, modifiers, actionId, packageName, original == null || original.enabled));
                    dialog.dismiss(); render();
                } catch (IllegalArgumentException invalid) { error.setText(invalid.getMessage()); }
            });
        }
        void setRecording(boolean active) {
            recording = active;
            MouseLongPressService svc = MouseLongPressService.getInstance();
            if (svc != null) svc.setShortcutRecording(active);
        }
        void update() {
            record.setText(recording ? "키 조합 입력 중…" : key == KeyEvent.KEYCODE_UNKNOWN ? "키 조합 입력" : ShortcutStore.chordLabel(key, modifiers) + "  ·  다시 입력");
            hint.setText(recording ? "• Ctrl/Alt + 키\n• Esc: 입력 취소" : "외장 키보드로 입력");
            action.setText(ActionCatalog.title(actionId) + "  ›");
            app.setVisibility(ActionCatalog.LAUNCH_APP.equals(actionId) ? View.VISIBLE : View.GONE);
            app.setText(packageName == null ? "실행할 앱 선택  ›" : appLabel(packageName) + "  ›");
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (save != null) save.setEnabled(!recording && key != KeyEvent.KEYCODE_UNKNOWN
                    && (!ActionCatalog.LAUNCH_APP.equals(actionId) || packageName != null));
        }
    }

    private interface ActionChoice { void selected(String id); }
    private void chooseAction(String[] ids, String current, ActionChoice callback) {
        String[] titles = new String[ids.length]; int checked = -1;
        for (int i = 0; i < ids.length; i++) { titles[i] = ActionCatalog.title(ids[i]); if (ids[i].equals(current)) checked = i; }
        styleDialog(new AlertDialog.Builder(this).setTitle("동작 선택").setSingleChoiceItems(titles, checked,
                (dialog, which) -> { dialog.dismiss(); callback.selected(ids[which]); }).setNegativeButton("취소", null).show());
    }
    private void chooseApp(Editor target) {
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> candidates = getPackageManager().queryIntentActivities(launcher, 0);
        List<String[]> apps = new ArrayList<>(); HashSet<String> seen = new HashSet<>();
        for (ResolveInfo candidate : candidates) {
            String pkg = candidate.activityInfo.packageName;
            if (seen.add(pkg)) apps.add(new String[]{candidate.loadLabel(getPackageManager()).toString(), pkg});
        }
        Collator collator = Collator.getInstance(); apps.sort((a, b) -> collator.compare(a[0], b[0]));
        String[] labels = new String[apps.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = apps.get(i)[0];
        AlertDialog.Builder picker = new AlertDialog.Builder(this).setTitle("실행할 앱 선택").setNegativeButton("취소", null);
        if (labels.length == 0) picker.setMessage("실행 가능한 앱 없음");
        else picker.setItems(labels, (d, which) -> { target.packageName = apps.get(which)[1]; target.update(); });
        styleDialog(picker.show());
    }
    private String appLabel(String pkg) {
        try {
            if (getPackageManager().getLaunchIntentForPackage(pkg) == null) return "앱 없음 · 다시 선택";
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (android.content.pm.PackageManager.NameNotFoundException e) { return "앱 없음 · 다시 선택"; }
    }
    private String ruleDescription(ShortcutRule rule) {
        return ActionCatalog.LAUNCH_APP.equals(rule.actionId) ? "앱 실행 · " + appLabel(rule.packageName) : ActionCatalog.title(rule.actionId);
    }
    private void openSettings() {
        if (AccessibilityConsent.isAccepted(this)) {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        if (consentDialog != null && consentDialog.isShowing()) return;
        consentDialog = new AlertDialog.Builder(this)
                .setTitle("접근성 사용 안내")
                .setMessage("RightPress는 접근성 API로 마우스 우클릭과 외장 키보드 단축키를 처리합니다. 앱 화면을 닫아도 서비스가 켜져 있으면 작동합니다.\n\n"
                        + "접근·실행\n• 화면 요소의 계층·위치·지원 동작 확인\n• 키 입력 이벤트를 받아 등록한 단축키와 비교\n• 우클릭·클릭·휠을 길게 누르기·터치·스크롤로 변환\n• 지정한 뒤로·홈·알림·앱 실행 동작 수행\n\n"
                        + "저장·전송\n• 화면 내용·입력 기록 저장 안 함\n• 설정과 동의 여부만 이 기기에 저장\n• 외부 전송·공유 없음\n\n"
                        + "Android 접근성 설정에서 언제든 서비스를 끌 수 있습니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("동의하고 설정 열기", null)
                .create();
        consentDialog.setOnDismissListener(dialog -> consentDialog = null);
        consentDialog.show();
        styleDialog(consentDialog);
        consentDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (!AccessibilityConsent.accept(this)) {
                Toast.makeText(this, "동의 저장 실패 · 다시 시도", Toast.LENGTH_SHORT).show();
                return;
            }
            consentDialog.dismiss();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
    }
    @Override protected void onDestroy() {
        if (consentDialog != null) consentDialog.dismiss();
        super.onDestroy();
    }
    private void showHelp() {
        styleDialog(new AlertDialog.Builder(this).setTitle("사용 안내")
                .setMessage("실행\n• 접근성 연결 후 백그라운드 작동\n• 앱 화면을 닫아도 설정 유지\n• 서비스 해제·강제 중지 시 중단\n\n마우스\n• Ctrl+Alt+R: 마우스만 켜기/끄기\n• 우클릭: 누르는 순간 실행\n• 길게 누르기 미지원: 버튼을 뗀 뒤 터치\n• 클릭·휠: 터치로 변환\n• 드래그·직접 터치 차이 가능\n• 마우스 오버 동작 제한\n\n키보드\n• Ctrl/Alt + 키 조합 지원\n• 일부 시스템 단축키 사용 불가\n\n정보 처리\n• 화면 요소 위치·접근성 동작 확인\n• 화면 내용·입력 기록 저장·전송 안 함\n• 설정은 이 기기에만 저장\n\n시험판\n• Android 14 이상\n• 실물 태블릿·입력장치 확인 필요")
                .setPositiveButton("확인", null).setNeutralButton("접근성 설정", (d, w) -> openSettings()).show());
    }

    private void styleDialog(AlertDialog dialog) {
        GradientDrawable surface = round(SURFACE, 12); surface.setStroke(dp(1), LINE);
        dialog.getWindow().setBackgroundDrawable(surface);
    }

    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private LinearLayout.LayoutParams fill() { return new LinearLayout.LayoutParams(-1, -2); }
    private void divider(LinearLayout parent) { View line = new View(this); line.setBackgroundColor(LINE); parent.addView(line, new LinearLayout.LayoutParams(-1, dp(1))); }
    private TextView mono(String value, int size, int color) { TextView view = text(value, size, color, false); view.setTypeface(Typeface.MONOSPACE); return view; }
    private Switch toggle(String label) {
        Switch view = new ConsoleToggle(this); view.setContentDescription(label); return view;
    }
    /** Keep native Switch semantics and hardware-key behavior while matching the selected console control. */
    private final class ConsoleToggle extends Switch {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ConsoleToggle(Context context) { super(context); setText(""); setMinWidth(dp(56)); setMinHeight(dp(48)); setBackgroundColor(Color.TRANSPARENT); }
        @Override public void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(resolveSize(dp(56), widthSpec), resolveSize(dp(48), heightSpec));
        }
        @Override protected void onDraw(Canvas canvas) {
            float left = (getWidth() - dp(54)) / 2f, top = (getHeight() - dp(30)) / 2f;
            paint.setStyle(Paint.Style.FILL); paint.setColor(isChecked() ? BLUE : 0xFF464D59);
            canvas.drawRoundRect(left, top, left + dp(54), top + dp(30), dp(15), dp(15), paint);
            paint.setColor(0xFFF5F7FB); canvas.drawCircle(left + dp(isChecked() ? 39 : 15), top + dp(15), dp(11), paint);
            if (isFocused() || isPressed()) {
                paint.setStyle(Paint.Style.STROKE); paint.setColor(BRIGHT); paint.setStrokeWidth(dp(2));
                canvas.drawRoundRect(dp(1), dp(1), getWidth() - dp(1), getHeight() - dp(1), dp(6), dp(6), paint);
            }
        }
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setLineSpacing(dp(3), 1.03f);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return view;
    }
    private Button button(String value, int background, int foreground) {
        Button b = new Button(this); b.setAllCaps(false); b.setText(value); b.setTextSize(15); b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}}, new int[]{0xFF707886, foreground}));
        b.setBackground(focusBackground(background)); b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48)); b.setPadding(dp(12), dp(8), dp(12), dp(8)); return b;
    }
    private StateListDrawable focusBackground(int color) {
        StateListDrawable states = new StateListDrawable();
        GradientDrawable focus = round(color, 6); focus.setStroke(dp(2), BLUE);
        GradientDrawable pressed = round(color, 6); pressed.setStroke(dp(3), BLUE);
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{android.R.attr.state_focused}, focus);
        states.addState(new int[]{}, round(color, 6));
        return states;
    }
    private StateListDrawable outlineBackground(int color) {
        StateListDrawable states = new StateListDrawable();
        GradientDrawable focused = round(color, 5); focused.setStroke(dp(2), BRIGHT);
        GradientDrawable normal = round(color, 5); normal.setStroke(dp(1), 0xFF64718A);
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal); return states;
    }
    private GradientDrawable round(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private void gap(LinearLayout parent, int height) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height))); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
