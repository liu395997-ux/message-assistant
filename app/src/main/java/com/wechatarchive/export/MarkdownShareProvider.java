package com.wechatarchive.export;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public final class MarkdownShareProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return "text/markdown";
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        if (getContext() == null) {
            return null;
        }
        File file = expectedFile();
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) {
                row.add(ShareFileStore.SHARE_FILE_NAME);
            } else if (OpenableColumns.SIZE.equals(column)) {
                row.add(file.exists() ? file.length() : 0L);
            } else {
                row.add(null);
            }
        }
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Read-only provider");
        }
        if (!isExpectedUri(uri)) {
            throw new FileNotFoundException("Unknown share resource");
        }
        File file = expectedFile();
        if (!file.exists()) {
            throw new FileNotFoundException("Markdown export is not ready");
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
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

    private boolean isExpectedUri(Uri uri) {
        return getContext() != null
                && ("export".equals(uri.getPathSegments().isEmpty()
                        ? null
                        : uri.getPathSegments().get(0)))
                && uri.getPathSegments().size() == 2
                && ShareFileStore.SHARE_FILE_NAME.equals(uri.getPathSegments().get(1));
    }

    private File expectedFile() {
        return new File(ShareFileStore.shareDirectory(getContext()), ShareFileStore.SHARE_FILE_NAME);
    }
}
