package no.difi.oxalis.as4.outbound;

import com.google.common.collect.Lists;
import no.difi.oxalis.as4.util.Constants;
import no.difi.oxalis.as4.util.Marshalling;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.*;
import org.springframework.http.MediaType;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.client.core.WebServiceMessageCallback;
import org.springframework.ws.mime.Attachment;
import org.springframework.ws.soap.SoapHeader;
import org.springframework.ws.soap.SoapHeaderElement;
import org.springframework.ws.soap.SoapMessage;

import javax.xml.bind.JAXBElement;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.UUID;

import static no.difi.oxalis.as4.util.Constants.*;

public class As4Sender implements WebServiceMessageCallback {

    private final InputStream payload;

    As4Sender(InputStream payload) {
        this.payload = payload;
    }

    @Override
    public void doWithMessage(WebServiceMessage webServiceMessage) throws IOException, TransformerException {
        SoapMessage message = (SoapMessage) webServiceMessage;
        message.addAttachment(newId(), () -> payload, MediaType.APPLICATION_XML_VALUE);
        addEbmsHeader(message);
        // DEBUG
//        try (FileOutputStream fos = new FileOutputStream(Instant.now().getEpochSecond()+".txt");) {
//            message.writeTo(fos);
//        }
    }

    private void addEbmsHeader(SoapMessage message) {
        SoapHeader header = message.getSoapHeader();
        SoapHeaderElement messagingHeader = header.addHeaderElement(Constants.MESSAGING_QNAME);

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
                .withProperty(Property.builder()
                        .withName("originalSender")
                        .withValue("test")
                        .build())
                .withProperty(Property.builder()
                        .withName("finalRecipient")
                        .withValue("test")
                        .build())
                .build();
    }

    private PartyInfo createPartyInfo() {
        return PartyInfo.builder()
                .withFrom(From.builder()
                        .withPartyId(PartyId.builder()
                                .withType(PARTY_ID_TYPE)
                                .withValue("?") // orgnr+landkode ??
                                .build())
                        .withRole(FROM_ROLE)
                        .build())
                .withTo(To.builder()
                        .withPartyId(PartyId.builder()
                                .withType(PARTY_ID_TYPE)
                                .withValue("?") // orgnr+landkode ??
                                .build())
                        .withRole(TO_ROLE)
                        .build()
                ).build();
    }

    private CollaborationInfo createCollaborationInfo() {
        return CollaborationInfo.builder()
                // DocumentIdentification -> InstanceIdentifier ?
                .withConversationId(newId())
                // BusinessScope -> DocumentId ?
                .withAction("urn:oasis:names:specification:ubl:schema:xsd:Invoice-2::Invoice##urn:www.cenbii.eu:transaction:biitrns010:ver2.0:extended:urn:www.peppol.eu:bis:peppol4a:ver2.0::2.1")
                // BusinessScope -> ProcessId ?
                .withService(createService())
                .build();
    }

    private Service createService() {
        return Service.builder()
                .withType("foo") // ??
                .withValue("urn:www.cenbii.eu:profile:bii04:ver1.0") // InstanceIdentifier
                .build();
    }

    private MessageInfo createMessageInfo() {
        GregorianCalendar gcal = GregorianCalendar.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()));
        XMLGregorianCalendar xmlDate;
        try {
            xmlDate = DatatypeFactory.newInstance().newXMLGregorianCalendar(gcal);
        } catch (DatatypeConfigurationException e) {
            throw new RuntimeException("Error getting xml date", e);
        }

        return MessageInfo.builder()
                .withMessageId(newId()) // newid?
                .withTimestamp(xmlDate) // CreationDateAndTime ?
                .build();
    }

    private String newId() {
        return UUID.randomUUID().toString();
    }
}
