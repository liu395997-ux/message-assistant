package com.wechatarchive.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.wechatarchive.domain.CapturedMessage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ArchiveDatabaseSingletonTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @After
    public void tearDown() {
        ArchiveDatabaseTestSupport.resetSharedInstance();
        context.deleteDatabase(ArchiveDatabase.DATABASE_NAME);
    }

    @Test
    public void applicationContextReceivesTheSameHelper() {
        ArchiveDatabase first = ArchiveDatabase.getInstance(context);
        ArchiveDatabase second = ArchiveDatabase.getInstance(context);

        assertSame(first, second);
        assertEquals(0, first.count());
        assertEquals(0, second.count());
    }

    @Test
    public void sharedHelperCanInsertAndReadRepeatedly() {
        ArchiveDatabase database = ArchiveDatabase.getInstance(context);
        CapturedMessage message = new CapturedMessage(
                "项目群",
                "李老师",
                "李老师",
                "测试单例数据库",
                1_700_000_000_000L,
                "singleton:test",
                false);

        assertEquals(true, database.insert(message));
        assertEquals(false, database.insert(message));
        assertEquals(1, ArchiveDatabase.getInstance(context).count());
    }
}
