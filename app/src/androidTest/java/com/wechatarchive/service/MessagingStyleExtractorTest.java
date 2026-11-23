package com.wechatarchive.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Person;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.wechatarchive.domain.RawMessage;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class MessagingStyleExtractorTest {
    @Test
    public void extractsPersonTextAndTimestamp() {
        Bundle message = new Bundle();
        message.putCharSequence("text", "项目文档已更新");
        message.putLong("time", 1_700_000_000_123L);
        message.putCharSequence("sender", "旧发送者");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            message.putParcelable(
                    "sender_person",
                    new Person.Builder().setName("李老师").build());
        }

        List<RawMessage> messages = MessagingStyleExtractor.extract(
                new Parcelable[]{message});

        assertEquals(1, messages.size());
        assertEquals("项目文档已更新", messages.get(0).getText());
        assertEquals(1_700_000_000_123L, messages.get(0).getTimestampMillis());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            assertEquals("李老师", messages.get(0).getSender());
        } else {
            assertEquals("旧发送者", messages.get(0).getSender());
        }
    }

    @Test
    public void fallsBackToLegacySenderWhenPersonIsAbsent() {
        Bundle message = new Bundle();
        message.putCharSequence("sender", "王同学");
        message.putCharSequence("text", "收到");
        message.putLong("time", 42L);

        List<RawMessage> messages = MessagingStyleExtractor.extract(
                new Parcelable[]{message});

        assertEquals(1, messages.size());
        assertEquals("王同学", messages.get(0).getSender());
        assertEquals("收到", messages.get(0).getText());
        assertEquals(42L, messages.get(0).getTimestampMillis());
    }

    @Test
    public void ignoresMalformedAndBlankEntries() {
        Bundle blank = new Bundle();
        blank.putCharSequence("text", "   ");
        blank.putLong("time", 7L);
        Bundle missingTimestamp = new Bundle();
        missingTimestamp.putCharSequence("sender", "刘同学");
        missingTimestamp.putCharSequence("text", "到教室了");

        List<RawMessage> messages = MessagingStyleExtractor.extract(
                new Parcelable[]{null, new Intent(), blank, missingTimestamp});

        assertEquals(1, messages.size());
        assertEquals("刘同学", messages.get(0).getSender());
        assertEquals("到教室了", messages.get(0).getText());
        assertEquals(0L, messages.get(0).getTimestampMillis());
        assertFalse(MessagingStyleExtractor.extract(null).iterator().hasNext());
        assertTrue(MessagingStyleExtractor.extract(new Parcelable[0]).isEmpty());
    }
}
