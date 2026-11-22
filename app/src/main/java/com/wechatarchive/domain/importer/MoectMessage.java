package com.wechatarchive.domain.importer;

import com.wechatarchive.domain.CapturedMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class MoectMessage {
    private final String conversation;
    private final String sender;
    private final String body;
    private final long occurredAtMillis;
    private final String stableKey;
    private final List<MoectResource> resources;

    public MoectMessage(
            String conversation,
            String sender,
            String body,
            long occurredAtMillis,
            String stableKey,
            List<MoectResource> resources) {
        this.conversation = Objects.requireNonNull(conversation, "conversation");
        this.sender = Objects.requireNonNull(sender, "sender");
        this.body = Objects.requireNonNull(body, "body");
        this.occurredAtMillis = occurredAtMillis;
        this.stableKey = Objects.requireNonNull(stableKey, "stableKey");
        this.resources = resources == null || resources.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(resources));
    }

    public String getConversation() {
        return conversation;
    }

    public String getSender() {
        return sender;
    }

    public String getBody() {
        return body;
    }

    public long getOccurredAtMillis() {
        return occurredAtMillis;
    }

    public String getStableKey() {
        return stableKey;
    }

    public List<MoectResource> getResources() {
        return resources;
    }

    public CapturedMessage toCapturedMessage() {
        return new CapturedMessage(
                conversation,
                sender,
                sender,
                body,
                occurredAtMillis,
                stableKey,
                false);
    }
}
