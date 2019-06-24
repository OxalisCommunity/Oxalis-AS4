package no.difi.oxalis.as4.outbound;

import no.difi.oxalis.api.outbound.TransmissionRequest;

import java.nio.charset.Charset;
import java.util.Map;

public interface As4TransmissionRequest extends TransmissionRequest {

    default String getRefToMessageId() { return  null; }

    default String getMessageId() {
        return null;
    }

    default String getConversationId() {
        return null;
    }

    default Map<String, String> getMessageProperties() {
        return null;
    }

    default String getPayloadHref() {
        return null;
    }

    default Charset getPayloadCharset() { return null; }

    default boolean isPing(){
        return false;
    }
}
