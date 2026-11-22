package com.wechatarchive.domain;

import java.util.Objects;

public final class AttachmentDescriptor {
    private final String displayName;
    private final String label;
    private final boolean stored;

    public AttachmentDescriptor(String displayName, String label, boolean stored) {
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.label = Objects.requireNonNull(label, "label");
        this.stored = stored;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLabel() {
        return label;
    }

    public boolean isStored() {
        return stored;
    }
}
