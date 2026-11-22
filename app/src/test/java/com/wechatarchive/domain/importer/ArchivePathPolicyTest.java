package com.wechatarchive.domain.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ArchivePathPolicyTest {
    @Test
    public void acceptsNestedRelativePaths() {
        assertTrue(ArchivePathPolicy.isSafeRelativePath(
                "chat_assets/resources/image/photo.jpg"));
        assertEquals(
                "chat_assets/resources/image/photo.jpg",
                ArchivePathPolicy.normalize("chat_assets\\resources\\image\\photo.jpg"));
    }

    @Test
    public void rejectsAbsoluteTraversalAndNulPaths() {
        assertFalse(ArchivePathPolicy.isSafeRelativePath("/sdcard/photo.jpg"));
        assertFalse(ArchivePathPolicy.isSafeRelativePath("../photo.jpg"));
        assertFalse(ArchivePathPolicy.isSafeRelativePath("folder/../../photo.jpg"));
        assertFalse(ArchivePathPolicy.isSafeRelativePath("C:/photo.jpg"));
        assertFalse(ArchivePathPolicy.isSafeRelativePath("folder/\u0000photo.jpg"));
    }

    @Test
    public void identifiesJsonEntriesCaseInsensitively() {
        assertTrue(ArchivePathPolicy.isJsonEntry("exports/chat.JSON"));
        assertFalse(ArchivePathPolicy.isJsonEntry("exports/resource-report.jsonl"));
        assertFalse(ArchivePathPolicy.isJsonEntry(null));
    }

    @Test
    public void resolvesResourcePathsRelativeToJsonDirectory() {
        assertEquals(
                "batch/chat/chat_assets/resources/image/photo.jpg",
                ArchivePathPolicy.resolveRelative(
                        "batch/chat/chat.json",
                        "chat_assets/resources/image/photo.jpg"));
    }
}
