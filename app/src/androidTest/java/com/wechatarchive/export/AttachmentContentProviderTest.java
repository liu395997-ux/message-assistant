package com.wechatarchive.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.wechatarchive.data.ArchiveDatabase;
import com.wechatarchive.data.ArchiveDatabaseTestSupport;
import com.wechatarchive.data.AttachmentEntry;
import com.wechatarchive.domain.CapturedMessage;
import com.wechatarchive.domain.importer.AttachmentKind;
import com.wechatarchive.domain.importer.AttachmentStatus;
import com.wechatarchive.importing.AttachmentStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class AttachmentContentProviderTest {
    private Context context;
    private ArchiveDatabase database;
    private AttachmentStore store;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        ArchiveDatabaseTestSupport.resetSharedInstance();
        context.deleteDatabase(ArchiveDatabase.DATABASE_NAME);
        store = new AttachmentStore(context);
        store.clear();
        database = new ArchiveDatabase(context);
    }

    @After
    public void tearDown() {
        database.close();
        ArchiveDatabaseTestSupport.resetSharedInstance();
        context.deleteDatabase(ArchiveDatabase.DATABASE_NAME);
        store.clear();
    }

    @Test
    public void servesStoredAttachmentReadOnly() throws Exception {
        long messageId = database.insertOrGetId(new CapturedMessage(
                "好友",
                "好友",
                "好友",
                "文件",
                1_700_000_000_000L,
                "provider:test",
                false));
        File source = new File(context.getCacheDir(), "provider-test.txt");
        try (FileOutputStream output = new FileOutputStream(source)) {
            output.write("provider content".getBytes(StandardCharsets.UTF_8));
        }
        AttachmentStore.StoredAttachment stored =
                store.store(source, messageId, "测试.txt");
        long attachmentId = database.insertAttachment(new AttachmentEntry(
                0,
                messageId,
                AttachmentKind.DOCUMENT,
                "测试.txt",
                "text/plain",
                stored.getRelativePath(),
                "resources/file/test.txt",
                stored.getSizeBytes(),
                stored.getSha256(),
                AttachmentStatus.AVAILABLE));
        Uri uri = Uri.parse(
                "content://com.wechatarchive.attachments/"
                        + attachmentId
                        + "/"
                        + Uri.encode("测试.txt"));
        ContentResolver resolver = context.getContentResolver();

        try (Cursor cursor = resolver.query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                null,
                null,
                null)) {
            assertTrue(cursor != null);
            assertTrue(cursor.moveToFirst());
            assertEquals("测试.txt", cursor.getString(0));
            assertEquals(stored.getSizeBytes(), cursor.getLong(1));
        }
        byte[] bytes = new byte[32];
        int size;
        try (InputStream input = resolver.openInputStream(uri)) {
            assertTrue(input != null);
            size = input.read(bytes);
        }
        assertEquals("provider content", new String(bytes, 0, size, StandardCharsets.UTF_8));
    }
}
