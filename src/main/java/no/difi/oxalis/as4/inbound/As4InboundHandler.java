package no.difi.oxalis.as4.inbound;

import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import no.difi.oxalis.api.header.HeaderParser;
import no.difi.oxalis.api.lang.OxalisContentException;
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
import no.difi.oxalis.commons.header.SbdhHeaderParser;
import no.difi.oxalis.commons.io.PeekingInputStream;
import no.difi.oxalis.commons.io.UnclosableInputStream;
import no.difi.vefa.peppol.common.code.DigestMethod;
import no.difi.vefa.peppol.common.model.Digest;
import no.difi.vefa.peppol.common.model.Header;
import no.difi.vefa.peppol.common.model.TransportProfile;
import no.difi.vefa.peppol.sbdh.SbdReader;
import no.difi.vefa.peppol.sbdh.lang.SbdhException;
import org.apache.commons.io.IOUtils;
import org.apache.cxf.attachment.AttachmentUtil;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.helpers.XPathUtils;
import org.oasis_open.docs.ebxml_bp.ebbp_signals_2.MessagePartNRInformation;
import org.oasis_open.docs.ebxml_bp.ebbp_signals_2.NonRepudiationInformation;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.*;
import org.w3.xmldsig.ReferenceType;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.soap.*;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

@Slf4j
@Singleton
public class As4InboundHandler {

    private final TransmissionVerifier transmissionVerifier;
    private final PersisterHandler persisterHandler;
    private final TimestampProvider timestampProvider;
    private final MessageIdGenerator messageIdGenerator;
    private final HeaderParser headerParser;

    @Inject
    public As4InboundHandler(TransmissionVerifier transmissionVerifier, PersisterHandler persisterHandler, TimestampProvider timestampProvider, MessageIdGenerator messageIdGenerator, HeaderParser headerParser) {
        this.transmissionVerifier = transmissionVerifier;
        this.persisterHandler = persisterHandler;
        this.timestampProvider = timestampProvider;
        this.messageIdGenerator = messageIdGenerator;
        this.headerParser = headerParser;
    }

    public SOAPMessage handle(SOAPMessage request) throws OxalisAs4Exception {

        // Organize input data
        SOAPHeader soapHeader = getSoapHeader(request);
        UserMessage userMessage = SOAPHeaderParser.getUserMessage(soapHeader);
        As4EnvelopeHeader envelopeHeader = parseAs4EnvelopeHeader(userMessage);
        TransmissionIdentifier ti = TransmissionIdentifier.of(envelopeHeader.getMessageId());

        validateMessageId(envelopeHeader.getMessageId());

        // Prepare response
        Timestamp ts = getTimestamp(soapHeader);
        List<ReferenceType> referenceList = SOAPHeaderParser.getReferenceListFromSignedInfo(soapHeader);
        SOAPMessage response = createSOAPResponse(ts, envelopeHeader.getMessageId(), referenceList);

        // Take a copy of the response so that we can persist it as metadata/proof
        byte[] copyOfResponse = copyResponse(response);


        // Handle payload
        LinkedHashMap<InputStream, As4PayloadHeader> payloads = parseAttachments(request, userMessage);

        List<Path> paths = new ArrayList<>();
        for(Map.Entry<InputStream, As4PayloadHeader> paylaod : payloads.entrySet()){

            validateAttachmentHeader(paylaod.getValue());

            // Persist payload
            paths.add(persistPayload(paylaod.getKey(), paylaod.getValue(), ti));
        }


        // Persist Metadata
        As4PayloadHeader firstHeader = payloads.entrySet().iterator().next().getValue();
        String firstAttachmentId = envelopeHeader.getPayloadCIDs().get(0);
        Digest firstAttachmentDigest = Digest.of(DigestMethod.SHA256, SOAPHeaderParser.getAttachmentDigest(firstAttachmentId, soapHeader));
        X509Certificate senderCertificate = extractSenderCertificate(soapHeader);

        As4InboundMetadata as4InboundMetadata = new As4InboundMetadata(
                ti,
                userMessage.getCollaborationInfo().getConversationId(),
                firstHeader,
                ts,
                TransportProfile.AS4,
                firstAttachmentDigest,
                senderCertificate,
                copyOfResponse,
                envelopeHeader);

        try {
            persisterHandler.persist(as4InboundMetadata, paths.get(0));
        } catch (IOException e) {
            throw new OxalisAs4Exception("Error persisting AS4 metadata", e);
        }

        // Send response
        return response;
    }

    public byte[] copyResponse(SOAPMessage response) throws OxalisAs4Exception{

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        try {
            response.writeTo(bos);
        } catch (SOAPException | IOException e) {
            throw new OxalisAs4Exception("Could not write SOAP response", e);
        }

        return bos.toByteArray();
    }

    public void validateMessageId(String messageId) throws OxalisAs4Exception{

        if (!MessageIdUtil.verify(messageId))
            throw new OxalisAs4Exception(
                    "Invalid Message-ID '" + messageId + "' in inbound message.");
    }

    private LinkedHashMap<InputStream, As4PayloadHeader> parseAttachments(SOAPMessage request, UserMessage userMessage) throws  OxalisAs4Exception{

        Iterator<AttachmentPart> attachments = CastUtils.cast(request.getAttachments());

        if ( !attachments.hasNext() ) {
            throw new OxalisAs4Exception("No attachment(s) present");
        }

        // <ContentId, <HeaderName, MimeHeader>>
        Map<String, Map<String, MimeHeader>> partInfoHeaders = userMessage.getPayloadInfo().getPartInfo().stream().collect(
                Collectors.toMap(
                        partInfo -> AttachmentUtil.cleanContentId(partInfo.getHref()),
                        partInfo -> partInfo.getPartProperties().getProperty().stream().collect(
                                Collectors.toMap(
                                        Property::getName,
                                        property -> new MimeHeader(property.getName(), property.getValue()))
                        )
                )
        );

        log.info("partInfoHeaders: {}", partInfoHeaders);

        LinkedHashMap<InputStream, As4PayloadHeader> payloads = new LinkedHashMap<>();

        while( attachments.hasNext() ){

            try {

                AttachmentPart attachmentPart = attachments.next();
                InputStream is  = attachmentPart.getDataHandler().getInputStream();
                String contentId = attachmentPart.getContentId();

                Map<String, MimeHeader> mimeHeaders = new HashMap<>();
                Iterator<MimeHeader> mimeHeaderIterator = attachmentPart.getAllMimeHeaders();
                mimeHeaderIterator.forEachRemaining(m -> mimeHeaders.put(m.getName(), m));

                log.info("MimeHeaders: {}", mimeHeaders);


                if( isAttachmentCoompressed(partInfoHeaders.get(contentId), mimeHeaders) ){
                    log.info("Decompressing payload: {}", contentId);
                    is = new GZIPInputStream(is);
                }


                PeekingInputStream pis = new PeekingInputStream(is);

//                BufferedInputStream bis = new BufferedInputStream(is);
//                bis.mark(5_000_000);

                Header sbdh;
                if(headerParser instanceof SbdhHeaderParser) {
                    try (SbdReader sbdReader = SbdReader.newInstance(pis)) {
                        sbdh = sbdReader.getHeader();
                    } catch (SbdhException | IOException e) {
                        throw new OxalisAs4Exception("Could not extract SBDH from payload");
                    }
                }else{
                    sbdh = new Header();
                }

//               sbdh = parseAttachmentHeader(pis)

                // Get an "unexpected eof in prolog"
                As4PayloadHeader header = new As4PayloadHeader(sbdh, mimeHeaders.values(), contentId);

//                bis.reset();
//                payloads.put(bis, header);

                payloads.put(pis.newInputStream(), header);

            } catch ( IOException | SOAPException e ) {

                throw new OxalisAs4Exception("Could not get attachment input stream", e);
            }
        }

        return payloads;
    }

    private boolean isAttachmentCoompressed(Map<String, MimeHeader> partInfoHeaders, Map<String, MimeHeader> mimeHeaders) {
        if ( partInfoHeaders.containsKey("CompressionType") ){
            String value = partInfoHeaders.get("CompressionType").getValue();


            log.info("header value: {}", value);
            log.info("header byte: {}", Arrays.asList(value.getBytes()));
            log.info("comparison value: {}", "application/gzip");
            log.info("comparison byte: {}", Arrays.asList("application/gzip".getBytes()));

            // Somehow this fails
            if( "application/gzip".equals(value) ){
                return true;
            }
        }

        if ( mimeHeaders.containsKey("CompressionType") &&
                "application/gzip".equals(mimeHeaders.get("CompressionType").getValue()) ){
            return true;
        }

        return false;
    }

    private As4EnvelopeHeader parseAs4EnvelopeHeader(UserMessage userMessage) {

        As4EnvelopeHeader as4EnvelopeHeader = new As4EnvelopeHeader();

        as4EnvelopeHeader.setMessageId(userMessage.getMessageInfo().getMessageId());
        as4EnvelopeHeader.setConversationId(userMessage.getCollaborationInfo().getConversationId());

        as4EnvelopeHeader.setFromPartyId(userMessage.getPartyInfo().getFrom().getPartyId().stream().map(PartyId::getValue).collect(Collectors.toList()));
        as4EnvelopeHeader.setFromPartyRole(userMessage.getPartyInfo().getFrom().getRole());

        as4EnvelopeHeader.setToPartyId(userMessage.getPartyInfo().getTo().getPartyId().stream().map(PartyId::getValue).collect(Collectors.toList()));
        as4EnvelopeHeader.setToPartyRole(userMessage.getPartyInfo().getTo().getRole());

        as4EnvelopeHeader.setAction(userMessage.getCollaborationInfo().getAction());
        as4EnvelopeHeader.setService(userMessage.getCollaborationInfo().getService().getValue());

        as4EnvelopeHeader.setMessageProperties(userMessage.getMessageProperties().getProperty().stream().collect(Collectors.toMap(Property::getName, Property::getValue)));

        as4EnvelopeHeader.setPayloadCIDs(userMessage.getPayloadInfo().getPartInfo().stream().map(PartInfo::getHref).collect(Collectors.toList()));

        return as4EnvelopeHeader;
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
        } catch ( CertificateException e ) {
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

    private Path persistPayload(InputStream inputStream, Header sbdh, TransmissionIdentifier ti) throws OxalisAs4Exception {
        // Extract "fresh" InputStream
        Path payloadPath;
        try {

            // Persist content
            payloadPath = persisterHandler.persist(ti, sbdh,
                    new UnclosableInputStream(inputStream));

            // Exhaust InputStream
            ByteStreams.exhaust(inputStream);
        } catch (IOException e) {
            throw new OxalisAs4Exception("Error processing payload input stream", e);
        }
        return payloadPath;
    }

    private void validateAttachmentHeader(Header attachmentHeader) throws OxalisAs4Exception {
        try {
            transmissionVerifier.verify(attachmentHeader, Direction.IN);
        } catch (VerifierException e) {
            throw new OxalisAs4Exception("Error verifying SBDH", e);
        }
    }

    private Header parseAttachmentHeader(InputStream is) throws OxalisAs4Exception {
        Header header;

        try {
            header = headerParser.parse(is);
        } catch (OxalisContentException e) {
            throw new OxalisAs4Exception("Could not extract SBDH from payload", e);
        }

        return header;
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
