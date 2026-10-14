package com.legalsuite.common;

import java.util.UUID;

/** Parse ids that arrive in JSON bodies. Blank means "not chosen", junk means 400, never 500. */
public final class Ids {
    private Ids() {}

    public static UUID optUuid(Object value, String field) {
        if (value == null) return null;
        String raw = String.valueOf(value).trim();
        if (raw.isEmpty() || "null".equals(raw) || "undefined".equals(raw)) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("The " + field + " is not a valid id.");
        }
    }

    public static UUID requireUuid(Object value, String field, String whenMissing) {
        UUID id = optUuid(value, field);
        if (id == null) throw ApiException.badRequest(whenMissing);
        return id;
    }
}
