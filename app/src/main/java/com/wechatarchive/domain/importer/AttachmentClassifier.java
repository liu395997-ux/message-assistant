package com.wechatarchive.domain.importer;

import java.util.Locale;

public final class AttachmentClassifier {
    private AttachmentClassifier() {
    }

    public static AttachmentKind classify(
            String messageKind,
            String resourceKind,
            String nameOrPath) {
        String message = lower(messageKind);
        String resource = lower(resourceKind);
        String extension = extension(lower(nameOrPath));

        if ("emoji".equals(message)) {
            return AttachmentKind.ANIMATED_STICKER;
        }
        if ("gif".equals(extension) || "webp".equals(extension)) {
            return AttachmentKind.ANIMATED_STICKER;
        }
        if ("video".equals(message)
                || "video".equals(resource)
                || isVideo(extension)) {
            return AttachmentKind.VIDEO;
        }
        if ("voice".equals(message)
                || "audio".equals(resource)
                || isAudio(extension)) {
            return AttachmentKind.AUDIO;
        }
        if ("file".equals(message)
                || "file".equals(resource)
                || isDocument(extension)) {
            return AttachmentKind.DOCUMENT;
        }
        if ("image".equals(message)
                || "image".equals(resource)
                || isImage(extension)) {
            return AttachmentKind.IMAGE;
        }
        return AttachmentKind.OTHER;
    }

    public static AttachmentStatus status(String value) {
        String normalized = lower(value);
        switch (normalized) {
            case "available":
                return AttachmentStatus.AVAILABLE;
            case "missing":
                return AttachmentStatus.MISSING;
            case "encrypted":
                return AttachmentStatus.ENCRYPTED;
            case "corrupt":
                return AttachmentStatus.CORRUPT;
            case "skipped":
                return AttachmentStatus.SKIPPED;
            default:
                return AttachmentStatus.UNSUPPORTED;
        }
    }

    public static String mimeTypeForName(String name) {
        String extension = extension(lower(name));
        switch (extension) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "webp":
                return "image/webp";
            case "bmp":
                return "image/bmp";
            case "mp4":
            case "m4v":
                return "video/mp4";
            case "mov":
                return "video/quicktime";
            case "webm":
                return "video/webm";
            case "mkv":
                return "video/x-matroska";
            case "mp3":
                return "audio/mpeg";
            case "m4a":
                return "audio/mp4";
            case "wav":
                return "audio/wav";
            case "flac":
                return "audio/flac";
            case "pdf":
                return "application/pdf";
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls":
                return "application/vnd.ms-excel";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt":
                return "application/vnd.ms-powerpoint";
            case "pptx":
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "txt":
                return "text/plain";
            case "csv":
                return "text/csv";
            case "json":
                return "application/json";
            case "zip":
                return "application/zip";
            default:
                return "application/octet-stream";
        }
    }

    public static String safeDisplayName(String candidate, String fallback) {
        String value = candidate == null ? "" : candidate.trim().replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        value = value.replaceAll("[\\x00-\\x1f\\\\/:*?\"<>|]", "_")
                .replaceAll("[. ]+$", "")
                .trim();
        if (value.isEmpty() || value.matches("[._ ]+")) {
            value = fallback == null ? "attachment.bin" : fallback.trim();
        }
        if (value.isEmpty()) {
            value = "attachment.bin";
        }
        return value.length() > 160 ? value.substring(0, 160) : value;
    }

    private static boolean isVideo(String extension) {
        return "mp4".equals(extension)
                || "m4v".equals(extension)
                || "mov".equals(extension)
                || "avi".equals(extension)
                || "mkv".equals(extension)
                || "webm".equals(extension);
    }

    private static boolean isAudio(String extension) {
        return "mp3".equals(extension)
                || "m4a".equals(extension)
                || "wav".equals(extension)
                || "flac".equals(extension)
                || "silk".equals(extension);
    }

    private static boolean isDocument(String extension) {
        return "pdf".equals(extension)
                || "doc".equals(extension)
                || "docx".equals(extension)
                || "xls".equals(extension)
                || "xlsx".equals(extension)
                || "ppt".equals(extension)
                || "pptx".equals(extension)
                || "txt".equals(extension)
                || "csv".equals(extension)
                || "json".equals(extension)
                || "zip".equals(extension);
    }

    private static boolean isImage(String extension) {
        return "jpg".equals(extension)
                || "jpeg".equals(extension)
                || "png".equals(extension)
                || "bmp".equals(extension);
    }

    private static String extension(String value) {
        int dot = value.lastIndexOf('.');
        return dot < 0 || dot == value.length() - 1
                ? ""
                : value.substring(dot + 1);
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
