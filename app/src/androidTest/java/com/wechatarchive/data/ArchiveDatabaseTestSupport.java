package com.wechatarchive.data;

public final class ArchiveDatabaseTestSupport {
    private ArchiveDatabaseTestSupport() {
    }

    public static void resetSharedInstance() {
        ArchiveDatabase.resetInstanceForTests();
    }
}
