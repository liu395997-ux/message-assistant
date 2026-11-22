package com.wechatarchive.domain.importer;

import java.util.Locale;

public final class ArchivePathPolicy {
    private ArchivePathPolicy() {
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().replace('\\', '/');
    }

    public static boolean isSafeRelativePath(String value) {
        String path = normalize(value);
        if (path.isEmpty()
                || path.startsWith("/")
                || path.matches("^[A-Za-z]:/.*")
                || path.contains("\u0000")) {
            return false;
        }
        String[] parts = path.split("/");
        for (String part : parts) {
            if ("..".equals(part)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isJsonEntry(String value) {
        String path = normalize(value).toLowerCase(Locale.ROOT);
        return path.endsWith(".json");
    }

    public static String resolveRelative(String documentPath, String resourcePath) {
        String document = normalize(documentPath);
        String resource = normalize(resourcePath);
        if (!isSafeRelativePath(document) || !isSafeRelativePath(resource)) {
            return "";
        }
        int slash = document.lastIndexOf('/');
        String base = slash < 0 ? "" : document.substring(0, slash);
        String resolved = base.isEmpty() ? resource : base + "/" + resource;
        return isSafeRelativePath(resolved) ? resolved : "";
    }
}
