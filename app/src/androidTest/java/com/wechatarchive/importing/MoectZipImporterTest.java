package com.wechatarchive.importing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.wechatarchive.data.ArchiveDatabase;
import com.wechatarchive.data.ArchiveEntry;
import com.wechatarchive.data.AttachmentEntry;
import com.wechatarchive.importing.AttachmentStore;
import com.wechatarchive.importing.MoectZipImporter;
import com.wechatarchive.importing.MoectZipImporter.ImportResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class MoectZipImporterTest {
    private Context context;
    private ArchiveDatabase database;
    private AttachmentStore store;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(ArchiveDatabase.DATABASE_NAME);
        store = new AttachmentStore(context);
        store.clear();
        database = new ArchiveDatabase(context);
    }

    @After
    public void tearDown() {
        database.close();
        context.deleteDatabase(ArchiveDatabase.DATABASE_NAME);
        store.clear();
    }

    @Test
    public void importsChatAndMixedAttachmentAndIsIdempotent() throws Exception {
        File zip = createFixtureZip();
        MoectZipImporter importer = new MoectZipImporter(database, store);

        ImportResult first = importer.importFrom(
                () -> new FileInputStream(zip));

        assertEquals(1, first.getArchiveCount());
        assertEquals(2, first.getMessageCount());
        assertEquals(1, first.getAvailableAttachmentCount());
        List<ArchiveEntry> entries = database.query("", 20);
        assertEquals(2, entries.size());
        ArchiveEntry imageMessage = entries.stream()
                .filter(entry -> entry.getBody().equals("[图片]"))
                .findFirst()
                .orElseThrow();
        assertEquals(1, imageMessage.getAttachments().size());
        AttachmentEntry attachment = imageMessage.getAttachments().get(0);
        assertTrue(attachment.isAvailable());
        assertTrue(new File(context.getFilesDir(), attachment.getLocalPath()).isFile());
        assertEquals("image/jpeg", attachment.getMimeType());

        ImportResult second = importer.importFrom(
                () -> new FileInputStream(zip));

        assertEquals(2, database.query("", 20).size());
        assertEquals(1, database.attachmentCount());
        assertEquals(1, second.getDuplicateAttachmentCount());

        File storedFile = new File(
                context.getFilesDir(),
                attachment.getLocalPath());
        List<String> paths = database.attachmentLocalPaths();
        assertEquals(1, database.delete(imageMessage.getId()));
        store.deletePaths(paths);
        assertFalse(storedFile.exists());
        assertTrue(database.attachmentsForMessage(imageMessage.getId()).isEmpty());
    }

    private File createFixtureZip() throws Exception {
        File file = new File(context.getCacheDir(), "moect-fixture.zip");
        String json = """
                {
                  "metadata": {"name": "好友", "session": "wxid_friend"},
                  "messages": [
                    {
                      "server_id": "1",
                      "sender": "好友",
                      "create_time": 1700000000,
                      "text": "你好",
                      "kind": "text",
                      "resources": []
                    },
                    {
                      "server_id": "2",
                      "sender": "我",
                      "create_time": 1700000010,
                      "text": "",
                      "kind": "image",
                      "resources": [{
                        "kind": "image",
                        "path": "chat_assets/resources/image/photo.jpg",
                        "name": "photo.jpg",
                        "status": "available"
                      }]
                    }
                  ]
                }
                """;
        try (ZipOutputStream zip =
                     new ZipOutputStream(new FileOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("conversation/chat.json"));
            zip.write(json.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(
                    "conversation/chat_assets/resources/image/photo.jpg"));
            zip.write(new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2, 3});
            zip.closeEntry();
        }
        return file;
    }
}
