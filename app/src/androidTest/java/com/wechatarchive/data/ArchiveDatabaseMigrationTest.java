package com.wechatarchive.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.wechatarchive.domain.CapturedMessage;
import com.wechatarchive.domain.importer.AttachmentKind;
import com.wechatarchive.domain.importer.AttachmentStatus;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ArchiveDatabaseMigrationTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(ArchiveDatabase.DATABASE_NAME);
    }

    @After
    public void tearDown() {
        context.deleteDatabase(ArchiveDatabase.DATABASE_NAME);
    }

    @Test
    public void migratesVersionOneWithoutLosingMessages() {
        createVersionOneDatabase();

        ArchiveDatabase database = new ArchiveDatabase(context);
        List<ArchiveEntry> entries = database.query("", 10);

        assertEquals(1, entries.size());
        assertEquals("旧消息", entries.get(0).getBody());
        assertEquals(2, database.getReadableDatabase().getVersion());
        database.close();
    }

    @Test
    public void storesAndDeletesAttachmentsWithMessage() {
        ArchiveDatabase database = new ArchiveDatabase(context);
        long messageId = database.insertOrGetId(new CapturedMessage(
                "好友",
                "好友",
                "好友",
                "看照片",
                1_700_000_000_000L,
                "test:attachment",
                false));
        AttachmentEntry attachment = new AttachmentEntry(
                0,
                messageId,
                AttachmentKind.IMAGE,
                "photo.jpg",
                "image/jpeg",
                "attachments/1/photo.jpg",
                "chat_assets/resources/image/photo.jpg",
                1024,
                "abc123",
                AttachmentStatus.AVAILABLE);

        long attachmentId = database.insertAttachment(attachment);
        List<AttachmentEntry> loaded = database.attachmentsForMessage(messageId);

        assertTrue(attachmentId > 0);
        assertEquals(1, loaded.size());
        assertEquals("photo.jpg", loaded.get(0).getDisplayName());
        assertEquals(AttachmentKind.IMAGE, loaded.get(0).getKind());
        assertTrue(loaded.get(0).isAvailable());

        assertEquals(1, database.delete(messageId));
        assertTrue(database.attachmentsForMessage(messageId).isEmpty());
        database.close();
    }

    @Test
    public void repeatedMessageInsertReturnsExistingId() {
        ArchiveDatabase database = new ArchiveDatabase(context);
        CapturedMessage message = new CapturedMessage(
                "好友",
                "好友",
                "好友",
                "你好",
                1_700_000_000_000L,
                "test:stable",
                false);

        long first = database.insertOrGetId(message);
        long second = database.insertOrGetId(message);

        assertEquals(first, second);
        assertEquals(1, database.count());
        assertFalse(database.insertAttachment(null) > 0);
        database.close();
    }

    @Test
    public void unavailableAttachmentCanBeUpgradedByCompleteReimport() {
        ArchiveDatabase database = new ArchiveDatabase(context);
        long messageId = database.insertOrGetId(new CapturedMessage(
                "好友",
                "好友",
                "好友",
                "看附件",
                1_700_000_000_000L,
                "test:upgrade",
                false));
        AttachmentEntry missing = new AttachmentEntry(
                0,
                messageId,
                AttachmentKind.DOCUMENT,
                "report.pdf",
                "application/pdf",
                "",
                "chat_assets/resources/file/report.pdf",
                0,
                "",
                AttachmentStatus.MISSING);
        AttachmentEntry available = new AttachmentEntry(
                0,
                messageId,
                AttachmentKind.DOCUMENT,
                "report.pdf",
                "application/pdf",
                "attachments/1/report.pdf",
                "chat_assets/resources/file/report.pdf",
                4096,
                "sha-upgrade",
                AttachmentStatus.AVAILABLE);

        long missingId = database.insertAttachment(missing);
        long upgradedId = database.insertAttachment(available);

        assertEquals(missingId, upgradedId);
        AttachmentEntry loaded =
                database.attachmentsForMessage(messageId).get(0);
        assertTrue(loaded.isAvailable());
        assertEquals("attachments/1/report.pdf", loaded.getLocalPath());
        assertEquals(4096, loaded.getSizeBytes());
        database.close();
    }

    private void createVersionOneDatabase() {
        SQLiteDatabase legacy = context.openOrCreateDatabase(
                ArchiveDatabase.DATABASE_NAME,
                Context.MODE_PRIVATE,
                null);
        legacy.execSQL(
                "CREATE TABLE messages ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "fingerprint TEXT NOT NULL UNIQUE,"
                        + "conversation TEXT NOT NULL,"
                        + "sender TEXT NOT NULL,"
                        + "title TEXT NOT NULL,"
                        + "body TEXT NOT NULL,"
                        + "occurred_at INTEGER NOT NULL,"
                        + "captured_at INTEGER NOT NULL,"
                        + "is_summary INTEGER NOT NULL DEFAULT 0)");
        legacy.execSQL(
                "INSERT INTO messages "
                        + "(fingerprint,conversation,sender,title,body,occurred_at,captured_at,is_summary) "
                        + "VALUES ('legacy','好友','好友','好友','旧消息',1000,2000,0)");
        legacy.setVersion(1);
        legacy.close();
    }
}
