package com.wechatarchive.data;

import com.wechatarchive.domain.importer.AttachmentKind;
import com.wechatarchive.domain.importer.AttachmentStatus;

import java.util.Objects;

public final class AttachmentEntry {
    private final long id;
    private final long messageId;
    private final AttachmentKind kind;
    private final String displayName;
    private final String mimeType;
    private final String localPath;
    private final String sourcePath;
    private final long sizeBytes;
    private final String sha256;
    private final AttachmentStatus status;

    public AttachmentEntry(
            long id,
            long messageId,
            AttachmentKind kind,
            String displayName,
            String mimeType,
            String localPath,
            String sourcePath,
            long sizeBytes,
            String sha256,
            AttachmentStatus status) {
        this.id = id;
        this.messageId = messageId;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.mimeType = Objects.requireNonNull(mimeType, "mimeType");
        this.localPath = localPath == null ? "" : localPath;
        this.sourcePath = sourcePath == null ? "" : sourcePath;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256 == null ? "" : sha256;
        this.status = Objects.requireNonNull(status, "status");
    }

    public long getId() {
        return id;
    }

    public long getMessageId() {
        return messageId;
    }

    public AttachmentKind getKind() {
        return kind;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getLocalPath() {
        return localPath;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public AttachmentStatus getStatus() {
        return status;
    }

    public boolean isAvailable() {
        return status == AttachmentStatus.AVAILABLE && !localPath.trim().isEmpty();
    }
}
