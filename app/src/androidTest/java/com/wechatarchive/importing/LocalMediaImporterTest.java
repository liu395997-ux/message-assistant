package com.wechatarchive.importing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.wechatarchive.data.ArchiveDatabase;
import com.wechatarchive.data.ArchiveDatabaseTestSupport;
import com.wechatarchive.data.ArchiveEntry;
import com.wechatarchive.data.AttachmentEntry;
import com.wechatarchive.domain.importer.AttachmentKind;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class LocalMediaImporterTest {
    private Context context;
    private ArchiveDatabase database;
    private AttachmentStore store;
    private File imageFile;
    private File textFile;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        ArchiveDatabaseTestSupport.resetSharedInstance();
        context.deleteDatabase(ArchiveDatabase.DATABASE_NAME);
        store = new AttachmentStore(context);
        store.clear();
        database = new ArchiveDatabase(context);

        imageFile = new File(context.getCacheDir(), "local-import-image.png");
        Bitmap bitmap = Bitmap.createBitmap(5, 3, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(0xffaa4433);
        try (FileOutputStream output = new FileOutputStream(imageFile)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
        bitmap.recycle();

        textFile = new File(context.getCacheDir(), "local-import-notes.txt");
        try (FileOutputStream output = new FileOutputStream(textFile)) {
            output.write("本地文件预览".getBytes(StandardCharsets.UTF_8));
        }
    }

    @After
    public void tearDown() {
        database.close();
        ArchiveDatabaseTestSupport.resetSharedInstance();
        context.deleteDatabase(ArchiveDatabase.DATABASE_NAME);
        store.clear();
        imageFile.delete();
        textFile.delete();
    }

    @Test
    public void importsImageAndTextIntoOneVisibleRecord() throws Exception {
        LocalMediaImporter importer = new LocalMediaImporter(context, database, store);

        LocalMediaImporter.ImportResult result = importer.importUris(Arrays.asList(
                Uri.fromFile(imageFile),
                Uri.fromFile(textFile)));

        assertEquals(2, result.getImportedCount());
        assertEquals(0, result.getFailedCount());
        assertTrue(result.getMessageId() > 0L);
        assertEquals(1, database.count());

        List<ArchiveEntry> entries = database.query("", 10);
        assertEquals(1, entries.size());
        assertEquals("本地媒体库", entries.get(0).getConversation());
        assertEquals(2, entries.get(0).getAttachments().size());

        boolean imageFound = false;
        boolean textFound = false;
        for (AttachmentEntry attachment : entries.get(0).getAttachments()) {
            assertTrue(attachment.isAvailable());
            assertTrue(store.resolve(attachment.getLocalPath()).isFile());
            if (attachment.getKind() == AttachmentKind.IMAGE) {
                imageFound = true;
            } else if (attachment.getKind() == AttachmentKind.DOCUMENT) {
                textFound = true;
            }
        }
        assertTrue(imageFound);
        assertTrue(textFound);
    }

    @Test
    public void reportsInvalidUriWithoutCrashing() {
        LocalMediaImporter importer = new LocalMediaImporter(context, database, store);

        LocalMediaImporter.ImportResult result = importer.importUris(Arrays.asList(
                Uri.parse("content://com.wechatarchive.missing/not-found")));

        assertEquals(0, result.getImportedCount());
        assertEquals(1, result.getFailedCount());
        assertEquals(0, database.count());
    }
}
