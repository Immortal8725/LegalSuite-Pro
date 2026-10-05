package com.legalsuite.voice;

import java.util.List;
import java.util.Map;

/** Carrier operations used by the firm number inventory and the callback bridge. */
public interface TwilioGateway {
    List<Map<String, String>> searchLocal(String country, String areaCode, String contains, String locality);

    /** Buys a local DID. Returns sid and phoneNumber. */
    Map<String, String> buyLocal(String e164, String friendlyName, String voiceUrl, String statusCallback);

    void releaseIncoming(String sid);

    /** Starts Outgoing Caller ID verification for a mobile or landline. Returns validationCode and callSid. */
    Map<String, String> startCallerIdVerification(String e164, String friendlyName, String statusCallback);

    /** SID of a verified outgoing caller ID, or null when Twilio has not confirmed it yet. */
    String findVerifiedCallerIdSid(String e164);

    /** First IncomingPhoneNumber on the account, or null when the account owns none. */
    String firstIncomingNumber();

    /** First verified Outgoing Caller ID on the account, or null when none is verified. */
    String firstOutgoingCallerId();

    void releaseCallerId(String sid);

    /** Places the first leg to the attorney's phone. Returns the call SID. */
    String createCall(String to, String from, String url, String statusCallback);
}
