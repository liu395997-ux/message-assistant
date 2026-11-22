package com.wechatarchive.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RawNotification {
    private final String packageName;
    private final String title;
    private final String text;
    private final String bigText;
    private final String subText;
    private final String conversationTitle;
    private final List<String> textLines;
    private final List<RawMessage> messages;
    private final long postedAtMillis;
    private final String notificationKey;

    public RawNotification(
            String packageName,
            String title,
            String text,
            String bigText,
            String subText,
            String conversationTitle,
            List<String> textLines,
            List<RawMessage> messages,
            long postedAtMillis,
            String notificationKey) {
        this.packageName = safe(packageName);
        this.title = safe(title);
        this.text = safe(text);
        this.bigText = safe(bigText);
        this.subText = safe(subText);
        this.conversationTitle = safe(conversationTitle);
        this.textLines = immutableCopy(textLines);
        this.messages = immutableCopy(messages);
        this.postedAtMillis = postedAtMillis;
        this.notificationKey = safe(notificationKey);
    }

    public String getPackageName() {
        return packageName;
    }

    public String getTitle() {
        return title;
    }

    public String getText() {
        return text;
    }

    public String getBigText() {
        return bigText;
    }

    public String getSubText() {
        return subText;
    }

    public String getConversationTitle() {
        return conversationTitle;
    }

    public List<String> getTextLines() {
        return textLines;
    }

    public List<RawMessage> getMessages() {
        return messages;
    }

    public long getPostedAtMillis() {
        return postedAtMillis;
    }

    public String getNotificationKey() {
        return notificationKey;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
