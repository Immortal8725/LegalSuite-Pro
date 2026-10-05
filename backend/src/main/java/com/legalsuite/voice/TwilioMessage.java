package com.legalsuite.voice;

/**
 * One Twilio Messages API send. SMS uses a normal E.164 To and From.
 * WhatsApp uses the {@code whatsapp:+E164} form on both. Blank optional fields are omitted.
 */
public record TwilioMessage(
        String to,
        String from,
        String messagingServiceSid,
        String body,
        String contentSid,
        String contentVariables,
        String statusCallback
) {}
