package com.wechatarchive.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ArchiveEntry {
    private final long id;
    private final String fingerprint;
    private final String conversation;
    private final String sender;
    private final String title;
    private final String body;
    private final long occurredAtMillis;
    private final long capturedAtMillis;
    private final boolean summary;
    private final List<AttachmentEntry> attachments;

    public ArchiveEntry(
            long id,
            String fingerprint,
            String conversation,
            String sender,
            String title,
            String body,
            long occurredAtMillis,
            long capturedAtMillis,
            boolean summary) {
        this(
                id,
                fingerprint,
                conversation,
                sender,
                title,
                body,
                occurredAtMillis,
                capturedAtMillis,
                summary,
                Collections.emptyList());
    }

    public ArchiveEntry(
            long id,
            String fingerprint,
            String conversation,
            String sender,
            String title,
            String body,
            long occurredAtMillis,
            long capturedAtMillis,
            boolean summary,
            List<AttachmentEntry> attachments) {
        this.id = id;
        this.fingerprint = fingerprint;
        this.conversation = conversation;
        this.sender = sender;
        this.title = title;
        this.body = body;
        this.occurredAtMillis = occurredAtMillis;
        this.capturedAtMillis = capturedAtMillis;
        this.summary = summary;
        this.attachments = attachments == null || attachments.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(attachments));
    }

    public long getId() {
        return id;
    }

    public String getFingerprint() {
        return fingerprint;
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

    public long getCapturedAtMillis() {
        return capturedAtMillis;
    }

    public boolean isSummary() {
        return summary;
    }

    public List<AttachmentEntry> getAttachments() {
        return attachments;
    }

    public com.wechatarchive.domain.CapturedMessage toCapturedMessage() {
        return new com.wechatarchive.domain.CapturedMessage(
                conversation,
                sender,
                title,
                body,
                occurredAtMillis,
                fingerprint,
                summary);
    }
}
