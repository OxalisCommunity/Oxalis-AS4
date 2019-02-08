package no.difi.oxalis.as4.inbound;

import no.difi.oxalis.api.model.Direction;
import no.difi.oxalis.api.persist.PayloadPersister;
import no.difi.oxalis.api.timestamp.Timestamp;
import no.difi.oxalis.api.timestamp.TimestampProvider;
import no.difi.vefa.peppol.common.code.DigestMethod;
import no.difi.vefa.peppol.common.model.Header;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.xml.soap.*;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class As4InboundParserTest {

    @Mock private PayloadPersister payloadPersister;
    @Mock private TimestampProvider timestampProvider;
    @Mock private SBDHProvider sbdhProvider;

    @InjectMocks private As4InboundParser as4InboundParser;

    @Mock private Header sbdh;
    @Mock private Timestamp timestamp;
    @Mock private Path payloadPath;

    private final SOAPMessage request;

    public As4InboundParserTest() throws SOAPException, IOException {
        this.request = createMessage();
    }

    private SOAPMessage createMessage() throws IOException, SOAPException {
        SOAPMessage message = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL)
                .createMessage(null, getClass().getResourceAsStream("/eDeliveryAS4-example.xml"));

        InputStream is = getClass().getResourceAsStream("/as2-peppol-bis-invoice-sbdh.xml");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        GZIPOutputStream os = new GZIPOutputStream(bos);
        IOUtils.copy(is, os);
        os.finish();

        AttachmentPart ap = message.createAttachmentPart(new StreamSource(new ByteArrayInputStream(bos.toByteArray())), "application/xml");
        ap.setMimeHeader("Content-Transfer-Encoding", "binary");
        ap.setMimeHeader("Content-Encoding", "gzip");
        message.addAttachmentPart(ap);
        message.saveChanges();
        return message;
    }

    @Test
    public void testParse() throws Exception {
        given(timestampProvider.generate(any(), any())).willReturn(timestamp);
        given(sbdhProvider.getValidSBDH(any())).willReturn(sbdh);
        given(payloadPersister.persist(any(), any(), any())).willReturn(payloadPath);

        As4InboundInfo result = as4InboundParser.parse(request);

        assertThat(result.getTransmissionIdentifier().getIdentifier()).isEqualTo("8196c8e2-820f-4aec-a1ca-288a4d1d4020@seller.eu");
        assertThat(result.getSbdh()).isSameAs(sbdh);
        assertThat(result.getPayloadPath()).isSameAs(payloadPath);
        assertThat(result.getTimestamp()).isSameAs(timestamp);
        assertThat(result.getReferenceListFromSignedInfo())
                .extracting("uri")
                .containsExactly(
                        "#_840b593a-a40f-40d8-a8fd-89591478e5df",
                        "#_210bca51-e9b3-4ee1-81e7-226949ab6ff6",
                        "cid:1400668830234@seller.eu");
        assertThat(result.getAttachmentDigest()).isNotNull();
        assertThat(result.getAttachmentDigest().getMethod()).isSameAs(DigestMethod.SHA256);
        assertThat(Base64.getEncoder().encodeToString(result.getAttachmentDigest().getValue()))
                .isEqualTo("d1ZnVDh3S0VzSmxPME81T2pqUUIvdnc5bUdzeGkxbi8wZGM5cWVScUZNND0=");
        assertThat(result.getSenderCertificate())
                .hasFieldOrPropertyWithValue("version", 3)
                .hasFieldOrPropertyWithValue("serialNumber", new BigInteger("4107"))
                .hasFieldOrPropertyWithValue("issuerDN.name", "EMAILADDRESS=CEF-EDELIVERY-SUPPORT@ec.europa.eu, CN=Connectivity Test Component CA, OU=Connecting Europe Facility, O=Connectivity Test, ST=Belgium, C=BE")
                .hasFieldOrPropertyWithValue("issuerX500Principal.name", "1.2.840.113549.1.9.1=#16224345462d4544454c49564552592d535550504f52544065632e6575726f70612e6575,CN=Connectivity Test Component CA,OU=Connecting Europe Facility,O=Connectivity Test,ST=Belgium,C=BE");

        verify(timestampProvider).generate(any(), same(Direction.IN));
        verify(sbdhProvider).getValidSBDH(any());
        verify(payloadPersister).persist(same(result.getTransmissionIdentifier()), same(sbdh), any());
    }
}
