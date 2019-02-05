package no.difi.oxalis.as4.common;

import no.difi.oxalis.api.inbound.InboundMetadata;
import no.difi.oxalis.api.outbound.TransmissionRequest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.mockito.BDDMockito.given;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest(DefaultMessageIdGenerator.class)
public class DefaultMessageIdGeneratorTest {

    private DefaultMessageIdGenerator generator = new DefaultMessageIdGenerator("test.com");

    @Mock private TransmissionRequest transmissionRequest;
    @Mock private InboundMetadata inboundMetadata;

    @Before
    public void before() {
        mockStatic(System.class);
        given(System.currentTimeMillis()).willReturn(1549372800L);
    }

    @Test
    public void testGenerateByTransmissionRequest() {
        given(transmissionRequest.hashCode()).willReturn(1234);
        Assert.assertEquals("<1549372800.1.1234.Oxalis@test.com>", generator.generate(transmissionRequest));
    }

    @Test
    public void testGenerateByInboundMetadata() {
        given(inboundMetadata.hashCode()).willReturn(1234);
        Assert.assertEquals("<1549372800.1.1234.Oxalis@test.com>", generator.generate(inboundMetadata));
    }
}
