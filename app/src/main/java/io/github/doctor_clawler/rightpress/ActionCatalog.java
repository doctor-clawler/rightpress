package io.github.doctor_clawler.rightpress;

import android.content.Context;

/** The bounded set of actions available to user-created input bindings. */
public final class ActionCatalog {
    public static final String LONG_PRESS = "long_press";
    public static final String BACK = "back";
    public static final String HOME = "home";
    public static final String RECENTS = "recents";
    public static final String NOTIFICATIONS = "notifications";
    public static final String QUICK_SETTINGS = "quick_settings";
    public static final String LAUNCH_APP = "launch_app";
    public static final String TOGGLE_MOUSE = "toggle_mouse";

    private ActionCatalog() { }

    public static String title(Context context, String actionId) {
        if (actionId == null) return context.getString(R.string.action_unknown);
        switch (actionId) {
            case LONG_PRESS: return context.getString(R.string.action_long_press);
            case BACK: return context.getString(R.string.action_back);
            case HOME: return context.getString(R.string.action_home);
            case RECENTS: return context.getString(R.string.action_recents);
            case NOTIFICATIONS: return context.getString(R.string.action_notifications);
            case QUICK_SETTINGS: return context.getString(R.string.action_quick_settings);
            case LAUNCH_APP: return context.getString(R.string.action_launch_app);
            case TOGGLE_MOUSE: return context.getString(R.string.action_toggle_mouse);
            default: return context.getString(R.string.action_unknown);
        }
    }

    public static String description(Context context, String actionId) {
        if (actionId == null) return context.getString(R.string.description_unknown);
        switch (actionId) {
            case LONG_PRESS: return context.getString(R.string.description_long_press);
            case BACK: return context.getString(R.string.description_back);
            case HOME: return context.getString(R.string.description_home);
            case RECENTS: return context.getString(R.string.description_recents);
            case NOTIFICATIONS: return context.getString(R.string.description_notifications);
            case QUICK_SETTINGS: return context.getString(R.string.description_quick_settings);
            case LAUNCH_APP: return context.getString(R.string.description_launch_app);
            case TOGGLE_MOUSE: return context.getString(R.string.description_toggle_mouse);
            default: return context.getString(R.string.description_unknown);
        }
    }

    public static String[] keyboardActions() {
        return new String[]{BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, LAUNCH_APP, TOGGLE_MOUSE};
    }

    public static String[] mouseActions() {
        return new String[]{LONG_PRESS, BACK, HOME, RECENTS, NOTIFICATIONS};
    }

    static boolean isKeyboardAction(String actionId) {
        for (String candidate : keyboardActions()) if (candidate.equals(actionId)) return true;
        return false;
    }

    static boolean isMouseAction(String actionId) {
        for (String candidate : mouseActions()) if (candidate.equals(actionId)) return true;
        return false;
    }
}
