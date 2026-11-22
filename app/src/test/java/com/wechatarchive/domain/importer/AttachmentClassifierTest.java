package com.wechatarchive.domain.importer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class AttachmentClassifierTest {
    @Test
    public void recognizesSupportedMultimodalKinds() {
        assertEquals(
                AttachmentKind.IMAGE,
                AttachmentClassifier.classify("image", "image", "photo.jpg"));
        assertEquals(
                AttachmentKind.VIDEO,
                AttachmentClassifier.classify("video", "video", "clip.mp4"));
        assertEquals(
                AttachmentKind.DOCUMENT,
                AttachmentClassifier.classify("file", "file", "report.pdf"));
        assertEquals(
                AttachmentKind.AUDIO,
                AttachmentClassifier.classify("voice", "audio", "voice.wav"));
        assertEquals(
                AttachmentKind.ANIMATED_STICKER,
                AttachmentClassifier.classify("emoji", "image", "sticker.gif"));
        assertEquals(
                AttachmentKind.OTHER,
                AttachmentClassifier.classify("unknown", "original", "blob.bin"));
    }

    @Test
    public void recognizesAnimatedExtensionsAsStickers() {
        assertEquals(
                AttachmentKind.ANIMATED_STICKER,
                AttachmentClassifier.classify("image", "image", "funny.webp"));
        assertEquals(
                AttachmentKind.ANIMATED_STICKER,
                AttachmentClassifier.classify("image", "image", "funny.gif"));
    }

    @Test
    public void detectsCommonMimeTypes() {
        assertEquals("image/jpeg", AttachmentClassifier.mimeTypeForName("a.jpg"));
        assertEquals("video/mp4", AttachmentClassifier.mimeTypeForName("a.mp4"));
        assertEquals("application/pdf", AttachmentClassifier.mimeTypeForName("a.pdf"));
        assertEquals("image/gif", AttachmentClassifier.mimeTypeForName("a.gif"));
        assertEquals("application/octet-stream", AttachmentClassifier.mimeTypeForName("a.unknown"));
    }

    @Test
    public void sanitizesDisplayNames() {
        assertEquals("report.pdf", AttachmentClassifier.safeDisplayName("../report.pdf", "x"));
        assertEquals("conversation.txt", AttachmentClassifier.safeDisplayName(":", "conversation.txt"));
        assertEquals("file.bin", AttachmentClassifier.safeDisplayName("", "file.bin"));
    }
}
