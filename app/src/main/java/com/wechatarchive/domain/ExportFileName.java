package com.wechatarchive.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class ExportFileName {
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ExportFileName() {
    }

    public static String create(Instant exportedAt, ZoneId zoneId) {
        return "wechat-notifications-"
                + FILE_TIME.format(exportedAt.atZone(zoneId))
                + ".md";
    }
}
