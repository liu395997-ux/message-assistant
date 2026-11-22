package com.wechatarchive.domain.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.wechatarchive.domain.importer.AttachmentKind;
import com.wechatarchive.domain.importer.AttachmentStatus;

import java.time.ZoneId;
import java.util.List;

import org.junit.Test;

public final class MoectArchiveParserTest {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    public void parsesPersonalChatTextMessages() throws Exception {
        MoectArchive archive = parse("""
                {
                  "metadata": {"name": "妈妈", "session": "wxid_mom"},
                  "messages": [
                    {
                      "server_id": "100",
                      "sender": "妈妈",
                      "create_time": 1700000000,
                      "text": "晚上回家吃饭吗？",
                      "kind": "text",
                      "resources": []
                    }
                  ]
                }
                """);

        assertEquals("妈妈", archive.getName());
        assertEquals("wxid_mom", archive.getSessionId());
        assertEquals(1, archive.getMessages().size());
        MoectMessage message = archive.getMessages().get(0);
        assertEquals("妈妈", message.getConversation());
        assertEquals("妈妈", message.getSender());
        assertEquals("晚上回家吃饭吗？", message.getBody());
        assertEquals(1_700_000_000_000L, message.getOccurredAtMillis());
        assertEquals("moect:wxid_mom:100", message.getStableKey());
        assertTrue(message.getResources().isEmpty());
    }

    @Test
    public void classifiesMixedMultimodalResources() throws Exception {
        MoectArchive archive = parse(multimodalJson("available"));

        assertEquals(6, archive.getMessages().size());
        assertEquals(
                AttachmentKind.IMAGE,
                archive.getMessages().get(0).getResources().get(0).getKind());
        assertEquals(
                "[图片]",
                archive.getMessages().get(0).getBody());
        assertEquals(
                AttachmentKind.VIDEO,
                archive.getMessages().get(1).getResources().get(0).getKind());
        assertEquals(
                AttachmentKind.DOCUMENT,
                archive.getMessages().get(2).getResources().get(0).getKind());
        assertEquals(
                "[文件] 课程设计.pdf",
                archive.getMessages().get(2).getBody());
        assertEquals(
                AttachmentKind.AUDIO,
                archive.getMessages().get(3).getResources().get(0).getKind());
        assertEquals(
                AttachmentKind.ANIMATED_STICKER,
                archive.getMessages().get(4).getResources().get(0).getKind());
        assertEquals(
                "[动画表情]",
                archive.getMessages().get(4).getBody());
        assertEquals(
                AttachmentKind.OTHER,
                archive.getMessages().get(5).getResources().get(0).getKind());
    }

    @Test
    public void preservesUnavailableResourceState() throws Exception {
        MoectArchive archive = parse(multimodalJson("missing"));

        MoectResource resource = archive
                .getMessages()
                .get(0)
                .getResources()
                .get(0);

        assertEquals(AttachmentStatus.MISSING, resource.getStatus());
        assertFalse(resource.hasSourcePath());
    }

    @Test
    public void collectsQuoteAndForwardResources() throws Exception {
        MoectArchive archive = parse("""
                {
                  "metadata": {"name": "好友", "session": "wxid_friend"},
                  "messages": [{
                    "server_id": "nested",
                    "sender": "好友",
                    "create_time": 1700000000,
                    "kind": "quote",
                    "text": "回复引用消息",
                    "quote": {
                      "sender": "我",
                      "kind": "image",
                      "resources": [{
                        "kind": "image",
                        "path": "chat_assets/resources/image/quoted.jpg",
                        "name": "quoted.jpg",
                        "status": "available"
                      }]
                    },
                    "forward": {
                      "messages": [{
                        "sender": "老师",
                        "kind": "file",
                        "resources": [{
                          "kind": "file",
                          "path": "chat_assets/resources/file/forwarded.pdf",
                          "name": "forwarded.pdf",
                          "status": "available"
                        }]
                      }]
                    }
                  }]
                }
                """);

        List<MoectResource> resources =
                archive.getMessages().get(0).getResources();
        assertEquals(2, resources.size());
        assertEquals(AttachmentKind.IMAGE, resources.get(0).getKind());
        assertEquals(AttachmentKind.DOCUMENT, resources.get(1).getKind());
        assertEquals("quoted.jpg", resources.get(0).getDisplayName());
        assertEquals("forwarded.pdf", resources.get(1).getDisplayName());
    }

    @Test
    public void fallsBackToTimeStringWhenEpochIsMissing() throws Exception {
        MoectArchive archive = parse("""
                {
                  "metadata": {"name": "好友", "session": "wxid_friend"},
                  "messages": [
                    {
                      "server_id": "fallback-time",
                      "sender": "好友",
                      "time_str": "2026-11-22 14:13:48",
                      "text": "测试",
                      "kind": "text"
                    }
                  ]
                }
                """);

        assertEquals(
                1_795_328_028_000L,
                archive.getMessages().get(0).getOccurredAtMillis());
    }

    @Test
    public void rejectsMalformedJson() {
        assertThrows(
                MoectImportException.class,
                () -> new MoectArchiveParser().parse("{not-json", SHANGHAI));
    }

    @Test
    public void rejectsTraversalAndAbsoluteResourcePaths() {
        String traversal = """
                {
                  "metadata": {"name": "会话", "session": "wxid"},
                  "messages": [{
                    "server_id": "1",
                    "sender": "我",
                    "create_time": 1700000000,
                    "kind": "image",
                    "resources": [{
                      "kind": "image",
                      "path": "../../secret.jpg",
                      "name": "secret.jpg",
                      "status": "available"
                    }]
                  }]
                }
                """;
        String absolute = traversal.replace("../../secret.jpg", "/sdcard/secret.jpg");

        assertThrows(
                MoectImportException.class,
                () -> parse(traversal));
        assertThrows(
                MoectImportException.class,
                () -> parse(absolute));
    }

    private MoectArchive parse(String json) throws MoectImportException {
        return new MoectArchiveParser().parse(json, SHANGHAI);
    }

    private String multimodalJson(String status) {
        return """
                {
                  "metadata": {
                    "name": "项目私聊",
                    "session": "wxid_project",
                    "time_zone": "Asia/Shanghai"
                  },
                  "messages": [
                    {
                      "server_id": "201",
                      "sender": "我",
                      "create_time": 1700000100,
                      "text": "",
                      "kind": "image",
                      "resources": [{
                        "kind": "image",
                        "path": "chat_assets/resources/image/photo.jpg",
                        "name": "photo.jpg",
                        "status": "%STATUS%"
                      }]
                    },
                    {
                      "server_id": "202",
                      "sender": "我",
                      "create_time": 1700000200,
                      "text": "",
                      "kind": "video",
                      "resources": [{
                        "kind": "video",
                        "path": "chat_assets/resources/video/clip.mp4",
                        "name": "clip.mp4",
                        "status": "%STATUS%"
                      }]
                    },
                    {
                      "server_id": "203",
                      "sender": "老师",
                      "create_time": 1700000300,
                      "text": "",
                      "kind": "file",
                      "resources": [{
                        "kind": "file",
                        "path": "chat_assets/resources/file/report.pdf",
                        "name": "课程设计.pdf",
                        "status": "%STATUS%"
                      }]
                    },
                    {
                      "server_id": "204",
                      "sender": "我",
                      "create_time": 1700000400,
                      "text": "",
                      "kind": "voice",
                      "resources": [{
                        "kind": "audio",
                        "path": "chat_assets/resources/audio/voice.wav",
                        "name": "voice.wav",
                        "status": "%STATUS%"
                      }]
                    },
                    {
                      "server_id": "205",
                      "sender": "我",
                      "create_time": 1700000500,
                      "text": "",
                      "kind": "emoji",
                      "resources": [{
                        "kind": "image",
                        "path": "chat_assets/resources/image/sticker.gif",
                        "name": "sticker.gif",
                        "status": "%STATUS%"
                      }]
                    },
                    {
                      "server_id": "206",
                      "sender": "我",
                      "create_time": 1700000600,
                      "text": "",
                      "kind": "unknown",
                      "resources": [{
                        "kind": "original",
                        "path": "chat_assets/resources/original/blob.bin",
                        "name": "blob.bin",
                        "status": "%STATUS%"
                      }]
                    }
                  ]
                }
                """.replace("%STATUS%", status);
    }
}
