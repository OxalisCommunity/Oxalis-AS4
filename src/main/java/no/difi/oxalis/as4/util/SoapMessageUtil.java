package no.difi.oxalis.as4.util;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import no.difi.oxalis.as4.lang.OxalisAs4Exception;
import org.apache.cxf.attachment.AttachmentDataSource;
import org.apache.cxf.helpers.CastUtils;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.UserMessage;

import javax.mail.BodyPart;
import javax.mail.Header;
import javax.mail.internet.MimeMultipart;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;
import javax.xml.soap.*;
import java.io.InputStream;
import java.util.Collections;

@UtilityClass
public class SoapMessageUtil {

    public static SOAPMessage loadSample(String resource, String contentType) {
        return loadSample(SoapMessageUtil.class.getResourceAsStream(resource), contentType);
    }

    @SneakyThrows
    public static SOAPMessage loadSample(InputStream is, String contentType) {

        MimeMultipart multipart = new MimeMultipart(
                new AttachmentDataSource(contentType, is));

        BodyPart soapPart = multipart.getBodyPart(0);
        SOAPMessage message = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL)
                .createMessage(null, soapPart.getInputStream());

        for (int i = 1; i < multipart.getCount(); ++i) {
            BodyPart bodyPart = multipart.getBodyPart(i);
            AttachmentPart attachmentPart = message.createAttachmentPart(bodyPart.getDataHandler());

            Collections.list(CastUtils.cast(bodyPart.getAllHeaders(), Header.class))
                    .stream()
                    .filter(p -> attachmentPart.getMimeHeader(p.getName()) == null)
                    .forEach(p -> attachmentPart.addMimeHeader(p.getName(), p.getValue()));

            message.addAttachmentPart(attachmentPart);
        }

        message.saveChanges();
        return message;
    }

    public static SOAPHeaderElement addResponseHeader(SOAPMessage message, QName qname) throws OxalisAs4Exception {
        try {
            SOAPHeaderElement messagingHeader = message.getSOAPHeader().addHeaderElement(qname);
            messagingHeader.setMustUnderstand(true);
            return messagingHeader;
        } catch (SOAPException e) {
            throw new OxalisAs4Exception("Could not create SOAP header", e);
        }
    }

    private static SOAPMessage getSOAPMessage() throws OxalisAs4Exception {
        try {
            return createMessageFactory().createMessage();
        } catch (SOAPException e) {
            throw new OxalisAs4Exception("Could not create SOAP message", e);
        }
    }

    private static MessageFactory createMessageFactory() throws SOAPException {
        return MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL);
    }

    private static <T> void marshall(QName qname, Class<T> clazz, T t, Node node) throws OxalisAs4Exception {
        try {
            getMarshaller().marshal(new JAXBElement<>(qname, clazz, t), node);
        } catch (JAXBException e) {
            throw new OxalisAs4Exception("Could not marshal signal message to header", e);
        }
    }

    private static Marshaller getMarshaller() throws JAXBException {
        return Marshalling.getInstance().createMarshaller();
    }

    public static SOAPMessage createSoapMessage(UserMessage userMessage) throws OxalisAs4Exception {
        SOAPMessage message = getSOAPMessage();
        SOAPHeaderElement messagingHeader = addResponseHeader(message, Constants.MESSAGING_QNAME);
        marshall(Constants.USER_MESSAGE_QNAME, UserMessage.class, userMessage, messagingHeader);
        return message;
    }
}
