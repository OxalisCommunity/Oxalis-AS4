package no.difi.oxalis.as4.inbound;

import no.difi.oxalis.api.model.TransmissionIdentifier;
import no.difi.oxalis.api.persist.ReceiptPersister;
import no.difi.oxalis.api.timestamp.Timestamp;
import no.difi.vefa.peppol.common.model.Digest;
import no.difi.vefa.peppol.common.model.Header;
import no.difi.vefa.peppol.common.model.TransportProfile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.xml.soap.SOAPMessage;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class As4ReceiptPersisterTest {

    private static final Date DATE = Date.from(LocalDateTime.parse("2019-02-07T12:36:24.123")
            .atZone(ZoneId.systemDefault()).toInstant());

    @InjectMocks private As4ReceiptPersister as4ReceiptPersister;

    @Mock private ReceiptPersister receiptPersister;
    @Mock private SOAPMessage response;
    @Mock private Path payloadPath;
    @Mock private TransmissionIdentifier transmissionIdentifier;
    @Mock private Header sbdh;
    @Mock private Digest attachmentDigest;
    @Mock private X509Certificate senderCertificate;

    @Captor private ArgumentCaptor<As4InboundMetadata> metadataArgumentCaptor;

    @Test
    public void testPersistReceipt() throws Exception {
        As4InboundInfo inboundInfo = As4InboundInfo.builder()
                .transmissionIdentifier(transmissionIdentifier)
                .sbdh(sbdh)
                .timestamp(new Timestamp(DATE, null))
                .payloadPath(payloadPath)
                .attachmentDigest(attachmentDigest)
                .senderCertificate(senderCertificate)
                .build();

        as4ReceiptPersister.persistReceipt(inboundInfo, response);

        verify(receiptPersister).persist(metadataArgumentCaptor.capture(), same(inboundInfo.getPayloadPath()));

        As4InboundMetadata metadata = metadataArgumentCaptor.getValue();
        assertThat(metadata.getTransmissionIdentifier()).isSameAs(inboundInfo.getTransmissionIdentifier());
        assertThat(metadata.getHeader()).isSameAs(inboundInfo.getSbdh());
        assertThat(metadata.getTimestamp()).isSameAs(DATE);
        assertThat(metadata.getDigest()).isSameAs(inboundInfo.getAttachmentDigest());
        assertThat(metadata.getCertificate()).isSameAs(inboundInfo.getSenderCertificate());
        assertThat(metadata.getProtocol()).isSameAs(TransportProfile.AS4);
    }
}
