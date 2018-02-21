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
import no.difi.oxalis.as4.lang.OxalisAs4Exception;
import no.difi.oxalis.as4.util.Marshalling;
import no.difi.oxalis.as4.util.SecurityHeaderParser;
import no.difi.oxalis.commons.io.PeekingInputStream;
import no.difi.oxalis.commons.io.UnclosableInputStream;
import no.difi.vefa.peppol.common.code.DigestMethod;
import no.difi.vefa.peppol.common.model.Digest;
import no.difi.vefa.peppol.common.model.Header;
import no.difi.vefa.peppol.common.model.TransportProfile;
import no.difi.vefa.peppol.sbdh.SbdReader;
import no.difi.vefa.peppol.sbdh.lang.SbdhException;
import org.apache.cxf.helpers.CastUtils;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Messaging;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.UserMessage;
import org.w3c.dom.Node;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.soap.AttachmentPart;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPMessage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.Iterator;

@Singleton
public class As4InboundHandler {

    @Inject
    private TransmissionVerifier transmissionVerifier;

    @Inject
    private PersisterHandler persisterHandler;

    @Inject
    private TimestampProvider timestampProvider;

    public SOAPMessage handle(SOAPMessage request) throws OxalisAs4Exception {
        SOAPHeader header = getSoapHeader(request);

        // Prepare content for reading of SBDH
        PeekingInputStream peekingInputStream = getAttachmentStream(request);

        // Extract SBDH
        Header sbdh = getSbdh(peekingInputStream);

        // Validate SBDH
        validateSBDH(sbdh);

        UserMessage userMessage = getUserMessage(header);
        TransmissionIdentifier ti = TransmissionIdentifier.of(userMessage.getMessageInfo().getMessageId());

        Path payloadPath = persistPayload(peekingInputStream, sbdh, ti);

        // Extract senders certificate from header
        X509Certificate senderCertificate = SecurityHeaderParser.getSenderCertificate(header);

        // Timestamp
        Timestamp ts = getTimestamp(header);

        if (userMessage.getPayloadInfo().getPartInfo().size() != 1) {
            throw new OxalisAs4Exception("Should only be one PartInfo in header");
        }
        String refId = userMessage.getPayloadInfo().getPartInfo().get(0).getHref();
        // Get attachment digest from header
        Digest attachmentDigest = Digest.of(DigestMethod.SHA256, SecurityHeaderParser.getAttachmentDigest(refId, header));

        As4InboundMetadata as4InboundMetadata = new As4InboundMetadata(
                ti,
                sbdh,
                ts,
                TransportProfile.AS4,
                attachmentDigest, //digest
                senderCertificate,
                null //primary receipt
        );

        try {
            persisterHandler.persist(as4InboundMetadata, payloadPath);
        } catch (IOException e) {
            throw new OxalisAs4Exception("Error persisting AS4 metadata", e);
        }

        return request;
    }

    private Timestamp getTimestamp(SOAPHeader header) throws OxalisAs4Exception {
        Timestamp ts;
        byte[] signature = SecurityHeaderParser.getSignature(header);
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

    private UserMessage getUserMessage(SOAPHeader header) throws OxalisAs4Exception {
        Node messagingNode = header.getElementsByTagNameNS("*", "Messaging").item(0);
        Messaging messaging;
        Unmarshaller unmarshaller;
        try {
            unmarshaller = Marshalling.getInstance().getJaxbContext().createUnmarshaller();
            messaging = unmarshaller.unmarshal(messagingNode, Messaging.class).getValue();
        } catch (JAXBException e) {
            throw new OxalisAs4Exception("Could not unmarshal Messaging node from header");
        }
        return messaging.getUserMessage().stream()
                .findFirst()
                .orElseThrow(() -> new OxalisAs4Exception("No UserMessage present in header"));
    }

    private void validateSBDH(Header sbdh) throws OxalisAs4Exception {
        try {
            transmissionVerifier.verify(sbdh, Direction.IN);
        } catch (VerifierException e) {
            throw new OxalisAs4Exception("Error verifying SBDH", e);
        }
    }

    private Header getSbdh(PeekingInputStream peekingInputStream) throws OxalisAs4Exception {
        Header sbdh;
        try (SbdReader sbdReader = SbdReader.newInstance(peekingInputStream)) {
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
            attachmentStream = attachments.next().getDataHandler().getInputStream();
        } catch (IOException | SOAPException e) {
            throw new OxalisAs4Exception("Could not get attachment input stream", e);
        }
        PeekingInputStream peekingInputStream;
        try {
            peekingInputStream = new PeekingInputStream(attachmentStream);
        } catch (IOException e) {
            throw new OxalisAs4Exception("Could not create peeking stream from attachment", e);
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
