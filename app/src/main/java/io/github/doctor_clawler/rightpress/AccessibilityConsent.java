package io.github.doctor_clawler.rightpress;

import android.content.Context;

/** Local receipt for the specific in-app Accessibility API disclosure. */
final class AccessibilityConsent {
    static final int DISCLOSURE_VERSION = 1;
    static final String PREFERENCES = "accessibility_consent";
    static final String VERSION_KEY = "disclosure_version";

    private AccessibilityConsent() { }

    static boolean isAccepted(Context context) {
        try {
            return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .getInt(VERSION_KEY, 0) == DISCLOSURE_VERSION;
        } catch (ClassCastException malformedReceipt) {
            return false;
        }
    }

    /** Called only by the disclosure's explicit accept button. */
    static boolean accept(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putInt(VERSION_KEY, DISCLOSURE_VERSION).commit();
    }
}
