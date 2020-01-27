package no.difi.oxalis.as4.outbound;

import no.difi.oxalis.api.outbound.TransmissionRequest;

import java.nio.charset.Charset;
import java.util.Map;

public interface As4TransmissionRequest extends TransmissionRequest {

    String getRefToMessageId();

    String getMessageId();

    String getConversationId();

    Map<String, String> getMessageProperties();

    String getPayloadHref();

    Charset getPayloadCharset();

    String getCompressionType();

    boolean isPing();
}
