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
import no.difi.oxalis.as4.util.SecurityCertificateExtractor;
import no.difi.oxalis.commons.io.PeekingInputStream;
import no.difi.oxalis.commons.io.UnclosableInputStream;
import no.difi.vefa.peppol.common.model.Header;
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

        SOAPHeader header;
        try {
            header = request.getSOAPHeader();
        } catch (SOAPException e) {
            throw new OxalisAs4Exception("Could not get SOAP header", e);
        }

        Iterator<AttachmentPart> attachments = CastUtils.cast(request.getAttachments());
        // Should only be one attachment?
        if (!attachments.hasNext()) {
            throw new RuntimeException("No attachment present");
        }

        InputStream attachmentStream;
        try {
            attachmentStream = attachments.next().getDataHandler().getInputStream();
        } catch (IOException | SOAPException e) {
            throw new OxalisAs4Exception("Could not get attachment input stream", e);
        }

        // Prepare content for reading of SBDH
        PeekingInputStream peekingInputStream;
        try {
            peekingInputStream = new PeekingInputStream(attachmentStream);
        } catch (IOException e) {
            throw new OxalisAs4Exception("Could not create peeking stream from attachment", e);
        }

        // Extract SBDH
        Header sbdh;
        try (SbdReader sbdReader = SbdReader.newInstance(peekingInputStream)) {
            sbdh = sbdReader.getHeader();
        } catch (SbdhException | IOException e) {
            throw new OxalisAs4Exception("Could not extract SBDH from payload");
        }

        // Validate SBDH
        try {
            transmissionVerifier.verify(sbdh, Direction.IN);
        } catch (VerifierException e) {
            throw new OxalisAs4Exception("Error verifying SBDH", e);
        }

        // Unmarshal Messaging node
        Node messagingNode = header.getElementsByTagNameNS("*", "Messaging").item(0);
        Messaging messaging;
        Unmarshaller unmarshaller = null;
        try {
            unmarshaller = Marshalling.getInstance().getJaxbContext().createUnmarshaller();
            messaging = unmarshaller.unmarshal(messagingNode, Messaging.class).getValue();
        } catch (JAXBException e) {
            throw new OxalisAs4Exception("Could not unmarshal Messaging node from header");
        }
        UserMessage userMessage = messaging.getUserMessage().stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No UserMessage present in header"));

        TransmissionIdentifier ti = TransmissionIdentifier.of(userMessage.getMessageInfo().getMessageId());

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


        // Extract senders certificate from header
        X509Certificate senderCertificate = SecurityCertificateExtractor.getSenderCertificate(header);

        Timestamp ts;
        try {
            // TODO: generate based on signature
            ts = timestampProvider.generate("foo".getBytes(), Direction.IN);
        } catch (TimestampException e) {
            throw new OxalisAs4Exception("Error generating timestamp", e);
        }

        As4InboundMetadata as4InboundMetadata = new As4InboundMetadata(
                ti,
                null, //header
                ts, //timestamp
                null, // transportProfile
                null, //digest
                senderCertificate,
                null //primary receipt
        );

        try {
            persisterHandler.persist(as4InboundMetadata, payloadPath);
        } catch (IOException e) {
            throw new OxalisAs4Exception("Error persisting AS4 metadata", e);
        }

        return null;
    }

}
