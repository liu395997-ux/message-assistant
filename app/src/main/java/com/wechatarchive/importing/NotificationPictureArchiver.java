package com.wechatarchive.importing;

import android.content.Context;
import android.graphics.Bitmap;

import com.wechatarchive.data.ArchiveDatabase;
import com.wechatarchive.data.AttachmentEntry;
import com.wechatarchive.domain.importer.AttachmentKind;
import com.wechatarchive.domain.importer.AttachmentStatus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

public final class NotificationPictureArchiver {
    private static final String DISPLAY_NAME = "通知图片.png";

    private NotificationPictureArchiver() {
    }

    public static boolean archive(
            Context context,
            ArchiveDatabase database,
            AttachmentStore store,
            Bitmap bitmap,
            long messageId,
            String sourceKey) {
        if (context == null || database == null || store == null
                || bitmap == null || bitmap.isRecycled() || messageId <= 0) {
            return false;
        }

        File temporary = new File(
                context.getCacheDir(),
                "notification-picture-" + UUID.randomUUID() + ".png");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    return false;
                }
                output.getFD().sync();
            }
            AttachmentStore.StoredAttachment stored =
                    store.store(temporary, messageId, DISPLAY_NAME);
            return database.insertAttachment(new AttachmentEntry(
                    0L,
                    messageId,
                    AttachmentKind.IMAGE,
                    DISPLAY_NAME,
                    "image/png",
                    stored.getRelativePath(),
                    sourceKey == null || sourceKey.trim().isEmpty()
                            ? "notification:picture"
                            : sourceKey,
                    stored.getSizeBytes(),
                    stored.getSha256(),
                    AttachmentStatus.AVAILABLE)) > 0L;
        } catch (IOException | RuntimeException error) {
            return false;
        } finally {
            temporary.delete();
        }
    }
}
