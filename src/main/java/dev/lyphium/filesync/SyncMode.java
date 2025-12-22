package dev.lyphium.filesync;

import org.jspecify.annotations.Nullable;

public enum SyncMode {

    PULL,
    PUSH,
    MANUAL,
    NONE;

    public static @Nullable SyncMode fromName(String value) {
        for (final SyncMode mode : SyncMode.values()) {
            if (mode.name().equalsIgnoreCase(value)) {
                return mode;
            }
        }

        return null;
    }

}
