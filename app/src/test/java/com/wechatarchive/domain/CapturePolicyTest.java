package com.wechatarchive.domain;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public final class CapturePolicyTest {
    @Test
    public void rejectsNonWeChatPackages() {
        CapturePolicy policy = new CapturePolicy(true, Collections.emptyList());

        assertFalse(policy.shouldCapture("com.tencent.mobileqq", "文件传输助手"));
        assertTrue(policy.shouldCapture("com.tencent.mm", "文件传输助手"));
    }

    @Test
    public void rejectsEverythingWhenCaptureIsDisabled() {
        CapturePolicy policy = new CapturePolicy(false, Collections.emptyList());

        assertFalse(policy.shouldCapture("com.tencent.mm", "项目群"));
    }

    @Test
    public void emptyWhitelistAllowsEveryWeChatConversation() {
        CapturePolicy policy = new CapturePolicy(true, Collections.emptyList());

        assertTrue(policy.shouldCapture("com.tencent.mm", "任意会话"));
    }

    @Test
    public void whitelistMatchesTrimmedConversationNamesOnly() {
        CapturePolicy policy = new CapturePolicy(
                true,
                Arrays.asList(" 项目群 ", "辅导员", ""));

        assertTrue(policy.shouldCapture("com.tencent.mm", "项目群"));
        assertTrue(policy.shouldCapture("com.tencent.mm", " 辅导员 "));
        assertFalse(policy.shouldCapture("com.tencent.mm", "家人群"));
    }

    @Test
    public void summaryNotificationsCanStillBeFilteredByPolicy() {
        CapturePolicy policy = new CapturePolicy(
                true,
                Collections.singletonList("项目群"));

        assertTrue(policy.shouldCapture("com.tencent.mm", "项目群"));
        assertFalse(policy.shouldCapture("com.tencent.mm", "其他群"));
    }
}
