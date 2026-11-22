package com.wechatarchive.importing;

import android.content.Context;

import com.wechatarchive.domain.importer.AttachmentClassifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;

public final class AttachmentStore {
    public static final long MAX_ATTACHMENT_BYTES = 2L * 1024L * 1024L * 1024L;
    public static final String ROOT_NAME = "attachments";

    private final File filesDirectory;
    private final File rootDirectory;

    public AttachmentStore(Context context) {
        filesDirectory = context.getApplicationContext().getFilesDir();
        rootDirectory = new File(filesDirectory, ROOT_NAME);
    }

    public StoredAttachment store(
            File source,
            long messageId,
            String displayName) throws IOException {
        if (messageId <= 0 || source == null || !source.isFile()) {
            throw new IOException("附件源文件无效");
        }
        File messageDirectory = new File(rootDirectory, String.valueOf(messageId));
        if (!messageDirectory.exists() && !messageDirectory.mkdirs()) {
            throw new IOException("无法创建附件目录");
        }

        String safeName = AttachmentClassifier.safeDisplayName(
                displayName,
                "attachment.bin");
        File temporary = new File(
                messageDirectory,
                ".incoming-" + UUID.randomUUID());
        MessageDigest digest = sha256();
        long size = 0L;
        try (InputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                size += read;
                if (size > MAX_ATTACHMENT_BYTES) {
                    throw new IOException("单个附件超过 2 GB 安全上限");
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
            output.getFD().sync();
        } catch (IOException error) {
            temporary.delete();
            throw error;
        }

        String hash = toHex(digest.digest());
        String fileName = hash.substring(0, 32) + "-" + safeName;
        File destination = new File(messageDirectory, fileName);
        if (destination.isFile() && destination.length() == size) {
            temporary.delete();
        } else {
            if (destination.exists() && !destination.delete()) {
                temporary.delete();
                throw new IOException("无法替换旧附件");
            }
            if (!temporary.renameTo(destination)) {
                temporary.delete();
                throw new IOException("无法保存附件");
            }
        }
        String relative = ROOT_NAME + "/" + messageId + "/" + fileName;
        return new StoredAttachment(relative, hash, size);
    }

    public File resolve(String relativePath) throws IOException {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new IOException("附件路径为空");
        }
        File candidate = new File(filesDirectory, relativePath).getCanonicalFile();
        File canonicalRoot = rootDirectory.getCanonicalFile();
        String rootPath = canonicalRoot.getPath() + File.separator;
        if (!candidate.getPath().startsWith(rootPath)) {
            throw new IOException("附件路径越界");
        }
        return candidate;
    }

    public void deletePaths(List<String> relativePaths) {
        if (relativePaths == null) {
            return;
        }
        for (String relativePath : relativePaths) {
            try {
                File file = resolve(relativePath);
                deleteTree(file);
                File parent = file.getParentFile();
                if (parent != null) {
                    deleteIfEmpty(parent);
                }
            } catch (IOException ignored) {
                // A stale or invalid path must not prevent database deletion.
            }
        }
    }

    public void clear() {
        deleteTree(rootDirectory);
    }

    File rootDirectory() {
        return rootDirectory;
    }

    private void deleteTree(File file) {
        if (!file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        file.delete();
    }

    private void deleteIfEmpty(File directory) {
        File[] children = directory.listFiles();
        if (children != null && children.length == 0) {
            directory.delete();
        }
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

    public static final class StoredAttachment {
        private final String relativePath;
        private final String sha256;
        private final long sizeBytes;

        private StoredAttachment(String relativePath, String sha256, long sizeBytes) {
            this.relativePath = relativePath;
            this.sha256 = sha256;
            this.sizeBytes = sizeBytes;
        }

        public String getRelativePath() {
            return relativePath;
        }

        public String getSha256() {
            return sha256;
        }

        public long getSizeBytes() {
            return sizeBytes;
        }
    }
}
