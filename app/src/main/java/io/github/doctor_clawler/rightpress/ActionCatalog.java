package io.github.doctor_clawler.rightpress;

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

    public static String title(String actionId) {
        if (actionId == null) return "알 수 없는 동작";
        switch (actionId) {
            case LONG_PRESS: return "길게 누르기";
            case BACK: return "뒤로";
            case HOME: return "홈";
            case RECENTS: return "최근 앱";
            case NOTIFICATIONS: return "알림 열기";
            case QUICK_SETTINGS: return "빠른 설정 열기";
            case LAUNCH_APP: return "앱 실행";
            case TOGGLE_MOUSE: return "마우스 변환 켜기/끄기";
            default: return "알 수 없는 동작";
        }
    }

    public static String description(String actionId) {
        if (actionId == null) return "지원하지 않는 동작";
        switch (actionId) {
            case LONG_PRESS: return "포인터 위치 길게 누르기 · 미지원 시 버튼을 뗀 뒤 터치";
            case BACK: return "이전 화면으로 이동";
            case HOME: return "홈 화면으로 이동";
            case RECENTS: return "최근 앱 목록 열기";
            case NOTIFICATIONS: return "알림 패널 열기";
            case QUICK_SETTINGS: return "빠른 설정 열기";
            case LAUNCH_APP: return "선택한 앱 실행";
            case TOGGLE_MOUSE: return "마우스만 켜기/끄기 · 키보드 설정 유지";
            default: return "지원하지 않는 동작";
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
