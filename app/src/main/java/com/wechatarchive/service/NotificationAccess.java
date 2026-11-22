package com.wechatarchive.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;

public final class NotificationAccess {
    private NotificationAccess() {
    }

    public static boolean isGranted(Context context) {
        String enabled = Settings.Secure.getString(
                context.getContentResolver(),
                "enabled_notification_listeners");
        if (TextUtils.isEmpty(enabled)) {
            return false;
        }
        ComponentName component = new ComponentName(
                context,
                WeChatNotificationListener.class);
        String flattened = component.flattenToString();
        String shortFlattened = component.flattenToShortString();
        String[] listeners = enabled.split(":");
        for (String listener : listeners) {
            if (listener.equalsIgnoreCase(flattened)
                    || listener.equalsIgnoreCase(shortFlattened)) {
                return true;
            }
        }
        return false;
    }

    public static Intent settingsIntent() {
        return new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
