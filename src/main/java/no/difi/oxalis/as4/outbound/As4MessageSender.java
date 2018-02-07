package no.difi.oxalis.as4.outbound;

import com.google.inject.Inject;
import no.difi.oxalis.api.outbound.TransmissionRequest;
import no.difi.oxalis.api.outbound.TransmissionResponse;
import no.difi.oxalis.as4.util.Marshalling;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.SoapVersion;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.springframework.ws.transport.http.HttpComponentsMessageSender;

import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPException;
import java.security.cert.X509Certificate;

public class As4MessageSender {

    private X509Certificate certificate;

    @Inject
    public As4MessageSender(X509Certificate certificate) {
        this.certificate = certificate;
    }

    public TransmissionResponse send(TransmissionRequest request) {
        WebServiceTemplate template = createTemplate();
        As4Sender sender = new As4Sender(request, certificate);
        template.sendAndReceive(request.getEndpoint().getAddress().toString(), sender, new TransmissionResponseExtractor());
        return null;
    }

    private SaajSoapMessageFactory createSoapMessageFactory() {
        SaajSoapMessageFactory factory;
        try {
            factory = new SaajSoapMessageFactory(MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL));
            factory.setSoapVersion(SoapVersion.SOAP_12);
            factory.afterPropertiesSet();
            return factory;
        } catch (SOAPException e) {
            throw new RuntimeException("Error creating SoapMessageFactory", e);
        }
    }

    private WebServiceTemplate createTemplate() {
        As4WebServiceTemplate template = new As4WebServiceTemplate(createSoapMessageFactory());
        template.setMarshaller(Marshalling.getInstance());
        template.setUnmarshaller(Marshalling.getInstance());
        template.setMessageSender(createMessageSender());
        return template;
    }

    private HttpComponentsMessageSender createMessageSender() {
        HttpComponentsMessageSender messageSender = new HttpComponentsMessageSender();
        return messageSender;
    }
}
