package com.wechatarchive.domain.importer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class MoectArchive {
    private final String name;
    private final String sessionId;
    private final List<MoectMessage> messages;

    public MoectArchive(String name, String sessionId, List<MoectMessage> messages) {
        this.name = Objects.requireNonNull(name, "name");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.messages = messages == null || messages.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(messages));
    }

    public String getName() {
        return name;
    }

    public String getSessionId() {
        return sessionId;
    }

    public List<MoectMessage> getMessages() {
        return messages;
    }
}
