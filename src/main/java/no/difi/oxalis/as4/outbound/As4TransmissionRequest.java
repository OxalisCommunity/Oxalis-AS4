package no.difi.oxalis.as4.outbound;

import no.difi.oxalis.api.outbound.TransmissionRequest;

import java.util.Map;

public interface As4TransmissionRequest extends TransmissionRequest {

    String getMessageId();

    String getConversationId();

    Map<String, String> getMessageProperties();
}
