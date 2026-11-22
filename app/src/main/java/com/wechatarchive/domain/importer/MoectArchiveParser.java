package com.wechatarchive.domain.importer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MoectArchiveParser {
    public static final int MAX_JSON_CHARACTERS = 64 * 1024 * 1024;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public MoectArchive parse(String json, ZoneId fallbackZone)
            throws MoectImportException {
        if (json == null || json.trim().isEmpty()) {
            throw new MoectImportException("Moect JSON 内容为空");
        }
        if (json.length() > MAX_JSON_CHARACTERS) {
            throw new MoectImportException("Moect JSON 超过 64 MB 安全上限");
        }

        try {
            JSONObject root = new JSONObject(json);
            JSONArray messageArray = root.optJSONArray("messages");
            if (messageArray == null) {
                throw new MoectImportException("Moect JSON 缺少 messages 数组");
            }
            JSONObject metadata = root.optJSONObject("metadata");
            String sessionId = metadata == null
                    ? ""
                    : first(metadata.optString("session"), metadata.optString("username"));
            String name = metadata == null
                    ? "导入的聊天"
                    : first(
                            metadata.optString("name"),
                            metadata.optString("display"),
                            sessionId,
                            "导入的聊天");
            ZoneId zone = metadata == null
                    ? fallbackZone
                    : zone(metadata.optString("time_zone"), fallbackZone);
            List<MoectMessage> messages = new ArrayList<>();
            for (int index = 0; index < messageArray.length(); index++) {
                JSONObject value = messageArray.optJSONObject(index);
                if (value != null) {
                    messages.add(parseMessage(value, name, sessionId, zone));
                }
            }
            return new MoectArchive(name, sessionId, messages);
        } catch (MoectImportException error) {
            throw error;
        } catch (JSONException error) {
            throw new MoectImportException("无法解析 Moect JSON", error);
        }
    }

    private MoectMessage parseMessage(
            JSONObject value,
            String conversation,
            String sessionId,
            ZoneId zone) throws MoectImportException {
        String sender = value.optString("sender").trim();
        String messageKind = value.optString("kind").trim();
        String text = value.optString("text").trim();
        List<MoectResource> resources = parseResources(value);
        String body = text.isEmpty() ? placeholder(messageKind, value.optString("type_name"), resources) : text;
        long time = timestamp(value, zone);
        String identity = first(
                value.optString("server_id"),
                value.optString("local_id"),
                time + ":" + sender);
        String stableKey = "moect:"
                + (sessionId.isEmpty() ? conversation : sessionId)
                + ":"
                + identity;
        return new MoectMessage(
                conversation,
                sender.isEmpty() ? conversation : sender,
                body,
                time,
                stableKey,
                resources);
    }

    private List<MoectResource> parseResources(JSONObject value)
            throws MoectImportException {
        List<MoectResource> resources = new ArrayList<>();
        collectResources(value, resources, new HashSet<>(), 0);
        return resources;
    }

    private void collectResources(
            JSONObject container,
            List<MoectResource> resources,
            Set<String> seen,
            int depth) throws MoectImportException {
        if (container == null || depth > 8) {
            return;
        }
        JSONArray values = container.optJSONArray("resources");
        if (values == null || values.length() == 0) {
            // Quotes and forwarded records may still contain nested resources.
        } else {
            String messageKind = container.optString("kind");
            for (int index = 0; index < values.length(); index++) {
                JSONObject value = values.optJSONObject(index);
                if (value == null) {
                    continue;
                }
                String sourcePath = normalizeSourcePath(value.optString("path"));
                String resourceKind = value.optString("kind");
                String displayName = AttachmentClassifier.safeDisplayName(
                        value.optString("name"),
                        fileName(sourcePath));
                AttachmentStatus status =
                        AttachmentClassifier.status(value.optString("status"));
                AttachmentKind kind = AttachmentClassifier.classify(
                        messageKind,
                        resourceKind,
                        displayName);
                String key = sourcePath + "\u0000" + displayName + "\u0000" + kind;
                if (sourcePath.isEmpty() || seen.add(key)) {
                    resources.add(new MoectResource(
                            sourcePath,
                            displayName,
                            kind,
                            status));
                }
            }
        }

        JSONObject quote = container.optJSONObject("quote");
        if (quote != null) {
            collectResources(quote, resources, seen, depth + 1);
        }
        JSONObject forward = container.optJSONObject("forward");
        JSONArray forwardedMessages = forward == null
                ? null
                : forward.optJSONArray("messages");
        if (forwardedMessages != null) {
            for (int index = 0; index < forwardedMessages.length(); index++) {
                collectResources(
                        forwardedMessages.optJSONObject(index),
                        resources,
                        seen,
                        depth + 1);
            }
        }
    }

    private String normalizeSourcePath(String raw) throws MoectImportException {
        if (raw == null || raw.trim().isEmpty()) {
            return "";
        }
        String path = raw.trim().replace('\\', '/');
        if (path.startsWith("/")
                || path.matches("^[A-Za-z]:/.*")
                || path.contains("\u0000")) {
            throw new MoectImportException("Moect 资源路径必须是相对 ZIP 路径");
        }
        String[] parts = path.split("/");
        for (String part : parts) {
            if ("..".equals(part)) {
                throw new MoectImportException("Moect 资源路径不能包含 ..");
            }
        }
        return path;
    }

    private long timestamp(JSONObject value, ZoneId zone) throws MoectImportException {
        long raw = value.optLong("create_time", 0L);
        if (raw > 10_000_000_000L) {
            return raw;
        }
        if (raw > 0L) {
            return raw * 1000L;
        }
        String formatted = value.optString("time_str").trim();
        if (!formatted.isEmpty()) {
            try {
                return LocalDateTime.parse(formatted, TIME_FORMAT)
                        .atZone(zone)
                        .toInstant()
                        .toEpochMilli();
            } catch (DateTimeParseException error) {
                throw new MoectImportException("无法解析 Moect 消息时间", error);
            }
        }
        throw new MoectImportException("Moect 消息缺少 create_time 和 time_str");
    }

    private String placeholder(
            String messageKind,
            String typeName,
            List<MoectResource> resources) {
        switch (messageKind.toLowerCase(java.util.Locale.ROOT)) {
            case "image":
                return "[图片]";
            case "video":
                return "[视频]";
            case "voice":
                return "[语音]";
            case "emoji":
                return "[动画表情]";
            case "file":
                return resources.isEmpty()
                        ? "[文件]"
                        : "[文件] " + resources.get(0).getDisplayName();
            default:
                String label = typeName == null ? "" : typeName.trim();
                return label.isEmpty() ? "[附件]" : "[" + label + "]";
        }
    }

    private ZoneId zone(String raw, ZoneId fallback) {
        try {
            return raw == null || raw.trim().isEmpty()
                    ? fallback
                    : ZoneId.of(raw.trim());
        } catch (DateTimeException error) {
            return fallback;
        }
    }

    private String fileName(String path) {
        int slash = path.lastIndexOf('/');
        String value = slash < 0 ? path : path.substring(slash + 1);
        return value.trim().isEmpty() ? "attachment.bin" : value;
    }

    private String first(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }
}
