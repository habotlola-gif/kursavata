package dev.hatek.client.feature.account;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record Account(String name, UUID id) {
    public static final int MAX_NAME = 16;

    public static Account of(String name) {
        String trimmed = name.trim();
        return new Account(trimmed, offlineId(trimmed));
    }

    public static UUID offlineId(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    public static boolean valid(String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim();
        if (trimmed.length() < 3 || trimmed.length() > MAX_NAME) {
            return false;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean ok = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z'
                    || c >= '0' && c <= '9' || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
