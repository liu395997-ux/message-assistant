package com.wechatarchive.service;

import android.app.Notification;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.wechatarchive.data.ArchiveDatabase;
import com.wechatarchive.data.ArchiveSettings;
import com.wechatarchive.domain.CapturePolicy;
import com.wechatarchive.domain.CapturedMessage;
import com.wechatarchive.domain.NotificationParser;
import com.wechatarchive.domain.RawMessage;
import com.wechatarchive.domain.RawNotification;
import com.wechatarchive.importing.AttachmentStore;
import com.wechatarchive.importing.NotificationPictureArchiver;

import java.util.ArrayList;
import java.util.List;

public final class WeChatNotificationListener extends NotificationListenerService {
    public static final String ACTION_ARCHIVE_UPDATED =
            "com.wechatarchive.ACTION_ARCHIVE_UPDATED";

    private final NotificationParser parser = new NotificationParser();

    @Override
    public void onNotificationPosted(StatusBarNotification statusBarNotification) {
        if (statusBarNotification == null
                || !CapturePolicy.WECHAT_PACKAGE.equals(statusBarNotification.getPackageName())
                || statusBarNotification.isOngoing()) {
            return;
        }

        Notification notification = statusBarNotification.getNotification();
        if (notification == null) {
            return;
        }

        RawNotification raw = extract(statusBarNotification, notification);
        ArchiveSettings settings = new ArchiveSettings(this);
        CapturePolicy policy = settings.currentPolicy();
        if (!policy.shouldCapture(raw.getPackageName(), raw.getConversationTitle())) {
            return;
        }

        ArchiveDatabase database = ArchiveDatabase.getInstance(this);
        AttachmentStore attachmentStore = new AttachmentStore(this);
        long newestMessageId = -1L;
        boolean hasMessage = false;
        for (CapturedMessage message : parser.parse(raw)) {
            if (policy.shouldCapture(raw.getPackageName(), message.getConversation())) {
                long messageId = database.insertOrGetId(message);
                if (messageId > 0L) {
                    newestMessageId = messageId;
                    hasMessage = true;
                }
            }
        }

        Bitmap picture = notificationPicture(notification);
        if (picture != null && newestMessageId > 0L) {
            NotificationPictureArchiver.archive(
                    this,
                    database,
                    attachmentStore,
                    picture,
                    newestMessageId,
                    "notification:picture:" + statusBarNotification.getKey());
        }
        if (hasMessage) {
            sendBroadcast(new Intent(ACTION_ARCHIVE_UPDATED).setPackage(getPackageName()));
        }
    }

    private RawNotification extract(
            StatusBarNotification statusBarNotification,
            Notification notification) {
        Bundle extras = notification.extras;
        if (extras == null) {
            extras = Bundle.EMPTY;
        }

        List<String> textLines = new ArrayList<>();
        CharSequence[] lineArray = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lineArray != null) {
            for (CharSequence line : lineArray) {
                if (line != null && !line.toString().trim().isEmpty()) {
                    textLines.add(line.toString());
                }
            }
        }

        String conversationTitle = text(
                extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));
        String title = text(extras.getCharSequence(Notification.EXTRA_TITLE));
        if (conversationTitle.isEmpty()) {
            conversationTitle = title;
        }

        return new RawNotification(
                statusBarNotification.getPackageName(),
                title,
                text(extras.getCharSequence(Notification.EXTRA_TEXT)),
                text(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)),
                text(extras.getCharSequence(Notification.EXTRA_SUB_TEXT)),
                conversationTitle,
                textLines,
                extractMessagingStyle(extras),
                statusBarNotification.getPostTime(),
                statusBarNotification.getKey());
    }

    @SuppressWarnings("deprecation")
    private Bitmap notificationPicture(Notification notification) {
        if (notification.extras == null) {
            return null;
        }
        Parcelable value = notification.extras.getParcelable(Notification.EXTRA_PICTURE);
        return value instanceof Bitmap ? (Bitmap) value : null;
    }
    private List<RawMessage> extractMessagingStyle(Bundle extras) {
        Parcelable[] bundles = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        return MessagingStyleExtractor.extract(bundles);
    }

    private String text(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
