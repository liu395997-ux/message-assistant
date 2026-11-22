package com.wechatarchive.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class CapturePolicy {
    public static final String WECHAT_PACKAGE = "com.tencent.mm";

    private final boolean enabled;
    private final Set<String> whitelist;

    public CapturePolicy(boolean enabled, Collection<String> conversationWhitelist) {
        this.enabled = enabled;
        Set<String> normalized = new LinkedHashSet<>();
        if (conversationWhitelist != null) {
            for (String value : conversationWhitelist) {
                String clean = normalize(value);
                if (!clean.isEmpty()) {
                    normalized.add(clean);
                }
            }
        }
        this.whitelist = Collections.unmodifiableSet(normalized);
    }

    public boolean shouldCapture(String packageName, String conversationName) {
        if (!enabled || !WECHAT_PACKAGE.equals(packageName)) {
            return false;
        }
        return whitelist.isEmpty() || whitelist.contains(normalize(conversationName));
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }
}
