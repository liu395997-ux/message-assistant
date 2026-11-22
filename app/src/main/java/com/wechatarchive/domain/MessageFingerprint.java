package com.wechatarchive.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class MessageFingerprint {
    private MessageFingerprint() {
    }

    public static String of(CapturedMessage message) {
        String canonical = normalize(message.getConversation())
                + '\u0000'
                + normalize(message.getSender())
                + '\u0000'
                + normalize(message.getBody())
                + '\u0000'
                + message.getOccurredAtMillis()
                + '\u0000'
                + normalize(message.getNotificationKey());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }
}
