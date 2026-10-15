package com.legalsuite.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

/**
 * Allowed document types for matter and client files. The extension picks the expected type and the first bytes
 * must match it, so a renamed executable or web page is refused. Empty files are refused.
 */
public final class DocumentTypePolicy {
    private DocumentTypePolicy() {}

    public static final Set<String> ALLOWED = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "odt", "ods", "rtf", "txt", "csv",
            "jpg", "jpeg", "png", "heic", "heif", "eml", "msg", "zip");

    private static final Map<String, String> MIME = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("odt", "application/vnd.oasis.opendocument.text"),
            Map.entry("ods", "application/vnd.oasis.opendocument.spreadsheet"),
            Map.entry("rtf", "application/rtf"),
            Map.entry("txt", "text/plain"),
            Map.entry("csv", "text/csv"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("heic", "image/heic"),
            Map.entry("heif", "image/heif"),
            Map.entry("eml", "message/rfc822"),
            Map.entry("msg", "application/vnd.ms-outlook"),
            Map.entry("zip", "application/zip"));

    public static final String HELP =
            "Upload a PDF, Word, Excel, OpenDocument, RTF, text, CSV, JPEG, PNG, HEIC, email (.eml or .msg) or ZIP file.";

    /** Checks the file and returns the MIME type to store. Throws a 400 ApiException when it is not allowed. */
    public static String check(MultipartFile file, String cleanName) {
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            throw ApiException.badRequest("The file is empty. Choose a file with content.");
        }
        String ext = UploadPolicy.extension(cleanName);
        if (!ALLOWED.contains(ext)) throw ApiException.badRequest("That file type is not allowed. " + HELP);
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(8192);
        } catch (IOException e) {
            throw ApiException.badRequest("The file could not be read. Try again.");
        }
        if (!matches(ext, head)) {
            throw ApiException.badRequest("The file content does not match its ." + ext + " extension. " + HELP);
        }
        return MIME.get(ext);
    }

    static boolean matches(String ext, byte[] b) {
        return switch (ext) {
            case "pdf" -> starts(b, "%PDF-".getBytes(StandardCharsets.US_ASCII));
            case "docx", "xlsx", "odt", "ods", "zip" -> starts(b, new byte[] {'P', 'K', 3, 4}) || (ext.equals("zip") && starts(b, new byte[] {'P', 'K', 5, 6}));
            case "doc", "xls", "msg" -> starts(b, new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1});
            case "rtf" -> starts(b, "{\\rtf".getBytes(StandardCharsets.US_ASCII));
            case "png" -> starts(b, new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
            case "jpg", "jpeg" -> starts(b, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case "heic", "heif" -> heif(b);
            case "txt", "csv", "eml" -> text(b);
            default -> false;
        };
    }

    private static boolean starts(byte[] b, byte[] sig) {
        if (b.length < sig.length) return false;
        for (int i = 0; i < sig.length; i++) if (b[i] != sig[i]) return false;
        return true;
    }

    private static boolean heif(byte[] b) {
        if (b.length < 12 || b[4] != 'f' || b[5] != 't' || b[6] != 'y' || b[7] != 'p') return false;
        String brand = new String(b, 8, 4, StandardCharsets.US_ASCII);
        return Set.of("heic", "heix", "hevc", "hevx", "mif1", "msf1", "heif").contains(brand);
    }

    /** Plain text: no NUL bytes and no HTML or script start, so a web page renamed to .txt is refused. */
    private static boolean text(byte[] b) {
        for (byte x : b) if (x == 0) return false;
        String s = new String(b, StandardCharsets.UTF_8).stripLeading().toLowerCase(java.util.Locale.ROOT);
        return !(s.startsWith("<!doctype html") || s.startsWith("<html") || s.startsWith("<script") || s.startsWith("<svg"));
    }
}
