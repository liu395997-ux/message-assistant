package com.wechatarchive;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SkeletonTest {
    @Test
    public void packageNameIsStable() {
        assertEquals("com.wechatarchive", BuildConfig.APPLICATION_ID);
    }
}
