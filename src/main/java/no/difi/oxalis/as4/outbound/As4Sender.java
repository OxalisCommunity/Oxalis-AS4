package no.difi.oxalis.as4.outbound;

import com.google.common.collect.Lists;
import no.difi.oxalis.api.outbound.TransmissionRequest;
import no.difi.oxalis.as4.util.Constants;
import no.difi.oxalis.as4.util.Marshalling;
import no.difi.oxalis.commons.security.CertificateUtils;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.*;
import org.springframework.http.MediaType;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.client.core.WebServiceMessageCallback;
import org.springframework.ws.mime.Attachment;
import org.springframework.ws.soap.SoapHeader;
import org.springframework.ws.soap.SoapHeaderElement;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.saaj.SaajSoapMessage;

import javax.xml.bind.JAXBElement;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.UUID;

import static no.difi.oxalis.as4.util.Constants.*;

public class As4Sender implements WebServiceMessageCallback {

    private final TransmissionRequest request;
    private final X509Certificate certificate;

    As4Sender(TransmissionRequest request, X509Certificate certificate) {
        this.request = request;
        this.certificate = certificate;
    }

    @Override
    public void doWithMessage(WebServiceMessage webServiceMessage) throws IOException, TransformerException {
        SaajSoapMessage message = (SaajSoapMessage) webServiceMessage;
        // Must be octet-stream for encrypted attachments
        message.addAttachment(newId(), () -> request.getPayload(), MediaType.APPLICATION_OCTET_STREAM_VALUE);
        addEbmsHeader(message);
    }

    private void addEbmsHeader(SoapMessage message) {
        SoapHeader header = message.getSoapHeader();
        SoapHeaderElement messagingHeader = header.addHeaderElement(Constants.MESSAGING_QNAME);
        messagingHeader.setMustUnderstand(true);

        UserMessage userMessage = UserMessage.builder()
                .withMessageInfo(createMessageInfo())
                .withPartyInfo(createPartyInfo())
                .withCollaborationInfo(createCollaborationInfo())
                .withMessageProperties(createMessageProperties())
                .withPayloadInfo(createPayloadInfo(message))
                .build();

        JAXBElement<UserMessage> userMessageJAXBElement = new JAXBElement<>(Constants.USER_MESSAGE_QNAME,
                (Class<UserMessage>) userMessage.getClass(), userMessage);
        Jaxb2Marshaller marshaller = Marshalling.getInstance();
        marshaller.marshal(userMessageJAXBElement, messagingHeader.getResult());
    }

    private PayloadInfo createPayloadInfo(SoapMessage message) {
        Iterator<Attachment> attachments = message.getAttachments();
        ArrayList<PartInfo> partInfos = Lists.newArrayList();
        while (attachments.hasNext()) {
            Attachment a = attachments.next();
            String cid = "cid:"+a.getContentId();
            Property compressionType = Property.builder()
                    .withName("CompressionType")
                    .withValue("application/gzip")
                    .build();
            Property mimeType = Property.builder()
                    .withName("MimeType")
                    .withValue("application/xml")
                    .build();
            PartProperties partProperties = PartProperties.builder().withProperty(compressionType, mimeType).build();
            PartInfo partInfo = PartInfo.builder()
                    .withHref(cid)
                    .withPartProperties(partProperties)
                    .build();
            partInfos.add(partInfo);
        }

        return PayloadInfo.builder()
                .withPartInfo(partInfos)
                .build();
    }

    private MessageProperties createMessageProperties() {
        return MessageProperties.builder()
                .withProperty(
                        Property.builder()
                                .withName("originalSender")
                                .withValue(request.getHeader().getSender().getIdentifier())
                                .build(),
                        Property.builder()
                                .withName("finalRecipient")
                                .withValue(request.getHeader().getReceiver().getIdentifier())
                                .build())
                .build();
    }

    private PartyInfo createPartyInfo() {

        String fromName = CertificateUtils.extractCommonName(certificate);
        String toName = CertificateUtils.extractCommonName(request.getEndpoint().getCertificate());

        return PartyInfo.builder()
                .withFrom(From.builder()
                        .withPartyId(PartyId.builder()
                                .withType(PARTY_ID_TYPE)
                                .withValue(fromName)
                                .build())
                        .withRole(FROM_ROLE)
                        .build())
                .withTo(To.builder()
                        .withPartyId(PartyId.builder()
                                .withType(PARTY_ID_TYPE)
                                .withValue(toName)
                                .build())
                        .withRole(TO_ROLE)
                        .build()
                ).build();
    }

    private CollaborationInfo createCollaborationInfo() {
        return CollaborationInfo.builder()
                .withConversationId(newId())
                .withAction(request.getHeader().getDocumentType().getIdentifier())
                .withService(Service.builder()
                        .withType(SERVICE_TYPE)
                        .withValue(request.getHeader().getProcess().getIdentifier())
                        .build())
                .build();
    }

    private MessageInfo createMessageInfo() {
        GregorianCalendar gcal = GregorianCalendar.from(LocalDateTime.now().atZone(ZoneId.systemDefault()));
        XMLGregorianCalendar xmlDate;
        try {
            xmlDate = DatatypeFactory.newInstance().newXMLGregorianCalendar(gcal);
        } catch (DatatypeConfigurationException e) {
            throw new RuntimeException("Error getting xml date", e);
        }

        return MessageInfo.builder()
                .withMessageId(newId())
                .withTimestamp(xmlDate)
                .build();
    }

    private String newId() {
        return UUID.randomUUID().toString();
    }
}
