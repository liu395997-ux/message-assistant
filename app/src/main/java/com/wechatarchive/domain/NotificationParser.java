package com.wechatarchive.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class NotificationParser {
    private static final String FALLBACK_CONVERSATION = "微信通知";
    private static final Pattern SUMMARY_PATTERN =
            Pattern.compile("^\\s*\\d+\\s*条新消息\\s*$");

    public List<CapturedMessage> parse(RawNotification raw) {
        if (raw == null) {
            return Collections.emptyList();
        }

        String conversation = firstNonBlank(
                raw.getConversationTitle(),
                raw.getTitle(),
                FALLBACK_CONVERSATION);
        List<CapturedMessage> parsed = parseMessagingStyle(raw, conversation);
        if (!parsed.isEmpty()) {
            return parsed;
        }

        String sender = firstNonBlank(raw.getSubText(), raw.getTitle(), conversation);
        String body = chooseBody(raw, sender);
        if (body.isEmpty()) {
            return Collections.emptyList();
        }

        return Collections.singletonList(new CapturedMessage(
                conversation,
                sender,
                firstNonBlank(raw.getSubText(), raw.getTitle(), conversation),
                body,
                timestampOrNow(raw.getPostedAtMillis()),
                raw.getNotificationKey(),
                isSummary(body)));
    }

    private List<CapturedMessage> parseMessagingStyle(
            RawNotification raw,
            String conversation) {
        if (raw.getMessages().isEmpty()) {
            return Collections.emptyList();
        }

        List<CapturedMessage> parsed = new ArrayList<>();
        for (RawMessage message : raw.getMessages()) {
            String body = normalizeText(message.getText());
            if (body.isEmpty()) {
                continue;
            }
            String sender = firstNonBlank(message.getSender(), raw.getSubText(), conversation);
            parsed.add(new CapturedMessage(
                    conversation,
                    sender,
                    firstNonBlank(sender, raw.getTitle(), conversation),
                    body,
                    timestampOrNow(message.getTimestampMillis() > 0
                            ? message.getTimestampMillis()
                            : raw.getPostedAtMillis()),
                    raw.getNotificationKey(),
                    isSummary(body)));
        }
        return parsed;
    }

    private String chooseBody(RawNotification raw, String sender) {
        String bigText = normalizeBody(raw.getBigText(), sender);
        if (!bigText.isEmpty()) {
            return bigText;
        }

        String text = normalizeBody(raw.getText(), sender);
        if (!text.isEmpty()) {
            return text;
        }

        List<String> cleanLines = new ArrayList<>();
        for (String line : raw.getTextLines()) {
            String clean = normalizeText(line);
            if (!clean.isEmpty()) {
                cleanLines.add(clean);
            }
        }
        return String.join("\n", cleanLines);
    }

    private String normalizeBody(String body, String sender) {
        String normalized = normalizeText(body);
        if (normalized.isEmpty() || sender.isEmpty()) {
            return normalized;
        }

        String prefix = sender.trim() + "：";
        if (normalized.startsWith(prefix)) {
            return normalized.substring(prefix.length()).trim();
        }
        String asciiPrefix = sender.trim() + ":";
        if (normalized.startsWith(asciiPrefix)) {
            return normalized.substring(asciiPrefix.length()).trim();
        }
        return normalized;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value
                .replace('\u00A0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
        String[] lines = normalized.split("\n", -1);
        StringBuilder result = new StringBuilder(normalized.length());
        for (String line : lines) {
            String trimmed = line.trim();
            if (result.length() > 0) {
                result.append('\n');
            }
            result.append(trimmed);
        }
        return result.toString().trim();
    }

    private boolean isSummary(String body) {
        return SUMMARY_PATTERN.matcher(body).matches();
    }

    private long timestampOrNow(long timestamp) {
        return timestamp > 0 ? timestamp : System.currentTimeMillis();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }
}
