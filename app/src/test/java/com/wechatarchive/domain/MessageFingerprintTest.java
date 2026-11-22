package com.wechatarchive.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class MessageFingerprintTest {
    @Test
    public void identicalMessagesProduceStableSha256Fingerprint() {
        CapturedMessage first = sample("项目群", "李老师", "请交文档", 1000L, "key");
        CapturedMessage second = sample(" 项目群 ", "李老师", " 请交文档 ", 1000L, "key");

        String fingerprint = MessageFingerprint.of(first);

        assertEquals(fingerprint, MessageFingerprint.of(second));
        assertEquals(64, fingerprint.length());
        assertTrue(fingerprint.matches("[0-9a-f]{64}"));
    }

    @Test
    public void conversationBodyAndTimestampAffectFingerprint() {
        CapturedMessage base = sample("项目群", "李老师", "请交文档", 1000L, "key");

        assertNotEquals(
                MessageFingerprint.of(base),
                MessageFingerprint.of(sample("家人群", "李老师", "请交文档", 1000L, "key")));
        assertNotEquals(
                MessageFingerprint.of(base),
                MessageFingerprint.of(sample("项目群", "李老师", "收到", 1000L, "key")));
        assertNotEquals(
                MessageFingerprint.of(base),
                MessageFingerprint.of(sample("项目群", "李老师", "请交文档", 1001L, "key")));
    }

    private CapturedMessage sample(
            String conversation,
            String sender,
            String body,
            long timestamp,
            String notificationKey) {
        return new CapturedMessage(
                conversation,
                sender,
                conversation,
                body,
                timestamp,
                notificationKey,
                false);
    }
}
