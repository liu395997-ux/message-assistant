package com.wechatarchive.export;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class ShareFileStore {
    public static final String AUTHORITY_SUFFIX = ".share";
    public static final String SHARE_FILE_NAME = "wechat-notifications.md";

    private ShareFileStore() {
    }

    public static Uri write(Context context, String markdown) throws IOException {
        File directory = shareDirectory(context);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create share directory");
        }
        File temporary = new File(directory, SHARE_FILE_NAME + ".tmp");
        File destination = new File(directory, SHARE_FILE_NAME);
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(markdown.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        if (destination.exists() && !destination.delete()) {
            throw new IOException("Unable to replace previous share file");
        }
        if (!temporary.renameTo(destination)) {
            throw new IOException("Unable to publish share file");
        }
        return new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + AUTHORITY_SUFFIX)
                .appendPath("export")
                .appendPath(SHARE_FILE_NAME)
                .build();
    }

    static File shareDirectory(Context context) {
        return new File(context.getCacheDir(), "share");
    }
}
