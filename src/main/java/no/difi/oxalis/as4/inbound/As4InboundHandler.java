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
import no.difi.oxalis.as4.lang.OxalisAs4Exception;
import no.difi.oxalis.as4.util.*;
import no.difi.oxalis.commons.header.SbdhHeaderParser;
import no.difi.oxalis.commons.io.PeekingInputStream;
import no.difi.oxalis.commons.io.UnclosableInputStream;
import no.difi.vefa.peppol.common.code.DigestMethod;
import no.difi.vefa.peppol.common.model.Digest;
import no.difi.vefa.peppol.common.model.Header;
import no.difi.vefa.peppol.common.model.ParticipantIdentifier;
import no.difi.vefa.peppol.common.model.TransportProfile;
import no.difi.vefa.peppol.sbdh.SbdReader;
import no.difi.vefa.peppol.sbdh.lang.SbdhException;
import org.apache.cxf.BusFactory;
import org.apache.cxf.attachment.AttachmentUtil;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.helpers.XPathUtils;
import org.apache.cxf.message.Attachment;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.apache.cxf.ws.policy.AssertionInfoMap;
import org.apache.cxf.ws.policy.PolicyBuilder;
import org.apache.neethi.Policy;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.*;
import org.w3.xmldsig.ReferenceType;

import javax.xml.soap.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;

@Slf4j
@Singleton
public class As4InboundHandler {

    private static final String REQUIRED_PAYLOAD_HREF_PREFIX = "cid:";

    private final TransmissionVerifier transmissionVerifier;
    private final PersisterHandler persisterHandler;
    private final TimestampProvider timestampProvider;
    private final HeaderParser headerParser;
    private final As4MessageFactory as4MessageFactory;

    @Inject
    public As4InboundHandler(TransmissionVerifier transmissionVerifier, PersisterHandler persisterHandler, TimestampProvider timestampProvider, HeaderParser headerParser, As4MessageFactory as4MessageFactory) {
        this.transmissionVerifier = transmissionVerifier;
        this.persisterHandler = persisterHandler;
        this.timestampProvider = timestampProvider;
        this.headerParser = headerParser;
        this.as4MessageFactory = as4MessageFactory;
    }


    public SOAPMessage handle(SOAPMessage request) throws OxalisAs4Exception {

        SOAPHeader soapHeader = getSoapHeader(request);
        Timestamp timestamp = getTimestamp(soapHeader);
        Iterator<AttachmentPart> attachments = CastUtils.cast(request.getAttachments());

        // Organize input data
        UserMessage userMessage = SOAPHeaderParser.getUserMessage(soapHeader);

        As4EnvelopeHeader envelopeHeader = parseAs4EnvelopeHeader(userMessage);




        TransmissionIdentifier messageId = TransmissionIdentifier.of(envelopeHeader.getMessageId());

        validateMessageId(messageId.getIdentifier()); // Validate UserMessage
        validatePayloads(userMessage.getPayloadInfo()); // Validate Payloads


        List<ReferenceType> referenceList = SOAPHeaderParser.getReferenceListFromSignedInfo(soapHeader);
        ProsessingContext prosessingContext = new ProsessingContext(timestamp, referenceList);

        // Prepare response
        SOAPMessage response = as4MessageFactory.createReceiptMessage(userMessage, prosessingContext);

        if( ! isPingMessage(Optional.of(userMessage)) ) {

            // Inform Backend

            // Take a copy of the response so that we can persist it as metadata/proof
            byte[] copyOfReceipt = copyReceipt(response);


            // Handle payload
            LinkedHashMap<InputStream, As4PayloadHeader> payloads = parseAttachments(attachments, userMessage);

            List<Path> paths = new ArrayList<>();
            for (Map.Entry<InputStream, As4PayloadHeader> paylaod : payloads.entrySet()) {

                validateAttachmentHeader(paylaod.getValue());

                // Persist payload
                paths.add(persistPayload(paylaod.getKey(), paylaod.getValue(), messageId));
            }


            // Persist Metadata
            As4PayloadHeader firstHeader = payloads.entrySet().iterator().next().getValue();
            String firstAttachmentId = envelopeHeader.getPayloadCIDs().get(0);
            Digest firstAttachmentDigest = Digest.of(DigestMethod.SHA256, SOAPHeaderParser.getAttachmentDigest(firstAttachmentId, soapHeader));
            X509Certificate senderCertificate = extractSenderCertificate(soapHeader);

            As4InboundMetadata as4InboundMetadata = new As4InboundMetadata(
                    messageId,
                    userMessage.getCollaborationInfo().getConversationId(),
                    firstHeader,
                    timestamp,
                    TransportProfile.AS4,
                    firstAttachmentDigest,
                    senderCertificate,
                    copyOfReceipt,
                    envelopeHeader);

            try {
                persisterHandler.persist(as4InboundMetadata, paths.get(0));
            } catch (IOException e) {
                throw new OxalisAs4Exception("Error persisting AS4 metadata", e, AS4ErrorCode.EBMS_0202);
            }
        }

        // Send response


        try {



            InputStream policyStream = getClass().getResourceAsStream("/policy.xml");

            PolicyBuilder builder = BusFactory.getDefaultBus().getExtension(org.apache.cxf.ws.policy.PolicyBuilder.class);
            Policy policy = builder.getPolicy(policyStream);

            response.setProperty(AssertionInfoMap.class.getName(), new AssertionInfoMap(policy));
            response.saveChanges();


        }catch (Exception e){
            e.printStackTrace();
        }

        return response;
    }

    private boolean isPingMessage(Optional<UserMessage> userMessage) {

        return userMessage
                .map(UserMessage::getCollaborationInfo)
                .map(CollaborationInfo::getService)
                .map(Service::getValue)
                .map(
                        service -> userMessage
                                .map(UserMessage::getCollaborationInfo)
                                .map(CollaborationInfo::getAction)
                                .map(action ->
                                        Constants.TEST_SERVICE.equals(service) && Constants.TEST_ACTION.equals(action)
                                ).orElse(false)
                ).orElse(false);

    }

    public static void validatePayloads(PayloadInfo payloadInfo) throws OxalisAs4Exception{
        List<String> externalPayloads = payloadInfo.getPartInfo().stream()
                .map( PartInfo::getHref )
                .filter( href -> !href.startsWith(REQUIRED_PAYLOAD_HREF_PREFIX) )
                .collect( Collectors.toList() );

        if(!externalPayloads.isEmpty()){
            String errorMessage = "Invalid PayloadInfo. Href(s) detected with \"external\" source: " + externalPayloads;
            log.debug(errorMessage);

            throw new OxalisAs4Exception(
                    errorMessage,
                    AS4ErrorCode.EBMS_0009
            );
        }


        List<String> payloadsWithInvalidCharset = payloadInfo.getPartInfo().stream()
                .filter(As4InboundHandler::partInfoHasInvalidCharset)
                .map(PartInfo::getHref)
                .collect(Collectors.toList());

        if(!payloadsWithInvalidCharset.isEmpty()){
            String errorMessage = "Invalid PayloadInfo. Part(s) detected invalid \"CharacterSet\" header: " + payloadsWithInvalidCharset;
            log.debug(errorMessage);

            throw new OxalisAs4Exception(
                    errorMessage,
                    AS4ErrorCode.EBMS_0009
            );
        }



        List<String> payloadsMissingMimeTypeHeader = payloadInfo.getPartInfo().stream()
                .filter(As4InboundHandler::partInfoMissingMimeTypeHeader)
                .map(PartInfo::getHref)
                .collect(Collectors.toList());

        if(!payloadsMissingMimeTypeHeader.isEmpty()){
            String errorMessage = "Invalid PayloadInfo. Part(s) detected without \"MimeType\" header: " + payloadsMissingMimeTypeHeader;
            log.debug(errorMessage);

            throw new OxalisAs4Exception(
                    errorMessage,
                    AS4ErrorCode.EBMS_0009
            );
        }

    }

    private static boolean partInfoHasInvalidCharset(PartInfo partInfo) {

         return Optional.ofNullable(partInfo)

                .map(PartInfo::getPartProperties)
                .map(PartProperties::getProperty)

                .map(Collection::stream).orElse(Stream.empty())

                 .anyMatch(property ->
                         Optional.of(property)
                                 .map(Property::getName)
                                 .filter("CharacterSet"::equals)
                                 .map(fieldName -> Optional.of(property)
                                         .map(Property::getValue)
                                         .map(charset -> {
                                             try {
                                                 return null == Charset.forName(property.getValue());
                                             } catch (Exception e) {
                                                 return true;
                                             }
                                         }).orElse(true)
                                 ).orElse(false)

                 );


    }


    public static boolean partInfoMissingMimeTypeHeader(PartInfo partInfo){

        return ! Optional.ofNullable(partInfo)

                .map(PartInfo::getPartProperties)
                .map(PartProperties::getProperty)

                .map(Collection::stream).orElse(Stream.empty())

                .map(Property::getName)
                .anyMatch("MimeType"::equals);
    }


    public byte[] copyReceipt(SOAPMessage response) throws OxalisAs4Exception{

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        try {
            response.writeTo(bos);
        } catch (SOAPException | IOException e) {
            throw new OxalisAs4Exception("Could not write SOAP response", e, AS4ErrorCode.EBMS_0202);
        }

        return bos.toByteArray();
    }

    public void validateMessageId(String messageId) throws OxalisAs4Exception{

        if (!MessageIdUtil.verify(messageId)) {
            throw new OxalisAs4Exception(
                    "Invalid Message-ID '" + messageId + "' in inbound message.",
                    AS4ErrorCode.EBMS_0009
            );
        }

    }

    private LinkedHashMap<InputStream, As4PayloadHeader> parseAttachments(Iterator<AttachmentPart> attachments, UserMessage userMessage) throws  OxalisAs4Exception{

        if ( !attachments.hasNext() ) {
            throw new OxalisAs4Exception("No attachment(s) present");
        }

        // <ContentId, <HeaderName, MimeHeader>>
        Map<String, Map<String, MimeHeader>> partInfoHeadersMap = userMessage.getPayloadInfo().getPartInfo().stream()
                .collect(
                    Collectors.toMap(
                        partInfo -> AttachmentUtil.cleanContentId( partInfo.getHref() ),
                        partInfo -> partInfo.getPartProperties().getProperty().stream().collect(
                            Collectors.toMap(
                                Property::getName,
                                property -> new MimeHeader( property.getName(), property.getValue() )
                            )
                        )
                    )
                );

        LinkedHashMap<InputStream, As4PayloadHeader> payloads = new LinkedHashMap<>();

        Collection<Attachment> s = PhaseInterceptorChain.getCurrentMessage().getAttachments();

//        while( attachments.hasNext() ){
        for(Attachment attachment : s){
            try {

//                AttachmentPart attachmentPart = attachments.next();
                InputStream is  = attachment.getDataHandler().getInputStream();
                String contentId = AttachmentUtil.cleanContentId(attachment.getId());

                Map<String, MimeHeader> mimeHeaders = new HashMap<>();
//                Iterator<MimeHeader> mimeHeaderIterator = attachmentPart.getAllMimeHeaders();
//                mimeHeaderIterator.forEachRemaining(m -> mimeHeaders.put(m.getName(), m));
                attachment.getHeaderNames().forEachRemaining(h -> mimeHeaders.put(h, new MimeHeader(h, attachment.getHeader(h))));


                Map<String, MimeHeader> partInfoHeaders = partInfoHeadersMap.get(contentId);


                if( isAttachmentCoompressed(partInfoHeaders, mimeHeaders) ){
                    try {
                        is = new GZIPInputStream(is);
                    }catch (IOException e){
                        throw new OxalisAs4Exception(
                                "Unable to initiate decompression of payload with Content-ID: " + contentId,
                                e,
                                AS4ErrorCode.EBMS_0303,
                                AS4ErrorCode.Severity.FAILURE
                        );
                    }
                }



                Header sbdh;
                if(headerParser instanceof SbdhHeaderParser) {

                    try {
                        PeekingInputStream pis = new PeekingInputStream(is);

                        try (SbdReader sbdReader = SbdReader.newInstance(pis)) {

                            sbdh = sbdReader.getHeader();
                            is = pis.newInputStream();

                        }
                    }catch (SbdhException | IOException e){

                        launderZipException(contentId, e);
                        throw new OxalisAs4Exception("Could not extract SBDH from payload");

                    }
                }else{
                    sbdh = new Header();
                    //TODO: populate based on userMessage
                    sbdh = sbdh.sender(ParticipantIdentifier.of(userMessage.getPartyInfo().getFrom().getPartyId().get(0).getValue()));
                }

                // Get an "unexpected eof in prolog"
                As4PayloadHeader header = new As4PayloadHeader(sbdh, partInfoHeaders.values(), contentId, userMessage.getMessageInfo().getMessageId());

                // Extract "fresh" InputStream
                payloads.put(is, header);

            } catch ( IOException  e ) {
                throw new OxalisAs4Exception("Could not get attachment input stream", e);
            }
        }

        return payloads;
    }

    private void launderZipException(String contentId, Exception e) throws OxalisAs4Exception {
        Throwable cause = e;
        for(int i = 0; i < 10 && cause != null; i++){
            if(cause instanceof ZipException){
                throw new OxalisAs4Exception(
                        "Unable to decompress of payload with Content-ID: " + contentId,
                        cause,
                        AS4ErrorCode.EBMS_0303,
                        AS4ErrorCode.Severity.FAILURE
                );
            }

            cause = e.getCause();
        }
    }

    private boolean isAttachmentCoompressed(Map<String, MimeHeader> partInfoHeaders, Map<String, MimeHeader> mimeHeaders) {
        if ( partInfoHeaders.containsKey("CompressionType") ){
            String value = partInfoHeaders.get("CompressionType").getValue();

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
        ns.put("secutil", "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd");
        ns.put("xmldsig", "http://www.w3.org/2000/09/xmldsig#");
        XPathUtils xu = new XPathUtils(ns);

        // Thanks to 'tjeb' for good info in PR #28
        String cert = xu.getValueString("//wsse:BinarySecurityToken[@secutil:Id=substring-after(//xmldsig:Signature/xmldsig:KeyInfo/wsse:SecurityTokenReference/wsse:Reference[1]/@URI, '#')]", header);
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

    private Path persistPayload(InputStream inputStream, As4PayloadHeader as4PayloadHeader, TransmissionIdentifier ti) throws OxalisAs4Exception {

        Path payloadPath;
        try {

            // Persist content
            payloadPath = persisterHandler.persist(ti, as4PayloadHeader,
                    new UnclosableInputStream(inputStream));

            // Exhaust InputStream
            ByteStreams.exhaust(inputStream);
        } catch (IOException e) {
            launderZipException(as4PayloadHeader.getCid(), e);
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
