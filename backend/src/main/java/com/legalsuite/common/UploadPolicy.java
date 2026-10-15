package com.legalsuite.common;

import java.util.Locale;
import java.util.UUID;

/** Rules for user-supplied file names. The original name is display text only; disk names are always generated. */
public final class UploadPolicy {
    private UploadPolicy() {}

    /** Display name: last path segment only, safe characters, never blank or a dot name, at most 120 characters. */
    public static String safeName(String original, String fallback) {
        String name = original == null ? "" : original;
        name = name.replace('\0', '_');
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("[^A-Za-z0-9 ._()-]", "_").trim();
        while (name.startsWith(".")) name = name.substring(1);
        if (name.isBlank()) name = fallback;
        if (name.length() > 120) {
            String ext = extension(name);
            int keep = 120 - (ext.isEmpty() ? 0 : ext.length() + 1);
            name = name.substring(0, keep) + (ext.isEmpty() ? "" : "." + ext);
        }
        return name;
    }

    /** Lower-case extension of the cleaned name, or an empty string. */
    public static String extension(String name) {
        if (name == null) return "";
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String n = slash >= 0 ? name.substring(slash + 1) : name;
        int dot = n.lastIndexOf('.');
        if (dot <= 0 || dot == n.length() - 1) return "";
        String ext = n.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.matches("[a-z0-9]{1,8}") ? ext : "";
    }

    /** Disk name: a random UUID plus the cleaned extension. No user text reaches the file system. */
    public static String storageName(String original) {
        String ext = extension(safeName(original, "file"));
        return UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
    }
}
