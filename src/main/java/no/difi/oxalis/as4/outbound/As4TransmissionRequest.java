package no.difi.oxalis.as4.outbound;

import no.difi.oxalis.api.outbound.TransmissionRequest;
import no.difi.oxalis.as4.common.As4MessageProperties;

import java.nio.charset.Charset;

public interface As4TransmissionRequest extends TransmissionRequest {

    String getRefToMessageId();

    String getMessageId();

    String getConversationId();

    As4MessageProperties getMessageProperties();

    String getPayloadHref();

    Charset getPayloadCharset();

    String getCompressionType();

    boolean isPing();
}
