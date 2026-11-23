package com.wechatarchive.service;

import android.app.Person;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;

import com.wechatarchive.domain.RawMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class MessagingStyleExtractor {
    private MessagingStyleExtractor() {
    }

    @SuppressWarnings("deprecation")
    static List<RawMessage> extract(Parcelable[] bundles) {
        if (bundles == null || bundles.length == 0) {
            return Collections.emptyList();
        }

        List<RawMessage> messages = new ArrayList<>();
        for (Parcelable parcelable : bundles) {
            if (!(parcelable instanceof Bundle)) {
                continue;
            }
            Bundle bundle = (Bundle) parcelable;
            String text = text(bundle.getCharSequence("text"));
            if (text.isEmpty()) {
                continue;
            }

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
                    text,
                    bundle.getLong("time", 0L)));
        }
        return messages;
    }

    private static String text(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
