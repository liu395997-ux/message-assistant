package com.wechatarchive.importing;

import android.content.Context;

import com.wechatarchive.data.ArchiveDatabase;
import com.wechatarchive.data.AttachmentEntry;
import com.wechatarchive.domain.importer.ArchivePathPolicy;
import com.wechatarchive.domain.importer.AttachmentStatus;
import com.wechatarchive.domain.importer.MoectArchive;
import com.wechatarchive.domain.importer.MoectArchiveParser;
import com.wechatarchive.domain.importer.MoectImportException;
import com.wechatarchive.domain.importer.MoectMessage;
import com.wechatarchive.domain.importer.MoectResource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

public final class MoectZipImporter {
    private static final int MAX_ENTRIES = 100_000;
    private static final long MAX_TOTAL_JSON_CHARS = 256L * 1024L * 1024L;
    private static final long MAX_TOTAL_ATTACHMENT_BYTES =
            20L * 1024L * 1024L * 1024L;

    private final Context context;
    private final ArchiveDatabase database;
    private final AttachmentStore attachmentStore;
    private final MoectArchiveParser parser = new MoectArchiveParser();

    public MoectZipImporter(
            ArchiveDatabase database,
            AttachmentStore attachmentStore) {
        this.database = database;
        this.attachmentStore = attachmentStore;
        this.context = null;
    }

    public MoectZipImporter(
            Context context,
            ArchiveDatabase database,
            AttachmentStore attachmentStore) {
        this.context = context.getApplicationContext();
        this.database = database;
        this.attachmentStore = attachmentStore;
    }

    public ImportResult importFrom(InputStreamFactory factory)
            throws MoectImportException {
        List<ParsedArchive> archives = readAndParseJson(factory);
        ImportResult result = new ImportResult();
        result.archiveCount = archives.size();
        Map<String, List<PendingAttachment>> pendingByPath = new LinkedHashMap<>();

        for (ParsedArchive parsed : archives) {
            for (MoectMessage message : parsed.archive.getMessages()) {
                result.messageCount++;
                long messageId = database.insertOrGetId(message.toCapturedMessage());
                if (messageId <= 0) {
                    throw new MoectImportException("无法保存导入的消息");
                }
                for (MoectResource resource : message.getResources()) {
                    if (resource.hasSourcePath()) {
                        String archivePath = ArchivePathPolicy.resolveRelative(
                                parsed.jsonPath,
                                resource.getSourcePath());
                        if (archivePath.isEmpty()) {
                            throw new MoectImportException(
                                    "Moect 资源路径无法映射到 ZIP 条目");
                        }
                        pendingByPath
                                .computeIfAbsent(
                                        archivePath,
                                        ignored -> new ArrayList<>())
                                .add(new PendingAttachment(messageId, resource));
                    } else {
                        insertUnavailable(result, messageId, resource);
                    }
                }
            }
        }

        if (!pendingByPath.isEmpty()) {
            extractPendingResources(factory, pendingByPath, result);
        }
        return result;
    }

    private List<ParsedArchive> readAndParseJson(InputStreamFactory factory)
            throws MoectImportException {
        Map<String, String> jsonEntries = new LinkedHashMap<>();
        long totalChars = 0L;
        int entryCount = 0;
        try (ZipInputStream zip = new ZipInputStream(factory.open())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ENTRIES) {
                    throw new MoectImportException("ZIP 条目数量超过安全上限");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                String path = ArchivePathPolicy.normalize(entry.getName());
                if (!ArchivePathPolicy.isSafeRelativePath(path)) {
                    throw new MoectImportException("ZIP 包含不安全的条目路径");
                }
                if (!ArchivePathPolicy.isJsonEntry(path)) {
                    continue;
                }
                String json = readUtf8(
                        zip,
                        MoectArchiveParser.MAX_JSON_CHARACTERS);
                totalChars += json.length();
                if (totalChars > MAX_TOTAL_JSON_CHARS) {
                    throw new MoectImportException("ZIP 内 JSON 总量超过安全上限");
                }
                if (jsonEntries.put(path, json) != null) {
                    throw new MoectImportException("ZIP 包含重复的 JSON 条目");
                }
            }
        } catch (ZipException error) {
            throw new MoectImportException(
                    "无法读取 ZIP；请导出未加密的 Moect ZIP",
                    error);
        } catch (IOException error) {
            throw new MoectImportException("读取 Moect ZIP 失败", error);
        }
        if (jsonEntries.isEmpty()) {
            throw new MoectImportException("ZIP 中没有 Moect JSON 导出文件");
        }

        ZoneId zone = ZoneId.systemDefault();
        List<ParsedArchive> archives = new ArrayList<>();
        for (Map.Entry<String, String> entry : jsonEntries.entrySet()) {
            archives.add(new ParsedArchive(
                    entry.getKey(),
                    parser.parse(entry.getValue(), zone)));
        }
        return archives;
    }

    private void extractPendingResources(
            InputStreamFactory factory,
            Map<String, List<PendingAttachment>> pendingByPath,
            ImportResult result) throws MoectImportException {
        Set<String> extracted = new HashSet<>();
        File temporaryDirectory;
        if (context != null) {
            temporaryDirectory = context.getCacheDir();
        } else {
            temporaryDirectory = new File(System.getProperty("java.io.tmpdir"));
        }

        long totalBytes = 0L;
        try (ZipInputStream zip = new ZipInputStream(factory.open())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String path = ArchivePathPolicy.normalize(entry.getName());
                List<PendingAttachment> pending = pendingByPath.get(path);
                if (pending == null) {
                    continue;
                }
                File temporary = File.createTempFile(
                        "moect-attachment-",
                        ".tmp",
                        temporaryDirectory);
                try {
                    long size = copyEntry(zip, temporary);
                    totalBytes += size;
                    if (totalBytes > MAX_TOTAL_ATTACHMENT_BYTES) {
                        throw new MoectImportException(
                                "ZIP 附件总量超过 20 GB 安全上限");
                    }
                    storePending(temporary, pending, result);
                    extracted.add(path);
                } finally {
                    temporary.delete();
                }
            }
        } catch (ZipException error) {
            throw new MoectImportException(
                    "无法读取 ZIP；请导出未加密的 Moect ZIP",
                    error);
        } catch (IOException error) {
            throw new MoectImportException("解压 Moect 附件失败", error);
        }

        for (Map.Entry<String, List<PendingAttachment>> entry : pendingByPath.entrySet()) {
            if (extracted.contains(entry.getKey())) {
                continue;
            }
            for (PendingAttachment pending : entry.getValue()) {
                insertUnavailable(
                        result,
                        pending.messageId,
                        pending.resource,
                        AttachmentStatus.MISSING);
            }
        }
    }

    private void storePending(
            File source,
            List<PendingAttachment> pending,
            ImportResult result) {
        for (PendingAttachment target : pending) {
            try {
                AttachmentStore.StoredAttachment stored = attachmentStore.store(
                        source,
                        target.messageId,
                        target.resource.getDisplayName());
                AttachmentEntry entry = new AttachmentEntry(
                        0,
                        target.messageId,
                        target.resource.getKind(),
                        target.resource.getDisplayName(),
                        target.resource.getMimeType(),
                        stored.getRelativePath(),
                        target.resource.getSourcePath(),
                        stored.getSizeBytes(),
                        stored.getSha256(),
                        AttachmentStatus.AVAILABLE);
                if (database.insertAttachment(entry) > 0) {
                    result.availableAttachmentCount++;
                } else {
                    result.duplicateAttachmentCount++;
                }
            } catch (IOException error) {
                insertUnavailable(
                        result,
                        target.messageId,
                        target.resource,
                        AttachmentStatus.MISSING);
            }
        }
    }

    private void insertUnavailable(
            ImportResult result,
            long messageId,
            MoectResource resource) {
        insertUnavailable(result, messageId, resource, resource.getStatus());
    }

    private void insertUnavailable(
            ImportResult result,
            long messageId,
            MoectResource resource,
            AttachmentStatus status) {
        AttachmentEntry entry = new AttachmentEntry(
                0,
                messageId,
                resource.getKind(),
                resource.getDisplayName(),
                resource.getMimeType(),
                "",
                resource.getSourcePath(),
                0L,
                "",
                status);
        if (database.insertAttachment(entry) > 0) {
            result.unavailableAttachmentCount++;
        } else {
            result.duplicateAttachmentCount++;
        }
    }

    private String readUtf8(InputStream input, int maximumBytes)
            throws IOException, MoectImportException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maximumBytes) {
                throw new MoectImportException("单个 Moect JSON 超过安全上限");
            }
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private long copyEntry(InputStream input, File destination) throws IOException {
        long total = 0L;
        try (FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > AttachmentStore.MAX_ATTACHMENT_BYTES) {
                    throw new IOException("单个附件超过 2 GB 安全上限");
                }
                output.write(buffer, 0, read);
            }
            output.getFD().sync();
        }
        return total;
    }

    @FunctionalInterface
    public interface InputStreamFactory {
        InputStream open() throws IOException;
    }

    private static final class PendingAttachment {
        private final long messageId;
        private final MoectResource resource;

        private PendingAttachment(long messageId, MoectResource resource) {
            this.messageId = messageId;
            this.resource = resource;
        }
    }

    private static final class ParsedArchive {
        private final String jsonPath;
        private final MoectArchive archive;

        private ParsedArchive(String jsonPath, MoectArchive archive) {
            this.jsonPath = jsonPath;
            this.archive = archive;
        }
    }

    public static final class ImportResult {
        private int archiveCount;
        private int messageCount;
        private int availableAttachmentCount;
        private int unavailableAttachmentCount;
        private int duplicateAttachmentCount;

        public int getArchiveCount() {
            return archiveCount;
        }

        public int getMessageCount() {
            return messageCount;
        }

        public int getAvailableAttachmentCount() {
            return availableAttachmentCount;
        }

        public int getUnavailableAttachmentCount() {
            return unavailableAttachmentCount;
        }

        public int getDuplicateAttachmentCount() {
            return duplicateAttachmentCount;
        }
    }
}
