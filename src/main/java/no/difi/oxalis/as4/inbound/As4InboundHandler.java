package no.difi.oxalis.as4.inbound;

import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import no.difi.oxalis.api.lang.TimestampException;
import no.difi.oxalis.api.lang.VerifierException;
import no.difi.oxalis.api.model.Direction;
import no.difi.oxalis.api.model.TransmissionIdentifier;
import no.difi.oxalis.api.persist.PersisterHandler;
import no.difi.oxalis.api.timestamp.Timestamp;
import no.difi.oxalis.api.timestamp.TimestampProvider;
import no.difi.oxalis.api.transmission.TransmissionVerifier;
import no.difi.oxalis.as4.api.MessageIdGenerator;
import no.difi.oxalis.as4.lang.OxalisAs4Exception;
import no.difi.oxalis.as4.util.Constants;
import no.difi.oxalis.as4.util.Marshalling;
import no.difi.oxalis.as4.util.MessageIdUtil;
import no.difi.oxalis.as4.util.SOAPHeaderParser;
import no.difi.oxalis.commons.io.PeekingInputStream;
import no.difi.oxalis.commons.io.UnclosableInputStream;
import no.difi.vefa.peppol.common.code.DigestMethod;
import no.difi.vefa.peppol.common.model.Digest;
import no.difi.vefa.peppol.common.model.Header;
import no.difi.vefa.peppol.common.model.TransportProfile;
import no.difi.vefa.peppol.sbdh.SbdReader;
import no.difi.vefa.peppol.sbdh.lang.SbdhException;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.helpers.XPathUtils;
import org.oasis_open.docs.ebxml_bp.ebbp_signals_2.MessagePartNRInformation;
import org.oasis_open.docs.ebxml_bp.ebbp_signals_2.NonRepudiationInformation;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.MessageInfo;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Receipt;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.SignalMessage;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.UserMessage;
import org.w3.xmldsig.ReferenceType;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.soap.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

@Singleton
public class As4InboundHandler {

    private final TransmissionVerifier transmissionVerifier;
    private final PersisterHandler persisterHandler;
    private final TimestampProvider timestampProvider;
    private final MessageIdGenerator messageIdGenerator;

    @Inject
    public As4InboundHandler(TransmissionVerifier transmissionVerifier, PersisterHandler persisterHandler, TimestampProvider timestampProvider, MessageIdGenerator messageIdGenerator) {
        this.transmissionVerifier = transmissionVerifier;
        this.persisterHandler = persisterHandler;
        this.timestampProvider = timestampProvider;
        this.messageIdGenerator = messageIdGenerator;
    }

    public SOAPMessage handle(SOAPMessage request) throws OxalisAs4Exception {
        SOAPHeader header = getSoapHeader(request);

        // Prepare content for reading of SBDH
        PeekingInputStream peekingInputStream = getAttachmentStream(request);

        // Extract SBDH
        Header sbdh = getSbdh(peekingInputStream);

        // Validate SBDH
        validateSBDH(sbdh);

        UserMessage userMessage = SOAPHeaderParser.getUserMessage(header);
        String messageId = userMessage.getMessageInfo().getMessageId();

        if (!MessageIdUtil.verify(messageId))
            throw new OxalisAs4Exception(
                    "Invalid Message-ID '" + messageId + "' in inbound message.");

        TransmissionIdentifier ti = TransmissionIdentifier.of(messageId);

        Path payloadPath = persistPayload(peekingInputStream, sbdh, ti);

        // Timestamp
        Timestamp ts = getTimestamp(header);

        if (userMessage.getPayloadInfo().getPartInfo().size() != 1) {
            throw new OxalisAs4Exception("Should only be one PartInfo in header");
        }

        String refId = userMessage.getPayloadInfo().getPartInfo().get(0).getHref();
        // Get attachment digest from header
        Digest attachmentDigest = Digest.of(DigestMethod.SHA256, SOAPHeaderParser.getAttachmentDigest(refId, header));

        X509Certificate senderCertificate = extractSenderCertificate(header);

        // Get reference list
        List<ReferenceType> referenceList = SOAPHeaderParser.getReferenceListFromSignedInfo(header);

        SOAPMessage response = createSOAPResponse(ts, messageId, referenceList);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            response.writeTo(bos);
        } catch (SOAPException | IOException e) {
            throw new OxalisAs4Exception("Could not write SOAP response", e);
        }

        As4InboundMetadata as4InboundMetadata = new As4InboundMetadata(
                ti,
                sbdh,
                ts,
                TransportProfile.AS4,
                attachmentDigest,
                senderCertificate,
                bos.toByteArray()
        );

        try {
            persisterHandler.persist(as4InboundMetadata, payloadPath);
        } catch (IOException e) {
            throw new OxalisAs4Exception("Error persisting AS4 metadata", e);
        }

        return response;
    }

    private X509Certificate extractSenderCertificate(SOAPHeader header) throws OxalisAs4Exception {
        Map<String, String> ns = new TreeMap<>();
        ns.put("wsse", "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd");
        XPathUtils xu = new XPathUtils(ns);
        String cert = xu.getValueString("//wsse:BinarySecurityToken[1]/text()", header);

        if (cert == null) {
            throw new OxalisAs4Exception("Unable to locate sender certificate");
        }

        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            byte[] certBytes = Base64.getDecoder().decode(cert.replaceAll("\r|\n", ""));
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certBytes));
        } catch (CertificateException e) {
            throw new OxalisAs4Exception("Unable to parse sender certificate", e);
        }
    }

    private SOAPMessage createSOAPResponse(Timestamp ts,
                                           String refToMessageId,
                                           List<ReferenceType> referenceList) throws OxalisAs4Exception {
        SignalMessage signalMessage;
        SOAPHeaderElement messagingHeader;
        SOAPMessage message;
        try {

            MessageFactory messageFactory = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL);
            message = messageFactory.createMessage();

            SOAPHeader soapHeader = message.getSOAPHeader();

            messagingHeader = soapHeader.addHeaderElement(Constants.MESSAGING_QNAME);
            messagingHeader.setMustUnderstand(true);

        } catch (SOAPException e) {
            throw new OxalisAs4Exception("Could not create SOAP message", e);
        }

        GregorianCalendar gc = new GregorianCalendar();
        gc.setTime(ts.getDate());

        XMLGregorianCalendar xmlGc;
        try {
            xmlGc = DatatypeFactory.newInstance().newXMLGregorianCalendar(gc);
        } catch (DatatypeConfigurationException e) {
            throw new OxalisAs4Exception("Could not parse timestamp", e);
        }

        // Generate Message-Id
        String messageId = messageIdGenerator.generate();

        if (!MessageIdUtil.verify(messageId))
            throw new OxalisAs4Exception(
                    "Invalid Message-ID '" + messageId + "' generated.");

        MessageInfo messageInfo = MessageInfo.builder()
                .withTimestamp(xmlGc)
                .withMessageId(messageId)
                .withRefToMessageId(refToMessageId)
                .build();

        List<MessagePartNRInformation> mpList = referenceList.stream()
                .map(r -> MessagePartNRInformation.builder().withReference(r).build())
                .collect(Collectors.toList());

        NonRepudiationInformation nri = NonRepudiationInformation.builder()
                .addMessagePartNRInformation(mpList)
                .build();

        signalMessage = SignalMessage.builder()
                .withMessageInfo(messageInfo)
                .withReceipt(Receipt.builder().withAny(nri).build())
                .build();

        JAXBElement<SignalMessage> userMessageJAXBElement = new JAXBElement<>(Constants.SIGNAL_MESSAGE_QNAME,
                (Class<SignalMessage>) signalMessage.getClass(), signalMessage);
        try {
            Marshaller marshaller = Marshalling.getInstance().getJaxbContext().createMarshaller();
            marshaller.marshal(userMessageJAXBElement, messagingHeader);
        } catch (JAXBException e) {
            throw new OxalisAs4Exception("Could not marshal signal message to header", e);
        }

        return message;
    }

    private Timestamp getTimestamp(SOAPHeader header) throws OxalisAs4Exception {
        Timestamp ts;
        byte[] signature = SOAPHeaderParser.getSignature(header);
        try {

            ts = timestampProvider.generate(signature, Direction.IN);

        } catch (TimestampException e) {

            throw new OxalisAs4Exception("Error generating timestamp", e);
        }
        return ts;
    }

    private Path persistPayload(PeekingInputStream peekingInputStream, Header sbdh, TransmissionIdentifier ti) throws OxalisAs4Exception {
        // Extract "fresh" InputStream
        Path payloadPath;
        try (InputStream payloadInputStream = peekingInputStream.newInputStream()) {

            // Persist content
            payloadPath = persisterHandler.persist(ti, sbdh,
                    new UnclosableInputStream(payloadInputStream));

            // Exhaust InputStream
            ByteStreams.exhaust(payloadInputStream);
        } catch (IOException e) {
            throw new OxalisAs4Exception("Error processing payload input stream", e);
        }
        return payloadPath;
    }

    private void validateSBDH(Header sbdh) throws OxalisAs4Exception {
        try {
            transmissionVerifier.verify(sbdh, Direction.IN);
        } catch (VerifierException e) {
            throw new OxalisAs4Exception("Error verifying SBDH", e);
        }
    }

    private Header getSbdh(InputStream is) throws OxalisAs4Exception {
        Header sbdh;

        try (SbdReader sbdReader = SbdReader.newInstance(is)) {
            sbdh = sbdReader.getHeader();
        } catch (SbdhException | IOException e) {
            throw new OxalisAs4Exception("Could not extract SBDH from payload");
        }

        return sbdh;
    }

    private PeekingInputStream getAttachmentStream(SOAPMessage request) throws OxalisAs4Exception {

        Iterator<AttachmentPart> attachments = CastUtils.cast(request.getAttachments());
        // Should only be one attachment?
        if (!attachments.hasNext()) {
            throw new OxalisAs4Exception("No attachment present");
        }

        InputStream attachmentStream;
        try {
            AttachmentPart attachmentPart = attachments.next();

            attachmentStream = attachmentPart.getDataHandler().getInputStream();

        } catch (IOException | SOAPException e) {
            throw new OxalisAs4Exception("Could not get attachment input stream", e);
        }

        PeekingInputStream peekingInputStream;
        try {
            peekingInputStream = new PeekingInputStream(new GZIPInputStream(attachmentStream));
        } catch (IOException e) {
            throw new OxalisAs4Exception("Could not obtain attachment inputStream", e);
        }
        return peekingInputStream;
    }

    private SOAPHeader getSoapHeader(SOAPMessage request) throws OxalisAs4Exception {
        SOAPHeader header;
        try {
            header = request.getSOAPHeader();
        } catch (SOAPException e) {
            throw new OxalisAs4Exception("Could not get SOAP header", e);
        }
        return header;
    }

}
