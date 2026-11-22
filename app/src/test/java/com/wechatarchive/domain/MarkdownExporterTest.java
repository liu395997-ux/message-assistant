package com.wechatarchive.domain;

import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public final class MarkdownExporterTest {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    public void rendersMetadataAndMessagesInChronologicalOrder() {
        CapturedMessage later = message("项目群", "王同学", "收到", 2_000L, false);
        CapturedMessage earlier = message("项目群", "李老师", "请提交文档", 1_000L, false);

        String markdown = MarkdownExporter.render(
                Arrays.asList(later, earlier),
                Instant.ofEpochMilli(3_000L),
                SHANGHAI);

        assertTrue(markdown.startsWith("# 微信通知归档"));
        assertTrue(markdown.contains("> 消息数量：2"));
        assertTrue(markdown.contains("## 会话：项目群"));
        assertTrue(markdown.indexOf("请提交文档") < markdown.indexOf("收到"));
        assertTrue(markdown.contains("**发送者：** 李老师"));
    }

    @Test
    public void groupsRecordsByConversation() {
        String markdown = MarkdownExporter.render(
                Arrays.asList(
                        message("项目群", "李老师", "文档在哪里", 1_000L, false),
                        message("家人群", "妈妈", "回家吃饭", 2_000L, false),
                        message("项目群", "王同学", "群里发了", 3_000L, false)),
                Instant.ofEpochMilli(4_000L),
                SHANGHAI);

        assertTrue(markdown.contains("## 会话：项目群"));
        assertTrue(markdown.contains("## 会话：家人群"));
    }

    @Test
    public void safelyRepresentsMarkdownControlCharactersAndBackticks() {
        CapturedMessage tricky = message(
                "# 项目|群",
                "王*同学",
                "第一行\n```\n# 不是标题\n第二行",
                1_000L,
                false);

        String markdown = MarkdownExporter.render(
                Collections.singletonList(tricky),
                Instant.ofEpochMilli(2_000L),
                SHANGHAI);

        assertTrue(markdown.contains("## 会话：\\# 项目\\|群"));
        assertTrue(markdown.contains("**发送者：** 王\\*同学"));
        assertTrue(markdown.contains("````text"));
        assertTrue(markdown.contains("# 不是标题"));
    }

    @Test
    public void rendersSummaryLabelAndEmptyArchiveState() {
        CapturedMessage summary = message("项目群", "项目群", "3条新消息", 1_000L, true);

        String markdown = MarkdownExporter.render(
                Collections.singletonList(summary),
                Instant.ofEpochMilli(2_000L),
                SHANGHAI);

        assertTrue(markdown.contains("**通知类型：** 汇总通知"));
        assertTrue(MarkdownExporter.render(
                Collections.emptyList(),
                Instant.ofEpochMilli(2_000L),
                SHANGHAI).contains("暂无已归档的微信通知"));
    }

    @Test
    public void listsAttachmentsWithoutLocalPrivatePaths() {
        CapturedMessage message = message(
                "好友",
                "好友",
                "照片和文档",
                1_000L,
                false);
        Map<String, java.util.List<AttachmentDescriptor>> attachments =
                new LinkedHashMap<>();
        attachments.put(
                MessageFingerprint.of(message),
                Arrays.asList(
                        new AttachmentDescriptor(
                                "photo.jpg",
                                "图片",
                                true),
                        new AttachmentDescriptor(
                                "report.pdf",
                                "文档",
                                false)));

        String markdown = MarkdownExporter.render(
                Collections.singletonList(message),
                attachments,
                Instant.ofEpochMilli(2_000L),
                SHANGHAI);

        assertTrue(markdown.contains("**附件：**"));
        assertTrue(markdown.contains("图片：photo\\.jpg（已存储）"));
        assertTrue(markdown.contains("文档：report\\.pdf（未取得）"));
        assertTrue(!markdown.contains("attachments/"));
    }

    private CapturedMessage message(
            String conversation,
            String sender,
            String body,
            long timestamp,
            boolean summary) {
        return new CapturedMessage(
                conversation,
                sender,
                conversation,
                body,
                timestamp,
                "key-" + timestamp,
                summary);
    }
}
