package io.github.doctor_clawler.rightpress;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.LocaleConfig;
import android.app.LocaleManager;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.LocaleList;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Language availability comes from Android resources, not a second hard-coded list. */
final class AppLanguage {
    private AppLanguage() { }

    static List<String> supportedTags(Context context) {
        LocaleList locales = new LocaleConfig(context).getSupportedLocales();
        List<String> tags = new ArrayList<>();
        if (locales != null) {
            for (int i = 0; i < locales.size(); i++) tags.add(locales.get(i).toLanguageTag());
        }
        return tags;
    }

    /** Empty means follow Android's system language list. */
    static String currentTag(Context context) {
        LocaleList locales = context.getSystemService(LocaleManager.class).getApplicationLocales();
        return locales.isEmpty() ? "" : locales.get(0).toLanguageTag();
    }

    static void apply(Context context, String tag) {
        String normalized = Locale.forLanguageTag(tag).toLanguageTag();
        if (!tag.isEmpty() && !supportedTags(context).contains(normalized)) {
            throw new IllegalArgumentException("Unsupported app locale: " + tag);
        }
        LocaleList next = tag.isEmpty() ? LocaleList.getEmptyLocaleList() : LocaleList.forLanguageTags(normalized);
        LocaleManager manager = context.getSystemService(LocaleManager.class);
        if (!manager.getApplicationLocales().equals(next)) manager.setApplicationLocales(next);
    }

    static AlertDialog showPicker(Activity activity) {
        List<String> tags = supportedTags(activity);
        Collator collator = Collator.getInstance(activity.getResources().getConfiguration().getLocales().get(0));
        tags.sort((a, b) -> collator.compare(nativeName(a), nativeName(b)));
        tags.add(0, "");
        String[] labels = new String[tags.size()];
        String active = currentTag(activity);
        int checked = 0;
        for (int i = 0; i < tags.size(); i++) {
            String tag = tags.get(i);
            labels[i] = tag.isEmpty() ? activity.getString(R.string.language_system) : nativeName(tag);
            if (tag.equals(active)) checked = i;
        }
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.language_title)
                .setSingleChoiceItems(labels, checked, (choice, index) -> {
                    choice.dismiss();
                    apply(activity, tags.get(index));
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.show();
        float density = activity.getResources().getDisplayMetrics().density;
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF222832);
        background.setCornerRadius(12 * density);
        background.setStroke(Math.round(density), 0xFF343B46);
        dialog.getWindow().setBackgroundDrawable(background);
        return dialog;
    }

    private static String nativeName(String tag) {
        Locale locale = Locale.forLanguageTag(tag);
        return locale.getDisplayName(locale);
    }
}
