package com.wechatarchive.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ConversationWhitelistParser {
    private ConversationWhitelistParser() {
    }

    public static List<String> parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> result = new LinkedHashSet<>();
        for (String token : raw.split("[,，\\n\\r]+")) {
            String clean = token.trim();
            if (!clean.isEmpty()) {
                result.add(clean);
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(result));
    }
}
