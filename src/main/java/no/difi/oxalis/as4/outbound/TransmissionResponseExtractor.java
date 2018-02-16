package no.difi.oxalis.as4.outbound;

import no.difi.oxalis.api.outbound.TransmissionResponse;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.client.core.WebServiceMessageExtractor;
import org.springframework.ws.soap.SoapMessage;

import javax.xml.transform.TransformerException;
import java.io.IOException;

public class TransmissionResponseExtractor implements WebServiceMessageExtractor<TransmissionResponse> {

    @Override
    public TransmissionResponse extractData(WebServiceMessage webServiceMessage) throws IOException, TransformerException {
        // TODO: process response
        SoapMessage soapMessage = (SoapMessage) webServiceMessage;
        return null;
    }
}
