package no.difi.oxalis.as4.outbound;

import no.difi.oxalis.api.outbound.TransmissionRequest;

public interface As4TransmissionRequest extends TransmissionRequest {

    String getConversationId();
}
