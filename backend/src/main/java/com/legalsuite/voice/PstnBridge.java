package com.legalsuite.voice;

/** TwiML for the second leg of a callback bridge. The client hears the firm caller ID, not the attorney's mobile. */
public final class PstnBridge {
    private PstnBridge() {}

    public static String twiml(String callerId, String destination, String dialResultUrl, boolean record, String recordingStatusUrl) {
        String recordAttr = record ? "record-from-answer" : "do-not-record";
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<Response>");
        xml.append("<Say voice=\"alice\">Connecting this call. The person you are calling will see your firm number.</Say>");
        xml.append("<Dial callerId=\"").append(xmlAttr(callerId)).append("\"");
        xml.append(" answerOnBridge=\"true\" timeout=\"40\"");
        xml.append(" action=\"").append(xmlAttr(dialResultUrl)).append("\" method=\"POST\"");
        xml.append(" record=\"").append(recordAttr).append("\"");
        if (record && recordingStatusUrl != null && !recordingStatusUrl.isBlank()) {
            xml.append(" recordingStatusCallback=\"").append(xmlAttr(recordingStatusUrl)).append("\"");
            xml.append(" recordingStatusCallbackMethod=\"POST\"");
        }
        xml.append(">");
        xml.append("<Number>").append(xmlText(destination)).append("</Number>");
        xml.append("</Dial>");
        xml.append("</Response>");
        return xml.toString();
    }

    public static String say(String message) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Say voice=\"alice\">"
                + xmlText(message)
                + "</Say></Response>";
    }

    static String xmlAttr(String value) {
        return xmlText(value).replace("\"", "&quot;");
    }

    static String xmlText(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
