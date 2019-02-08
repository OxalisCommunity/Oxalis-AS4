package no.difi.oxalis.as4.inbound;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.xml.soap.SOAPMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class As4InboundHandlerTest {
    @Mock private As4InboundParser as4InboundParser;
    @Mock private As4ResponseProvider as4ResponseProvider;
    @Mock private As4ReceiptPersister as4ReceiptPersister;

    @InjectMocks private As4InboundHandler as4InboundHandler;

    @Mock private SOAPMessage request;
    @Mock private SOAPMessage response;

    @Test
    public void testHandle() throws Exception {
        As4InboundInfo inboundInfo = As4InboundInfo.builder().build();
        given(as4InboundParser.parse(any())).willReturn(inboundInfo);
        given(as4ResponseProvider.getSOAPResponse(any())).willReturn(response);

        assertThat(as4InboundHandler.handle(request)).isSameAs(response);

        verify(as4InboundParser).parse(same(request));
        verify(as4ResponseProvider).getSOAPResponse(same(inboundInfo));
        verify(as4ReceiptPersister).persistReceipt(same(inboundInfo), same(response));
    }
}
