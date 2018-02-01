package no.difi.oxalis.as4.outbound;

import org.springframework.ws.client.core.WebServiceMessageCallback;
import org.springframework.ws.client.core.WebServiceMessageExtractor;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.springframework.ws.transport.WebServiceConnection;

import java.io.IOException;

public class As4WebServiceTemplate extends WebServiceTemplate {

    As4WebServiceTemplate(SaajSoapMessageFactory factory) {
        super(factory);
    }

    @Override
    protected <T> T doSendAndReceive(MessageContext messageContext, WebServiceConnection connection,
                                     WebServiceMessageCallback requestCallback, WebServiceMessageExtractor<T> responseExtractor) throws IOException {
        return super.doSendAndReceive(messageContext, connection, requestCallback, responseExtractor);
    }
}
