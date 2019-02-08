package no.difi.oxalis.as4.inbound;

import no.difi.oxalis.api.model.TransmissionIdentifier;
import no.difi.oxalis.api.timestamp.Timestamp;
import no.difi.oxalis.as4.api.MessageIdGenerator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.w3.xmldsig.ReferenceType;

import javax.xml.soap.SOAPMessage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@RunWith(MockitoJUnitRunner.class)
public class As4ResponseProviderTest {

    private static final Date DATE = Date.from(LocalDateTime.parse("2019-02-07T12:36:24.123")
            .atZone(ZoneId.systemDefault()).toInstant());

    @Mock private MessageIdGenerator messageIdGenerator;

    @InjectMocks private As4ResponseProvider as4ResponseProvider;

    @Test
    public void testGetSOAPResponse() throws Exception {
        given(messageIdGenerator.generate()).willReturn("A296c8e2-820f-4aec-a1ca-288a4d1d4020@buyer.eu");

        As4InboundInfo inboundInfo = As4InboundInfo.builder()
                .transmissionIdentifier(TransmissionIdentifier.of("8196c8e2-820f-4aec-a1ca-288a4d1d4020@seller.eu"))
                .timestamp(new Timestamp(DATE, null))
                .referenceListFromSignedInfo(Collections.singletonList(ReferenceType.builder()
                        .withURI("#_840b593a-a40f-40d8-a8fd-89591478e5df")
                        .build()))
                .build();

        SOAPMessage result = as4ResponseProvider.getSOAPResponse(inboundInfo);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        result.writeTo(out);
        String message = out.toString(StandardCharsets.UTF_8.name());

        assertThat(removeTimeZoneFromDate(removeNamespaces(message))).isEqualTo("<Envelope><Header><Messaging><SignalMessage><MessageInfo><Timestamp>2019-02-07T12:36:24.123</Timestamp><MessageId>A296c8e2-820f-4aec-a1ca-288a4d1d4020@buyer.eu</MessageId><RefToMessageId>8196c8e2-820f-4aec-a1ca-288a4d1d4020@seller.eu</RefToMessageId></MessageInfo><Receipt><NonRepudiationInformation><MessagePartNRInformation><Reference URI=\"#_840b593a-a40f-40d8-a8fd-89591478e5df\"/></MessagePartNRInformation></NonRepudiationInformation></Receipt></SignalMessage></Messaging></Header><Body/></Envelope>");
    }

    private String removeNamespaces(String in) {
        return in.replaceAll("(</?)\\w+:", "$1")
                .replaceAll(" \\w+:\\w+=\"[^\"]+\"", "");
    }

    private String removeTimeZoneFromDate(String in) {
        return in.replaceAll("(?:Z|\\+\\d{2}:\\d{2})</Timestamp>", "</Timestamp>");
    }
}
