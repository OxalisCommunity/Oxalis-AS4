package no.difi.oxalis.as4.util;

import org.junit.Test;

import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class SOAPHeaderParserTest {

    private final SOAPMessage request;

    public SOAPHeaderParserTest() throws SOAPException, IOException {
        this.request = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL)
                .createMessage(null, getClass().getResourceAsStream("/eDeliveryAS4-example.xml"));
    }

    @Test
    public void testGetAttachmentDigest() throws Exception {
        assertThat(SOAPHeaderParser.getAttachmentDigest(
                "cid:1400668830234@seller.eu", request.getSOAPHeader()
        )).isEqualTo("wVgT8wKEsJlO0O5OjjQB/vw9mGsxi1n/0dc9qeRqFM4=".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testGetSignature() throws Exception {
        assertThat(SOAPHeaderParser.getSignature(request.getSOAPHeader()))
                .isEqualTo("CyVaSr9BLh7m4KC7xNszOsmJNM6aNJPKwQwNNqY5cvu3GgSIYBQWecg==".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testGetReferenceListFromSignedInfo() throws Exception {
        assertThat(SOAPHeaderParser.getReferenceListFromSignedInfo(request.getSOAPHeader()))
                .extracting("uri")
                .containsExactly(
                        "#_840b593a-a40f-40d8-a8fd-89591478e5df",
                        "#_210bca51-e9b3-4ee1-81e7-226949ab6ff6",
                        "cid:1400668830234@seller.eu");
    }

    @Test
    public void testGetUserMessage() throws Exception {
        assertThat(SOAPHeaderParser.getUserMessage(request.getSOAPHeader()))
                .hasFieldOrPropertyWithValue("mpc", "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/defaultMPC");
    }

    @Test
    public void testGetSenderCertificate() throws Exception {
        assertThat(SOAPHeaderParser.getSenderCertificate(request.getSOAPHeader()))
                .hasFieldOrPropertyWithValue("version", 3)
                .hasFieldOrPropertyWithValue("serialNumber", new BigInteger("4107"))
                .hasFieldOrPropertyWithValue("issuerDN.name", "EMAILADDRESS=CEF-EDELIVERY-SUPPORT@ec.europa.eu, CN=Connectivity Test Component CA, OU=Connecting Europe Facility, O=Connectivity Test, ST=Belgium, C=BE")
                .hasFieldOrPropertyWithValue("issuerX500Principal.name", "1.2.840.113549.1.9.1=#16224345462d4544454c49564552592d535550504f52544065632e6575726f70612e6575,CN=Connectivity Test Component CA,OU=Connecting Europe Facility,O=Connectivity Test,ST=Belgium,C=BE");
    }
}
