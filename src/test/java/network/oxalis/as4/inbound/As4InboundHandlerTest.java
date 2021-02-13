package network.oxalis.as4.inbound;

import network.oxalis.as4.lang.OxalisAs4Exception;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.PartInfo;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.PartProperties;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.PayloadInfo;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Property;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class As4InboundHandlerTest {

    @Test()
    public void testValidateMessageId_withValidHref() throws Exception{

        PayloadInfo payloadInfo = PayloadInfo.builder()
                .addPartInfo(PartInfo.builder()
                        .withHref("cid:attachedPayload")
                        .withPartProperties(PartProperties.builder().
                                withProperty(Property.builder()
                                        .withName("MimeType")
                                        .withValue("Dummy").build()).build()
                        )
                        .build())
                .build();

        As4InboundHandler.validatePayloads(payloadInfo);
    }

    @Test( expectedExceptions = {OxalisAs4Exception.class} )
    public void testValidateMessageId_withInvalidHref() throws Exception{

        PayloadInfo payloadInfo = PayloadInfo.builder()
                .addPartInfo(PartInfo.builder()
                        .withHref("http://difi.no")
                        .build())
                .build();

        As4InboundHandler.validatePayloads(payloadInfo);

        fail();
    }


}