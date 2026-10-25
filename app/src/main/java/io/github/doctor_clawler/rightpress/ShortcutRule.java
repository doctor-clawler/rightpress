package io.github.doctor_clawler.rightpress;

import android.view.KeyEvent;

/** An immutable shortcut. Store validation rejects unsupported or conflicting rules. */
public final class ShortcutRule {
    public final String id;
    public final int keyCode;
    public final int modifiers;
    public final String actionId;
    public final String packageName;
    public final boolean enabled;

    public ShortcutRule(String id, int keyCode, int modifiers, String actionId,
                        String packageName, boolean enabled) {
        this.id = id;
        this.keyCode = keyCode;
        this.modifiers = KeyEvent.normalizeMetaState(modifiers) & ShortcutStore.MODIFIER_MASK;
        this.actionId = actionId;
        this.packageName = packageName;
        this.enabled = enabled;
    }
}
