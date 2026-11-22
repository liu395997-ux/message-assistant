package com.wechatarchive.domain.importer;

import java.util.Objects;

public final class MoectResource {
    private final String sourcePath;
    private final String displayName;
    private final AttachmentKind kind;
    private final AttachmentStatus status;

    public MoectResource(
            String sourcePath,
            String displayName,
            AttachmentKind kind,
            AttachmentStatus status) {
        this.sourcePath = sourcePath == null ? "" : sourcePath;
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.status = Objects.requireNonNull(status, "status");
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AttachmentKind getKind() {
        return kind;
    }

    public AttachmentStatus getStatus() {
        return status;
    }

    public boolean hasSourcePath() {
        return status == AttachmentStatus.AVAILABLE
                && !sourcePath.trim().isEmpty();
    }

    public String getMimeType() {
        return AttachmentClassifier.mimeTypeForName(displayName);
    }
}
