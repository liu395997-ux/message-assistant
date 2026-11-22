package com.wechatarchive.domain;

public final class RawMessage {
    private final String sender;
    private final String text;
    private final long timestampMillis;

    public RawMessage(String sender, String text, long timestampMillis) {
        this.sender = sender == null ? "" : sender;
        this.text = text == null ? "" : text;
        this.timestampMillis = timestampMillis;
    }

    public String getSender() {
        return sender;
    }

    public String getText() {
        return text;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }
}
