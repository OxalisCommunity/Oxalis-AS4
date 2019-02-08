package no.difi.oxalis.as4.inbound;

import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import no.difi.oxalis.api.lang.TimestampException;
import no.difi.oxalis.api.model.Direction;
import no.difi.oxalis.api.model.TransmissionIdentifier;
import no.difi.oxalis.api.persist.PayloadPersister;
import no.difi.oxalis.api.timestamp.Timestamp;
import no.difi.oxalis.api.timestamp.TimestampProvider;
import no.difi.oxalis.as4.lang.OxalisAs4Exception;
import no.difi.oxalis.as4.util.MessageIdUtil;
import no.difi.oxalis.as4.util.SOAPHeaderParser;
import no.difi.oxalis.as4.util.SOAPMessageUtil;
import no.difi.oxalis.commons.io.PeekingInputStream;
import no.difi.oxalis.commons.io.UnclosableInputStream;
import no.difi.vefa.peppol.common.code.DigestMethod;
import no.difi.vefa.peppol.common.model.Digest;
import no.difi.vefa.peppol.common.model.Header;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.PartInfo;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.UserMessage;

import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPMessage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

class As4InboundParser {

    private final PayloadPersister payloadPersister;
    private final TimestampProvider timestampProvider;
    private final SBDHProvider sbdhProvider;

    @Inject
    As4InboundParser(PayloadPersister payloadPersister, TimestampProvider timestampProvider, SBDHProvider sbdhProvider) {
        this.sbdhProvider = sbdhProvider;
        this.payloadPersister = payloadPersister;
        this.timestampProvider = timestampProvider;
    }

    As4InboundInfo parse(SOAPMessage request) throws OxalisAs4Exception {
        SOAPHeader header = SOAPMessageUtil.getSoapHeader(request);
        UserMessage userMessage = SOAPHeaderParser.getUserMessage(header);
        TransmissionIdentifier transmissionIdentifier = getTransmissionIdentifier(userMessage);
        PeekingInputStream peekingAttachmentStream = SOAPMessageUtil.getPeekingAttachmentStream(request);
        Header sbdh = sbdhProvider.getValidSBDH(peekingAttachmentStream);

        return As4InboundInfo.builder()
                .transmissionIdentifier(transmissionIdentifier)
                .sbdh(sbdh)
                .payloadPath(persistPayload(peekingAttachmentStream, sbdh, transmissionIdentifier))
                .timestamp(getTimestamp(header))
                .referenceListFromSignedInfo(SOAPHeaderParser.getReferenceListFromSignedInfo(header))
                .attachmentDigest(getAttachmentDigest(header, userMessage.getPayloadInfo().getPartInfo()))
                .senderCertificate(SOAPHeaderParser.getSenderCertificate(header))
                .build();
    }

    private TransmissionIdentifier getTransmissionIdentifier(UserMessage userMessage) throws OxalisAs4Exception {
        String messageId = userMessage.getMessageInfo().getMessageId();

        if (!MessageIdUtil.verify(messageId))
            throw new OxalisAs4Exception(
                    "Invalid Message-ID '" + messageId + "' in inbound message.");

        return TransmissionIdentifier.of(messageId);
    }

    private Path persistPayload(PeekingInputStream peekingAttachmentStream, Header sbdh, TransmissionIdentifier ti) throws
            OxalisAs4Exception {
        // Extract "fresh" InputStream
        try (InputStream payloadInputStream = peekingAttachmentStream.newInputStream()) {

            // Persist content
            Path payloadPath = payloadPersister.persist(ti, sbdh,
                    new UnclosableInputStream(payloadInputStream));

            // Exhaust InputStream
            ByteStreams.exhaust(payloadInputStream);
            return payloadPath;
        } catch (IOException e) {
            throw new OxalisAs4Exception("Error processing payload input stream", e);
        }
    }

    private Timestamp getTimestamp(SOAPHeader header) throws OxalisAs4Exception {
        try {
            return timestampProvider.generate(
                    SOAPHeaderParser.getSignature(header), Direction.IN);
        } catch (TimestampException e) {
            throw new OxalisAs4Exception("Error generating timestamp", e);
        }
    }

    private Digest getAttachmentDigest(SOAPHeader header, List<PartInfo> partInfoList) throws OxalisAs4Exception {
        if (partInfoList.size() != 1) {
            throw new OxalisAs4Exception("Should only be one PartInfo in header");
        }

        String refId = partInfoList.get(0).getHref();
        // Get attachment digest from header
        return Digest.of(DigestMethod.SHA256, SOAPHeaderParser.getAttachmentDigest(refId, header));
    }
}
