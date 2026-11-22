package com.wechatarchive.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public final class NotificationParserTest {
    @Test
    public void parsesPlainPrivateChatNotification() {
        RawNotification raw = new RawNotification(
                "com.tencent.mm",
                "张同学",
                "今晚七点开会",
                "",
                "",
                "",
                Collections.emptyList(),
                Collections.emptyList(),
                1_700_000_000_000L,
                "wechat:key-1");

        List<CapturedMessage> messages = new NotificationParser().parse(raw);

        assertEquals(1, messages.size());
        CapturedMessage message = messages.get(0);
        assertEquals("张同学", message.getConversation());
        assertEquals("张同学", message.getSender());
        assertEquals("张同学", message.getTitle());
        assertEquals("今晚七点开会", message.getBody());
        assertEquals(1_700_000_000_000L, message.getOccurredAtMillis());
        assertFalse(message.isSummary());
    }

    @Test
    public void prefersBigTextWhenTextIsCollapsed() {
        RawNotification raw = new RawNotification(
                "com.tencent.mm",
                "项目群",
                "2条新消息",
                "李老师：请提交文档\n王同学：收到",
                "李老师",
                "项目群",
                Collections.emptyList(),
                Collections.emptyList(),
                10L,
                "wechat:key-2");

        CapturedMessage message = new NotificationParser().parse(raw).get(0);

        assertEquals("项目群", message.getConversation());
        assertEquals("李老师", message.getSender());
        assertEquals("请提交文档\n王同学：收到", message.getBody());
    }

    @Test
    public void extractsEachMessagingStyleLine() {
        RawNotification raw = new RawNotification(
                "com.tencent.mm",
                "项目群",
                "",
                "",
                "",
                "项目群",
                Collections.emptyList(),
                Arrays.asList(
                        new RawMessage("李老师", "请提交文档", 101L),
                        new RawMessage("王同学", "收到", 102L)),
                100L,
                "wechat:key-3");

        List<CapturedMessage> messages = new NotificationParser().parse(raw);

        assertEquals(2, messages.size());
        assertEquals("李老师", messages.get(0).getSender());
        assertEquals("请提交文档", messages.get(0).getBody());
        assertEquals(101L, messages.get(0).getOccurredAtMillis());
        assertEquals("王同学", messages.get(1).getSender());
        assertEquals("收到", messages.get(1).getBody());
    }

    @Test
    public void joinsTextLinesWhenMessagingStyleIsUnavailable() {
        RawNotification raw = new RawNotification(
                "com.tencent.mm",
                "家人群",
                "",
                "",
                "妈妈",
                "家人群",
                Arrays.asList("妈妈：回家吃饭", "爸爸：知道了"),
                Collections.emptyList(),
                20L,
                "wechat:key-4");

        CapturedMessage message = new NotificationParser().parse(raw).get(0);

        assertEquals("妈妈：回家吃饭\n爸爸：知道了", message.getBody());
        assertFalse(message.isSummary());
    }

    @Test
    public void marksCollapsedNotificationAsSummary() {
        RawNotification raw = new RawNotification(
                "com.tencent.mm",
                "项目群",
                "3条新消息",
                "",
                "",
                "项目群",
                Collections.emptyList(),
                Collections.emptyList(),
                30L,
                "wechat:key-5");

        CapturedMessage message = new NotificationParser().parse(raw).get(0);

        assertTrue(message.isSummary());
        assertEquals("3条新消息", message.getBody());
    }

    @Test
    public void rejectsWhitespaceOnlyNotification() {
        RawNotification raw = new RawNotification(
                "com.tencent.mm",
                "  ",
                " ",
                "",
                "",
                "",
                Collections.emptyList(),
                Collections.emptyList(),
                40L,
                "wechat:key-6");

        assertTrue(new NotificationParser().parse(raw).isEmpty());
    }

    @Test
    public void usesFallbackNamesForMissingConversation() {
        RawNotification raw = new RawNotification(
                "com.tencent.mm",
                "",
                "收到一条消息",
                "",
                "",
                "",
                Collections.emptyList(),
                Collections.emptyList(),
                50L,
                "wechat:key-7");

        CapturedMessage message = new NotificationParser().parse(raw).get(0);

        assertEquals("微信通知", message.getConversation());
        assertEquals("微信通知", message.getTitle());
    }
}
