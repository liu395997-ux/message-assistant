package com.wechatarchive.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.wechatarchive.domain.CapturedMessage;
import com.wechatarchive.domain.MessageFingerprint;
import com.wechatarchive.domain.importer.AttachmentKind;
import com.wechatarchive.domain.importer.AttachmentStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArchiveDatabase extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "wechat_archive.db";
    private static final int DATABASE_VERSION = 2;

    private static final String TABLE_MESSAGES = "messages";
    private static final String TABLE_ATTACHMENTS = "attachments";
    private static final String COL_ID = "id";
    private static final String COL_FINGERPRINT = "fingerprint";
    private static final String COL_CONVERSATION = "conversation";
    private static final String COL_SENDER = "sender";
    private static final String COL_TITLE = "title";
    private static final String COL_BODY = "body";
    private static final String COL_OCCURRED_AT = "occurred_at";
    private static final String COL_CAPTURED_AT = "captured_at";
    private static final String COL_SUMMARY = "is_summary";
    private static final String ATTACHMENT_ID = "id";
    private static final String ATTACHMENT_MESSAGE_ID = "message_id";
    private static final String ATTACHMENT_KIND = "kind";
    private static final String ATTACHMENT_NAME = "display_name";
    private static final String ATTACHMENT_MIME = "mime_type";
    private static final String ATTACHMENT_LOCAL_PATH = "local_path";
    private static final String ATTACHMENT_SOURCE_PATH = "source_path";
    private static final String ATTACHMENT_SIZE = "size_bytes";
    private static final String ATTACHMENT_SHA256 = "sha256";
    private static final String ATTACHMENT_STATUS = "status";
    private static final String ATTACHMENT_CREATED_AT = "created_at";

    private static volatile ArchiveDatabase instance;

    public static ArchiveDatabase getInstance(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        ArchiveDatabase current = instance;
        if (current == null) {
            synchronized (ArchiveDatabase.class) {
                current = instance;
                if (current == null) {
                    current = new ArchiveDatabase(context.getApplicationContext());
                    instance = current;
                }
            }
        }
        return current;
    }

    public ArchiveDatabase(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    static void resetInstanceForTests() {
        synchronized (ArchiveDatabase.class) {
            if (instance != null) {
                instance.close();
                instance = null;
            }
        }
    }

    @Override
    public void onConfigure(SQLiteDatabase database) {
        super.onConfigure(database);
        database.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(
                "CREATE TABLE " + TABLE_MESSAGES + " ("
                        + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + COL_FINGERPRINT + " TEXT NOT NULL UNIQUE, "
                        + COL_CONVERSATION + " TEXT NOT NULL, "
                        + COL_SENDER + " TEXT NOT NULL, "
                        + COL_TITLE + " TEXT NOT NULL, "
                        + COL_BODY + " TEXT NOT NULL, "
                        + COL_OCCURRED_AT + " INTEGER NOT NULL, "
                        + COL_CAPTURED_AT + " INTEGER NOT NULL, "
                        + COL_SUMMARY + " INTEGER NOT NULL DEFAULT 0"
                        + ")");
        database.execSQL(
                "CREATE INDEX idx_messages_conversation_time ON "
                        + TABLE_MESSAGES + " (" + COL_CONVERSATION + ", "
                        + COL_OCCURRED_AT + " DESC)");
        database.execSQL(
                "CREATE INDEX idx_messages_time ON "
                        + TABLE_MESSAGES + " (" + COL_OCCURRED_AT + " DESC)");
        createAttachmentsTable(database);
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            createAttachmentsTable(database);
        }
    }

    public boolean insert(CapturedMessage message) {
        return insertOrGetId(message, false) != -1;
    }

    public long insertOrGetId(CapturedMessage message) {
        return insertOrGetId(message, true);
    }

    private long insertOrGetId(CapturedMessage message, boolean existingOnConflict) {
        if (message == null) {
            return -1;
        }
        String fingerprint = MessageFingerprint.of(message);
        ContentValues values = new ContentValues();
        values.put(COL_FINGERPRINT, fingerprint);
        values.put(COL_CONVERSATION, message.getConversation());
        values.put(COL_SENDER, message.getSender());
        values.put(COL_TITLE, message.getTitle());
        values.put(COL_BODY, message.getBody());
        values.put(COL_OCCURRED_AT, message.getOccurredAtMillis());
        values.put(COL_CAPTURED_AT, System.currentTimeMillis());
        values.put(COL_SUMMARY, message.isSummary() ? 1 : 0);
        long rowId = getWritableDatabase().insertWithOnConflict(
                TABLE_MESSAGES,
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE);
        if (rowId != -1 || !existingOnConflict) {
            return rowId;
        }
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_MESSAGES,
                new String[]{COL_ID},
                COL_FINGERPRINT + " = ?",
                new String[]{fingerprint},
                null,
                null,
                null,
                "1")) {
            return cursor.moveToFirst() ? cursor.getLong(0) : -1;
        }
    }

    public List<ArchiveEntry> query(String search, int limit) {
        String query = search == null ? "" : search.trim();
        String selection = null;
        List<String> arguments = new ArrayList<>();
        if (!query.isEmpty()) {
            selection = "(" + COL_CONVERSATION + " LIKE ? OR "
                    + COL_SENDER + " LIKE ? OR "
                    + COL_TITLE + " LIKE ? OR "
                    + COL_BODY + " LIKE ? OR " + COL_ID + " IN ("
                    + "SELECT " + ATTACHMENT_MESSAGE_ID + " FROM "
                    + TABLE_ATTACHMENTS + " WHERE " + ATTACHMENT_NAME + " LIKE ?))";
            String like = "%" + query + "%";
            arguments.add(like);
            arguments.add(like);
            arguments.add(like);
            arguments.add(like);
            arguments.add(like);
        }

        int safeLimit = limit <= 0 ? 500 : Math.min(limit, 5000);
        return readEntries(
                selection,
                arguments.isEmpty() ? null : arguments.toArray(new String[0]),
                String.valueOf(safeLimit));
    }

    public List<ArchiveEntry> allForExport() {
        return readEntries(null, null, null);
    }

    private List<ArchiveEntry> readEntries(
            String selection,
            String[] selectionArguments,
            String limit) {
        String[] columns = {
                COL_ID,
                COL_FINGERPRINT,
                COL_CONVERSATION,
                COL_SENDER,
                COL_TITLE,
                COL_BODY,
                COL_OCCURRED_AT,
                COL_CAPTURED_AT,
                COL_SUMMARY
        };
        List<ArchiveEntry> entries = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_MESSAGES,
                columns,
                selection,
                selectionArguments,
                null,
                null,
                COL_OCCURRED_AT + " DESC, " + COL_ID + " DESC",
                limit)) {
            while (cursor.moveToNext()) {
                entries.add(new ArchiveEntry(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4),
                        cursor.getString(5),
                        cursor.getLong(6),
                        cursor.getLong(7),
                        cursor.getInt(8) != 0));
            }
        }
        if (!entries.isEmpty()) {
            List<Long> ids = new ArrayList<>(entries.size());
            for (ArchiveEntry entry : entries) {
                ids.add(entry.getId());
            }
            Map<Long, List<AttachmentEntry>> byMessage =
                    attachmentsForMessages(ids);
            List<ArchiveEntry> withAttachments = new ArrayList<>(entries.size());
            for (ArchiveEntry entry : entries) {
                withAttachments.add(new ArchiveEntry(
                        entry.getId(),
                        entry.getFingerprint(),
                        entry.getConversation(),
                        entry.getSender(),
                        entry.getTitle(),
                        entry.getBody(),
                        entry.getOccurredAtMillis(),
                        entry.getCapturedAtMillis(),
                        entry.isSummary(),
                        byMessage.get(entry.getId())));
            }
            return withAttachments;
        }
        return entries;
    }

    public int count() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_MESSAGES,
                null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public int delete(long id) {
        return getWritableDatabase().delete(
                TABLE_MESSAGES,
                COL_ID + " = ?",
                new String[]{String.valueOf(id)});
    }

    public int clear() {
        return getWritableDatabase().delete(TABLE_MESSAGES, null, null);
    }

    public long insertAttachment(AttachmentEntry attachment) {
        if (attachment == null || attachment.getMessageId() <= 0) {
            return -1;
        }
        ExistingAttachment existing = existingAttachment(
                attachment.getMessageId(),
                attachment.getSourcePath(),
                attachment.getDisplayName());
        if (existing != null) {
            if (attachment.isAvailable()
                    && existing.status != AttachmentStatus.AVAILABLE) {
                getWritableDatabase().update(
                        TABLE_ATTACHMENTS,
                        attachmentValues(attachment),
                        ATTACHMENT_ID + " = ?",
                        new String[]{String.valueOf(existing.id)});
                return existing.id;
            }
            return -1;
        }
        ContentValues values = attachmentValues(attachment);
        return getWritableDatabase().insertWithOnConflict(
                TABLE_ATTACHMENTS,
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE);
    }

    private ExistingAttachment existingAttachment(
            long messageId,
            String sourcePath,
            String displayName) {
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_ATTACHMENTS,
                new String[]{ATTACHMENT_ID, ATTACHMENT_STATUS},
                ATTACHMENT_MESSAGE_ID + " = ? AND "
                        + ATTACHMENT_SOURCE_PATH + " = ? AND "
                        + ATTACHMENT_NAME + " = ?",
                new String[]{
                        String.valueOf(messageId),
                        sourcePath == null ? "" : sourcePath,
                        displayName == null ? "" : displayName
                },
                null,
                null,
                null,
                "1")) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new ExistingAttachment(
                    cursor.getLong(0),
                    enumValue(
                            AttachmentStatus.class,
                            cursor.getString(1),
                            AttachmentStatus.UNSUPPORTED));
        }
    }

    public List<AttachmentEntry> attachmentsForMessage(long messageId) {
        return attachmentsForMessages(Collections.singletonList(messageId))
                .getOrDefault(messageId, Collections.emptyList());
    }

    public Map<Long, List<AttachmentEntry>> attachmentsForMessages(
            Collection<Long> messageIds) {
        Map<Long, List<AttachmentEntry>> result = new LinkedHashMap<>();
        if (messageIds == null || messageIds.isEmpty()) {
            return result;
        }
        List<String> ids = new ArrayList<>();
        for (Long id : messageIds) {
            if (id != null && id > 0) {
                ids.add(String.valueOf(id));
            }
        }
        if (ids.isEmpty()) {
            return result;
        }
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String[] columns = {
                ATTACHMENT_ID,
                ATTACHMENT_MESSAGE_ID,
                ATTACHMENT_KIND,
                ATTACHMENT_NAME,
                ATTACHMENT_MIME,
                ATTACHMENT_LOCAL_PATH,
                ATTACHMENT_SOURCE_PATH,
                ATTACHMENT_SIZE,
                ATTACHMENT_SHA256,
                ATTACHMENT_STATUS
        };
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_ATTACHMENTS,
                columns,
                ATTACHMENT_MESSAGE_ID + " IN (" + placeholders + ")",
                ids.toArray(new String[0]),
                null,
                null,
                ATTACHMENT_ID + " ASC")) {
            while (cursor.moveToNext()) {
                AttachmentEntry entry = attachmentFromCursor(cursor);
                result.computeIfAbsent(
                                entry.getMessageId(),
                                ignored -> new ArrayList<>())
                        .add(entry);
            }
        }
        return result;
    }

    public AttachmentEntry attachment(long id) {
        String[] columns = {
                ATTACHMENT_ID,
                ATTACHMENT_MESSAGE_ID,
                ATTACHMENT_KIND,
                ATTACHMENT_NAME,
                ATTACHMENT_MIME,
                ATTACHMENT_LOCAL_PATH,
                ATTACHMENT_SOURCE_PATH,
                ATTACHMENT_SIZE,
                ATTACHMENT_SHA256,
                ATTACHMENT_STATUS
        };
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_ATTACHMENTS,
                columns,
                ATTACHMENT_ID + " = ?",
                new String[]{String.valueOf(id)},
                null,
                null,
                null,
                "1")) {
            return cursor.moveToFirst() ? attachmentFromCursor(cursor) : null;
        }
    }

    public List<String> attachmentLocalPaths() {
        List<String> paths = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_ATTACHMENTS,
                new String[]{ATTACHMENT_LOCAL_PATH},
                ATTACHMENT_LOCAL_PATH + " <> ''",
                null,
                null,
                null,
                null)) {
            while (cursor.moveToNext()) {
                paths.add(cursor.getString(0));
            }
        }
        return paths;
    }

    public int attachmentCount() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_ATTACHMENTS,
                null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private void createAttachmentsTable(SQLiteDatabase database) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_ATTACHMENTS + " ("
                        + ATTACHMENT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + ATTACHMENT_MESSAGE_ID + " INTEGER NOT NULL, "
                        + ATTACHMENT_KIND + " TEXT NOT NULL, "
                        + ATTACHMENT_NAME + " TEXT NOT NULL, "
                        + ATTACHMENT_MIME + " TEXT NOT NULL, "
                        + ATTACHMENT_LOCAL_PATH + " TEXT NOT NULL, "
                        + ATTACHMENT_SOURCE_PATH + " TEXT NOT NULL, "
                        + ATTACHMENT_SIZE + " INTEGER NOT NULL, "
                        + ATTACHMENT_SHA256 + " TEXT NOT NULL, "
                        + ATTACHMENT_STATUS + " TEXT NOT NULL, "
                        + ATTACHMENT_CREATED_AT + " INTEGER NOT NULL, "
                        + "FOREIGN KEY (" + ATTACHMENT_MESSAGE_ID + ") REFERENCES "
                        + TABLE_MESSAGES + "(" + COL_ID + ") ON DELETE CASCADE"
                        + ")");
        database.execSQL(
                "CREATE INDEX idx_attachments_message ON "
                        + TABLE_ATTACHMENTS + " (" + ATTACHMENT_MESSAGE_ID + ")");
        database.execSQL(
                "CREATE INDEX idx_attachments_name ON "
                        + TABLE_ATTACHMENTS + " (" + ATTACHMENT_NAME + ")");
    }

    private ContentValues attachmentValues(AttachmentEntry attachment) {
        ContentValues values = new ContentValues();
        values.put(ATTACHMENT_MESSAGE_ID, attachment.getMessageId());
        values.put(ATTACHMENT_KIND, attachment.getKind().name());
        values.put(ATTACHMENT_NAME, attachment.getDisplayName());
        values.put(ATTACHMENT_MIME, attachment.getMimeType());
        values.put(ATTACHMENT_LOCAL_PATH, attachment.getLocalPath());
        values.put(ATTACHMENT_SOURCE_PATH, attachment.getSourcePath());
        values.put(ATTACHMENT_SIZE, attachment.getSizeBytes());
        values.put(ATTACHMENT_SHA256, attachment.getSha256());
        values.put(ATTACHMENT_STATUS, attachment.getStatus().name());
        values.put(ATTACHMENT_CREATED_AT, System.currentTimeMillis());
        return values;
    }

    private AttachmentEntry attachmentFromCursor(Cursor cursor) {
        return new AttachmentEntry(
                cursor.getLong(0),
                cursor.getLong(1),
                enumValue(AttachmentKind.class, cursor.getString(2), AttachmentKind.OTHER),
                cursor.getString(3),
                cursor.getString(4),
                cursor.getString(5),
                cursor.getString(6),
                cursor.getLong(7),
                cursor.getString(8),
                enumValue(
                        AttachmentStatus.class,
                        cursor.getString(9),
                        AttachmentStatus.UNSUPPORTED));
    }

    private <T extends Enum<T>> T enumValue(
            Class<T> type,
            String value,
            T fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException | NullPointerException error) {
            return fallback;
        }
    }

    private static final class ExistingAttachment {
        private final long id;
        private final AttachmentStatus status;

        private ExistingAttachment(long id, AttachmentStatus status) {
            this.id = id;
            this.status = status;
        }
    }
}
