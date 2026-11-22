package com.wechatarchive.export;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import com.wechatarchive.data.ArchiveDatabase;
import com.wechatarchive.data.AttachmentEntry;
import com.wechatarchive.importing.AttachmentStore;

import java.io.File;
import java.io.FileNotFoundException;

public final class AttachmentContentProvider extends ContentProvider {
    public static final String AUTHORITY_SUFFIX = ".attachments";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        AttachmentEntry attachment = attachment(uri);
        return attachment == null ? "application/octet-stream" : attachment.getMimeType();
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        AttachmentEntry attachment = attachment(uri);
        if (attachment == null) {
            return null;
        }
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) {
                row.add(attachment.getDisplayName());
            } else if (OpenableColumns.SIZE.equals(column)) {
                row.add(attachment.getSizeBytes());
            } else {
                row.add(null);
            }
        }
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Attachment provider is read-only");
        }
        AttachmentEntry attachment = attachment(uri);
        if (attachment == null || !attachment.isAvailable() || getContext() == null) {
            throw new FileNotFoundException("Attachment is unavailable");
        }
        try {
            File file = new AttachmentStore(getContext())
                    .resolve(attachment.getLocalPath());
            if (!file.isFile()) {
                throw new FileNotFoundException("Attachment file is missing");
            }
            return ParcelFileDescriptor.open(
                    file,
                    ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (java.io.IOException error) {
            throw new FileNotFoundException(error.getMessage());
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    @Override
    public int update(
            Uri uri,
            ContentValues values,
            String selection,
            String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    private AttachmentEntry attachment(Uri uri) {
        if (getContext() == null || uri == null || uri.getPathSegments().size() < 1) {
            return null;
        }
        long id;
        try {
            id = Long.parseLong(uri.getPathSegments().get(0));
        } catch (NumberFormatException error) {
            return null;
        }
        ArchiveDatabase database = new ArchiveDatabase(getContext());
        try {
            return database.attachment(id);
        } finally {
            database.close();
        }
    }
}
