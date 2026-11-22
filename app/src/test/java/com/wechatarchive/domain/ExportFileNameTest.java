package com.wechatarchive.domain;

import static org.junit.Assert.assertEquals;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.Test;

public final class ExportFileNameTest {
    @Test
    public void usesTimestampAndMarkdownExtension() {
        assertEquals(
                "wechat-notifications-20261122-221503.md",
                ExportFileName.create(
                        Instant.parse("2026-11-22T14:15:03Z"),
                        ZoneId.of("Asia/Shanghai")));
    }
}
