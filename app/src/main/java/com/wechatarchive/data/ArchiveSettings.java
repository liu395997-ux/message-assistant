package com.wechatarchive.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.wechatarchive.domain.CapturePolicy;
import com.wechatarchive.domain.ConversationWhitelistParser;

public final class ArchiveSettings {
    private static final String PREFS_NAME = "archive_settings";
    private static final String KEY_CAPTURE_ENABLED = "capture_enabled";
    private static final String KEY_WHITELIST = "conversation_whitelist";

    private final SharedPreferences preferences;

    public ArchiveSettings(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isCaptureEnabled() {
        return preferences.getBoolean(KEY_CAPTURE_ENABLED, true);
    }

    public void setCaptureEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_CAPTURE_ENABLED, enabled).apply();
    }

    public String getWhitelistText() {
        return preferences.getString(KEY_WHITELIST, "");
    }

    public void setWhitelistText(String text) {
        preferences.edit().putString(KEY_WHITELIST, text == null ? "" : text.trim()).apply();
    }

    public CapturePolicy currentPolicy() {
        return new CapturePolicy(
                isCaptureEnabled(),
                ConversationWhitelistParser.parse(getWhitelistText()));
    }
}
