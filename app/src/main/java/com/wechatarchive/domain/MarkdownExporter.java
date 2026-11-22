package com.wechatarchive.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MarkdownExporter {
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private MarkdownExporter() {
    }

    public static String render(
            List<CapturedMessage> messages,
            Instant exportedAt,
            ZoneId zoneId) {
        return render(
                messages,
                Collections.emptyMap(),
                exportedAt,
                zoneId);
    }

    public static String render(
            List<CapturedMessage> messages,
            Map<String, List<AttachmentDescriptor>> attachments,
            Instant exportedAt,
            ZoneId zoneId) {
        List<CapturedMessage> safeMessages = messages == null
                ? new ArrayList<>()
                : new ArrayList<>(messages);
        safeMessages.sort(Comparator
                .comparingLong(CapturedMessage::getOccurredAtMillis)
                .thenComparing(CapturedMessage::getConversation)
                .thenComparing(CapturedMessage::getBody));

        StringBuilder markdown = new StringBuilder(4096);
        markdown.append("# 微信通知归档\n\n");
        markdown.append("> 导出时间：")
                .append(TIME_FORMAT.format(exportedAt.atZone(zoneId)))
                .append("\n");
        markdown.append("> 消息数量：")
                .append(safeMessages.size())
                .append("\n");
        markdown.append("> 数据来源：本人安卓设备上的微信通知\n\n");

        if (safeMessages.isEmpty()) {
            markdown.append("暂无已归档的微信通知。\n");
            return markdown.toString();
        }

        Map<String, List<CapturedMessage>> grouped = new LinkedHashMap<>();
        for (CapturedMessage message : safeMessages) {
            grouped.computeIfAbsent(message.getConversation(), ignored -> new ArrayList<>())
                    .add(message);
        }

        for (Map.Entry<String, List<CapturedMessage>> entry : grouped.entrySet()) {
            markdown.append("## 会话：")
                    .append(escapeInline(entry.getKey()))
                    .append("\n\n");
            for (CapturedMessage message : entry.getValue()) {
                markdown.append("### ")
                        .append(TIME_FORMAT.format(
                                Instant.ofEpochMilli(message.getOccurredAtMillis())
                                        .atZone(zoneId)))
                        .append("\n\n");
                markdown.append("**发送者：** ")
                        .append(escapeInline(message.getSender()))
                        .append("\n\n");
                markdown.append("**标题：** ")
                        .append(escapeInline(message.getTitle()))
                        .append("\n\n");
                if (message.isSummary()) {
                    markdown.append("**通知类型：** 汇总通知\n\n");
                }
                markdown.append("**内容：**\n\n");
                markdown.append(codeBlock(message.getBody()));
                appendAttachments(
                        markdown,
                        attachments.get(MessageFingerprint.of(message)));
                markdown.append("\n---\n\n");
            }
        }
        return markdown.toString();
    }

    private static void appendAttachments(
            StringBuilder markdown,
            List<AttachmentDescriptor> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        markdown.append("\n**附件：**\n\n");
        for (AttachmentDescriptor value : values) {
            markdown.append("- ")
                    .append(escapeInline(value.getLabel()))
                    .append("：")
                    .append(escapeInline(value.getDisplayName()))
                    .append(value.isStored() ? "（已存储）" : "（未取得）")
                    .append("\n");
        }
    }

    private static String codeBlock(String body) {
        int longestRun = 0;
        int currentRun = 0;
        for (int index = 0; index < body.length(); index++) {
            if (body.charAt(index) == '`') {
                currentRun++;
                longestRun = Math.max(longestRun, currentRun);
            } else {
                currentRun = 0;
            }
        }
        String fence = "`".repeat(Math.max(3, longestRun + 1));
        return fence + "text\n" + body + "\n" + fence + "\n";
    }

    private static String escapeInline(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if ("\\`*_{}[]<>#|+-.!".indexOf(current) >= 0) {
                escaped.append('\\');
            }
            escaped.append(current);
        }
        return escaped.toString();
    }
}
