package io.github.doctor_clawler.rightpress;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Local settings only: no recorded input, text injection, scripts or network. */
public final class ShortcutStore {
    static final int MODIFIER_MASK = KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON;
    private static final String RULES_KEY = "shortcut_rules";
    private static final Object RULES_LOCK = new Object();
    private final Context context;
    private final SharedPreferences preferences;

    public ShortcutStore(Context context) {
        // Retain the caller's configuration so validation uses the app's chosen locale.
        this.context = context;
        preferences = this.context.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public List<ShortcutRule> load() {
        if (!preferences.contains(RULES_KEY)) return defaults();
        List<ShortcutRule> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> chords = new HashSet<>();
        try {
            JSONArray entries = new JSONArray(preferences.getString(RULES_KEY, "[]"));
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.optJSONObject(i);
                if (entry == null) continue;
                ShortcutRule rule = new ShortcutRule(entry.optString("id", ""),
                    entry.optInt("keyCode", KeyEvent.KEYCODE_UNKNOWN), entry.optInt("modifiers", 0),
                    entry.optString("actionId", ""),
                    entry.isNull("packageName") ? null : entry.optString("packageName", null),
                    entry.optBoolean("enabled", true));
                String chord = rule.keyCode + ":" + rule.modifiers;
                if (validationError(rule) == null && !ids.contains(rule.id) && !chords.contains(chord)) {
                    ids.add(rule.id);
                    chords.add(chord);
                    result.add(rule);
                }
            }
        } catch (JSONException | ClassCastException ex) {
            // Malformed persisted data must not unexpectedly activate default bindings.
            result.clear();
        }
        return result;
    }

    public void upsert(ShortcutRule rule) throws IllegalArgumentException {
        String error = validationError(rule);
        if (error != null) throw new IllegalArgumentException(error);
        synchronized (RULES_LOCK) {
            List<ShortcutRule> rules = load();
            int replacement = -1;
            for (int i = 0; i < rules.size(); i++) {
                ShortcutRule existing = rules.get(i);
                if (existing.id.equals(rule.id)) {
                    replacement = i;
                } else if (existing.keyCode == rule.keyCode && existing.modifiers == rule.modifiers) {
                    throw new IllegalArgumentException(context.getString(R.string.error_duplicate_chord));
                }
            }
            if (replacement >= 0) rules.set(replacement, rule);
            else rules.add(rule);
            save(rules);
        }
        notifyChanged();
    }

    public void delete(String id) {
        synchronized (RULES_LOCK) {
            List<ShortcutRule> rules = load();
            if (!rules.removeIf(rule -> rule.id.equals(id))) return;
            save(rules);
        }
        notifyChanged();
    }

    public boolean isKeyboardEnabled() { return preferences.getBoolean("keyboard_enabled", true); }

    public void setKeyboardEnabled(boolean enabled) {
        preferences.edit().putBoolean("keyboard_enabled", enabled).apply();
        notifyChanged();
    }

    public String getMouseAction() {
        String action = preferences.getString("mouse_action", ActionCatalog.LONG_PRESS);
        return ActionCatalog.isMouseAction(action) ? action : ActionCatalog.LONG_PRESS;
    }

    public void setMouseAction(String actionId) {
        if (!ActionCatalog.isMouseAction(actionId)) throw new IllegalArgumentException(context.getString(R.string.error_mouse_action));
        preferences.edit().putString("mouse_action", actionId).apply();
        notifyChanged();
    }

    public static int modifiersOf(KeyEvent event) {
        return KeyEvent.normalizeMetaState(event.getMetaState()) & MODIFIER_MASK;
    }

    public static String validateChord(Context context, int keyCode, int modifiers) {
        if (keyCode <= KeyEvent.KEYCODE_UNKNOWN || keyCode > KeyEvent.getMaxKeyCode()
                || KeyEvent.isModifierKey(keyCode) || keyCode == KeyEvent.KEYCODE_CAPS_LOCK
                || keyCode == KeyEvent.KEYCODE_NUM_LOCK || keyCode == KeyEvent.KEYCODE_SCROLL_LOCK) {
            return context.getString(R.string.error_regular_key);
        }
        int normalized = KeyEvent.normalizeMetaState(modifiers) & MODIFIER_MASK;
        if ((normalized & (KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON)) == 0) {
            return context.getString(R.string.error_ctrl_alt_required);
        }
        if (keyCode == KeyEvent.KEYCODE_R && (normalized & KeyEvent.META_CTRL_ON) != 0
                && (normalized & KeyEvent.META_ALT_ON) != 0) {
            return context.getString(R.string.error_reserved_chord);
        }
        return null;
    }

    public static String chordLabel(int keyCode, int modifiers) {
        int normalized = KeyEvent.normalizeMetaState(modifiers) & MODIFIER_MASK;
        StringBuilder label = new StringBuilder();
        if ((normalized & KeyEvent.META_CTRL_ON) != 0) label.append("Ctrl+");
        if ((normalized & KeyEvent.META_ALT_ON) != 0) label.append("Alt+");
        if ((normalized & KeyEvent.META_SHIFT_ON) != 0) label.append("Shift+");
        String key = KeyEvent.keyCodeToString(keyCode);
        if (key.startsWith("KEYCODE_")) key = key.substring("KEYCODE_".length());
        if (keyCode == KeyEvent.KEYCODE_DEL) key = "Backspace";
        else if (keyCode == KeyEvent.KEYCODE_FORWARD_DEL) key = "Delete";
        return label.append(key).toString();
    }

    private String validationError(ShortcutRule rule) {
        if (rule == null) return context.getString(R.string.error_missing_shortcut);
        if (rule.id == null || rule.id.trim().isEmpty() || rule.id.length() > 128) return context.getString(R.string.error_shortcut_id);
        String chordError = validateChord(context, rule.keyCode, rule.modifiers);
        if (chordError != null) return chordError;
        if (!ActionCatalog.isKeyboardAction(rule.actionId)) return context.getString(R.string.error_keyboard_action);
        if (ActionCatalog.LAUNCH_APP.equals(rule.actionId)
                && (rule.packageName == null || rule.packageName.length() > 255
                    || !rule.packageName.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*"))) {
            return context.getString(R.string.error_choose_app);
        }
        return null;
    }

    private static List<ShortcutRule> defaults() {
        List<ShortcutRule> rules = new ArrayList<>();
        int modifiers = KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON;
        rules.add(new ShortcutRule("default_back", KeyEvent.KEYCODE_B, modifiers, ActionCatalog.BACK, null, true));
        rules.add(new ShortcutRule("default_home", KeyEvent.KEYCODE_H, modifiers, ActionCatalog.HOME, null, true));
        rules.add(new ShortcutRule("default_recents", KeyEvent.KEYCODE_O, modifiers, ActionCatalog.RECENTS, null, true));
        rules.add(new ShortcutRule("default_notifications", KeyEvent.KEYCODE_N, modifiers, ActionCatalog.NOTIFICATIONS, null, true));
        return rules;
    }

    private void save(List<ShortcutRule> rules) {
        JSONArray entries = new JSONArray();
        try {
            for (ShortcutRule rule : rules) {
                JSONObject entry = new JSONObject();
                entry.put("id", rule.id);
                entry.put("keyCode", rule.keyCode);
                entry.put("modifiers", rule.modifiers);
                entry.put("actionId", rule.actionId);
                entry.put("packageName", rule.packageName == null ? JSONObject.NULL : rule.packageName);
                entry.put("enabled", rule.enabled);
                entries.put(entry);
            }
        } catch (JSONException ex) {
            throw new IllegalArgumentException(context.getString(R.string.error_save_shortcut), ex);
        }
        preferences.edit().putString(RULES_KEY, entries.toString()).apply();
    }

    private void notifyChanged() {
        context.sendBroadcast(new Intent(MouseLongPressService.ACTION_STATE_CHANGED).setPackage(context.getPackageName()));
    }
}
