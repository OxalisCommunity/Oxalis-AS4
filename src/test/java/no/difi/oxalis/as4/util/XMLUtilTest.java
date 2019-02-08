package no.difi.oxalis.as4.util;

import no.difi.oxalis.api.timestamp.Timestamp;
import org.junit.Test;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.UserMessage;
import org.w3.xmldsig.ReferenceType;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

public class XMLUtilTest {

    private final SOAPMessage request;

    public XMLUtilTest() throws SOAPException, IOException {
        this.request = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL)
                .createMessage(null, getClass().getResourceAsStream("/eDeliveryAS4-example.xml"));
    }

    @Test
    public void testUnmarshalNodeList() throws Exception {
        assertThat(XMLUtil.unmarshal(getSignedInfoReferenceNodeList(), ReferenceType.class))
                .extracting("uri")
                .containsExactly(
                        "#_840b593a-a40f-40d8-a8fd-89591478e5df",
                        "#_210bca51-e9b3-4ee1-81e7-226949ab6ff6",
                        "cid:1400668830234@seller.eu");
    }

    @Test
    public void testUnmarshalNode() throws Exception {
        Node userMessage = request.getSOAPHeader().getElementsByTagNameNS("*", "UserMessage").item(0);

        assertThat(XMLUtil.unmarshal(userMessage, UserMessage.class))
                .hasFieldOrPropertyWithValue("mpc", "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/defaultMPC");
    }

    @Test
    public void testElementStream() throws Exception {
        assertThat(XMLUtil.elementStream(getSignedInfoReferenceNodeList()))
                .hasSize(3);
    }

    @Test
    public void testGetElementByTagPath() throws Exception {
        assertThat(XMLUtil.getElementByTagPath(
                request.getSOAPHeader(), "Signature", "SignatureValue")
                .getTextContent()
        ).isEqualTo("CyVaSr9BLh7m4KC7xNszOsmJNM6aNJPKwQwNNqY5cvu3GgSIYBQWecg==");
    }

    @Test
    public void testGetElementByTagName() throws Exception {
        assertThat(XMLUtil.getElementByTagName(request.getSOAPHeader(), "SignatureValue")
                .getTextContent()
        ).isEqualTo("CyVaSr9BLh7m4KC7xNszOsmJNM6aNJPKwQwNNqY5cvu3GgSIYBQWecg==");
    }

    @Test
    public void testToXmlGregorianCalendar() throws Exception {
        Date in = Date.from(LocalDateTime.parse("2019-02-07T12:36:24.123")
                .atZone(ZoneId.systemDefault()).toInstant());

        assertThat(XMLUtil.toXmlGregorianCalendar(new Timestamp(in, null)).toXMLFormat())
                .startsWith("2019-02-07T12:36:24.123");
    }

    private NodeList getSignedInfoReferenceNodeList() throws SOAPException {
        Element signedInfo = (Element) request.getSOAPHeader().getElementsByTagNameNS("*", "SignedInfo").item(0);
        return signedInfo.getElementsByTagNameNS("*", "Reference");
    }
}
