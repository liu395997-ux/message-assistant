package com.wechatarchive.importing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.wechatarchive.data.ArchiveDatabase;
import com.wechatarchive.data.ArchiveDatabaseTestSupport;
import com.wechatarchive.data.AttachmentEntry;
import com.wechatarchive.domain.CapturedMessage;
import com.wechatarchive.domain.importer.AttachmentKind;

import java.io.File;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class NotificationPictureArchiverTest {
    private Context context;
    private ArchiveDatabase database;
    private AttachmentStore store;
    private long messageId;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        ArchiveDatabaseTestSupport.resetSharedInstance();
        context.deleteDatabase(ArchiveDatabase.DATABASE_NAME);
        store = new AttachmentStore(context);
        store.clear();
        database = new ArchiveDatabase(context);
        messageId = database.insertOrGetId(new CapturedMessage(
                "项目群",
                "李老师",
                "李老师",
                "看图片",
                1_700_000_000_000L,
                "notification:picture:test",
                false));
    }

    @After
    public void tearDown() {
        database.close();
        ArchiveDatabaseTestSupport.resetSharedInstance();
        context.deleteDatabase(ArchiveDatabase.DATABASE_NAME);
        store.clear();
    }

    @Test
    public void archivesNotificationBitmapAndResolvesStoredFile() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(6, 4, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(0xff2f6f4e);

        boolean archived = NotificationPictureArchiver.archive(
                context,
                database,
                store,
                bitmap,
                messageId,
                "notification:picture:test");

        assertTrue(archived);
        List<AttachmentEntry> attachments = database.attachmentsForMessage(messageId);
        assertEquals(1, attachments.size());
        AttachmentEntry attachment = attachments.get(0);
        assertEquals(AttachmentKind.IMAGE, attachment.getKind());
        assertEquals("image/png", attachment.getMimeType());
        assertTrue(attachment.isAvailable());
        File file = store.resolve(attachment.getLocalPath());
        assertTrue(file.isFile());
        assertTrue(file.length() > 0L);
        bitmap.recycle();
    }

    @Test
    public void rejectsInvalidBitmapAndMessageId() {
        assertFalse(NotificationPictureArchiver.archive(
                context,
                database,
                store,
                null,
                messageId,
                "notification:null"));
        assertFalse(NotificationPictureArchiver.archive(
                context,
                database,
                store,
                Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888),
                0L,
                "notification:invalid"));
        assertTrue(database.attachmentsForMessage(messageId).isEmpty());
    }
}
