package com.wechatarchive.service;

import android.app.Notification;
import android.app.Person;
import android.content.Intent;
import android.os.Build;
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

import java.util.ArrayList;
import java.util.Collections;
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

        ArchiveDatabase database = new ArchiveDatabase(this);
        boolean inserted = false;
        for (CapturedMessage message : parser.parse(raw)) {
            if (policy.shouldCapture(raw.getPackageName(), message.getConversation())) {
                inserted |= database.insert(message);
            }
        }
        if (inserted) {
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
    private List<RawMessage> extractMessagingStyle(Bundle extras) {
        Parcelable[] bundles = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (bundles == null || bundles.length == 0) {
            return Collections.emptyList();
        }

        List<Notification.MessagingStyle.Message> styleMessages = Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.R
                ? Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles)
                : null;
        if (styleMessages == null || styleMessages.isEmpty()) {
            return extractLegacyMessagingStyle(bundles);
        }

        List<RawMessage> messages = new ArrayList<>();
        for (Notification.MessagingStyle.Message message : styleMessages) {
            if (message == null) {
                continue;
            }
            String sender = "";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Person senderPerson = message.getSenderPerson();
                if (senderPerson != null) {
                    sender = text(senderPerson.getName());
                }
            }
            if (sender.isEmpty()) {
                sender = text(message.getSender());
            }
            messages.add(new RawMessage(
                    sender,
                    text(message.getText()),
                    message.getTimestamp()));
        }
        return messages;
    }

    @SuppressWarnings("deprecation")
    private List<RawMessage> extractLegacyMessagingStyle(Parcelable[] bundles) {
        List<RawMessage> messages = new ArrayList<>();
        for (Parcelable parcelable : bundles) {
            if (!(parcelable instanceof Bundle)) {
                continue;
            }
            Bundle bundle = (Bundle) parcelable;
            String sender = "";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Person person = bundle.getParcelable("sender_person");
                if (person != null) {
                    sender = text(person.getName());
                }
            }
            if (sender.isEmpty()) {
                sender = text(bundle.getCharSequence("sender"));
            }
            messages.add(new RawMessage(
                    sender,
                    text(bundle.getCharSequence("text")),
                    bundle.getLong("time", 0L)));
        }
        return messages;
    }

    private String text(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
