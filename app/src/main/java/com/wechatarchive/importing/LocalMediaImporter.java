package com.wechatarchive.importing;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.wechatarchive.data.ArchiveDatabase;
import com.wechatarchive.data.AttachmentEntry;
import com.wechatarchive.domain.CapturedMessage;
import com.wechatarchive.domain.importer.AttachmentClassifier;
import com.wechatarchive.domain.importer.AttachmentKind;
import com.wechatarchive.domain.importer.AttachmentStatus;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LocalMediaImporter {
    private static final String CONVERSATION = "本地媒体库";
    private static final String SENDER = "本机导入";
    private final ContentResolver resolver;
    private final ArchiveDatabase database;
    private final AttachmentStore store;

    public LocalMediaImporter(
            Context context,
            ArchiveDatabase database,
            AttachmentStore store) {
        this.resolver = context.getContentResolver();
        this.database = database;
        this.store = store;
    }

    public ImportResult importUris(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) {
            return new ImportResult(0, 0, -1L);
        }

        List<MediaSource> sources = new ArrayList<>();
        int failed = 0;
        for (Uri uri : uris) {
            if (uri == null) {
                failed++;
                continue;
            }
            try {
                sources.add(readMetadata(uri));
            } catch (RuntimeException error) {
                failed++;
            }
        }
        if (sources.isEmpty()) {
            return new ImportResult(0, failed, -1L);
        }

        long messageId = database.insertOrGetId(new CapturedMessage(
                CONVERSATION,
                SENDER,
                "本地媒体",
                summary(sources),
                System.currentTimeMillis(),
                "local-media:" + UUID.randomUUID(),
                false));
        if (messageId <= 0L) {
            return new ImportResult(0, uris.size(), -1L);
        }

        int imported = 0;
        List<String> storedPaths = new ArrayList<>();
        for (MediaSource source : sources) {
            try (InputStream input = resolver.openInputStream(source.uri)) {
                if (input == null) {
                    throw new IOException("无法打开所选文件");
                }
                AttachmentStore.StoredAttachment stored =
                        store.store(input, messageId, source.displayName);
                long attachmentId = database.insertAttachment(new AttachmentEntry(
                        0L,
                        messageId,
                        source.kind,
                        source.displayName,
                        source.mimeType,
                        stored.getRelativePath(),
                        "local-media:" + imported,
                        stored.getSizeBytes(),
                        stored.getSha256(),
                        AttachmentStatus.AVAILABLE));
                if (attachmentId > 0L) {
                    imported++;
                    storedPaths.add(stored.getRelativePath());
                } else {
                    failed++;
                }
            } catch (IOException | RuntimeException error) {
                failed++;
            }
        }

        if (imported == 0) {
            database.delete(messageId);
            store.deletePaths(storedPaths);
            return new ImportResult(0, failed, -1L);
        }
        return new ImportResult(imported, failed, messageId);
    }

    private MediaSource readMetadata(Uri uri) {
        String displayName = displayName(uri);
        String mimeType = resolver.getType(uri);
        if (mimeType == null || mimeType.trim().isEmpty()
                || "application/octet-stream".equals(mimeType)) {
            String guessed = AttachmentClassifier.mimeTypeForName(displayName);
            if (!"application/octet-stream".equals(guessed)) {
                mimeType = guessed;
            }
        }
        if (mimeType == null || mimeType.trim().isEmpty()) {
            mimeType = "application/octet-stream";
        }
        AttachmentKind kind = AttachmentClassifier.classify("", mimeType, displayName);
        return new MediaSource(uri, displayName, mimeType, kind);
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = resolver.query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String value = cursor.getString(0);
                if (value != null && !value.trim().isEmpty()) {
                    return AttachmentClassifier.safeDisplayName(value, "本地文件");
                }
            }
        } catch (RuntimeException ignored) {
            // Some providers reject projection queries; fall back to the URI.
        }
        String fallback = uri.getLastPathSegment();
        return AttachmentClassifier.safeDisplayName(fallback, "本地文件");
    }

    private String summary(List<MediaSource> sources) {
        int images = 0;
        int videos = 0;
        int stickers = 0;
        int documents = 0;
        int audio = 0;
        int other = 0;
        for (MediaSource source : sources) {
            switch (source.kind) {
                case IMAGE:
                    images++;
                    break;
                case VIDEO:
                    videos++;
                    break;
                case ANIMATED_STICKER:
                    stickers++;
                    break;
                case DOCUMENT:
                    documents++;
                    break;
                case AUDIO:
                    audio++;
                    break;
                default:
                    other++;
                    break;
            }
        }
        List<String> parts = new ArrayList<>();
        addCount(parts, "图片", images);
        addCount(parts, "视频", videos);
        addCount(parts, "表情", stickers);
        addCount(parts, "文件", documents);
        addCount(parts, "音频", audio);
        addCount(parts, "其他", other);
        return "已导入 " + String.join(" · ", parts) + "，点击查看";
    }

    private void addCount(List<String> parts, String label, int count) {
        if (count > 0) {
            parts.add(label + " " + count);
        }
    }

    private static final class MediaSource {
        private final Uri uri;
        private final String displayName;
        private final String mimeType;
        private final AttachmentKind kind;

        private MediaSource(
                Uri uri,
                String displayName,
                String mimeType,
                AttachmentKind kind) {
            this.uri = uri;
            this.displayName = displayName;
            this.mimeType = mimeType;
            this.kind = kind;
        }
    }

    public static final class ImportResult {
        private final int importedCount;
        private final int failedCount;
        private final long messageId;

        private ImportResult(int importedCount, int failedCount, long messageId) {
            this.importedCount = importedCount;
            this.failedCount = failedCount;
            this.messageId = messageId;
        }

        public int getImportedCount() {
            return importedCount;
        }

        public int getFailedCount() {
            return failedCount;
        }

        public long getMessageId() {
            return messageId;
        }
    }
}
