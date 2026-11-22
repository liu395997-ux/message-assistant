package com.wechatarchive.domain;

import java.util.Objects;

public final class CapturedMessage {
    private final String conversation;
    private final String sender;
    private final String title;
    private final String body;
    private final long occurredAtMillis;
    private final String notificationKey;
    private final boolean summary;

    public CapturedMessage(
            String conversation,
            String sender,
            String title,
            String body,
            long occurredAtMillis,
            String notificationKey,
            boolean summary) {
        this.conversation = Objects.requireNonNull(conversation, "conversation");
        this.sender = Objects.requireNonNull(sender, "sender");
        this.title = Objects.requireNonNull(title, "title");
        this.body = Objects.requireNonNull(body, "body");
        this.occurredAtMillis = occurredAtMillis;
        this.notificationKey = Objects.requireNonNull(notificationKey, "notificationKey");
        this.summary = summary;
    }

    public String getConversation() {
        return conversation;
    }

    public String getSender() {
        return sender;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public long getOccurredAtMillis() {
        return occurredAtMillis;
    }

    public String getNotificationKey() {
        return notificationKey;
    }

    public boolean isSummary() {
        return summary;
    }
}
