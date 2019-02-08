package no.difi.oxalis.as4.util;

import lombok.experimental.UtilityClass;
import no.difi.oxalis.as4.lang.OxalisAs4Exception;
import org.apache.cxf.helpers.XPathUtils;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Messaging;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.UserMessage;
import org.w3.xmldsig.ReferenceType;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.soap.SOAPHeader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@UtilityClass
public class SOAPHeaderParser {

    private static final String NS_ALL = "*";
    private static final String MESSAGING = "Messaging";
    private static final String SIG = "Signature";
    private static final String SIG_VAL = "SignatureValue";
    private static final String SIG_INFO = "SignedInfo";
    private static final String NRI = "NonRepudiationInformation";
    private static final String REF = "Reference";
    private static final String DIGEST_VAL = "DigestValue";

    public static byte[] getAttachmentDigest(String refId, SOAPHeader header) throws OxalisAs4Exception {
        Element sigInfoElement = XMLUtil.getElementByTagName(header, SIG_INFO);
        NodeList refNodes = sigInfoElement.getElementsByTagNameNS(NS_ALL, REF);

        return XMLUtil.elementStream(refNodes)
                .filter(p -> refId.equals(p.getAttribute("URI")))
                .map(p -> p.getElementsByTagNameNS(NS_ALL, DIGEST_VAL).item(0).getTextContent().getBytes(StandardCharsets.UTF_8))
                .findAny()
                .orElse(null);
    }

    public static byte[] getSignature(SOAPHeader header) throws OxalisAs4Exception {
        return XMLUtil.getElementByTagPath(header, SIG, SIG_VAL)
                .getTextContent()
                .replace("\r\n", "")
                .getBytes(StandardCharsets.UTF_8);
    }

    public static List<ReferenceType> getReferenceListFromSignedInfo(SOAPHeader header) throws OxalisAs4Exception {
        return getRefList(XMLUtil.getElementByTagName(header, SIG_INFO));
    }

    public static List<ReferenceType> getReferenceListFromNonRepudiationInformation(SOAPHeader header) throws OxalisAs4Exception {
        return getRefList(XMLUtil.getElementByTagName(header, NRI));
    }

    private static List<ReferenceType> getRefList(Element element) throws OxalisAs4Exception {
        return getRefList(element.getElementsByTagNameNS(NS_ALL, REF));
    }

    private static List<ReferenceType> getRefList(NodeList refNodes) throws OxalisAs4Exception {
        return XMLUtil.unmarshal(refNodes, ReferenceType.class);
    }

    public static UserMessage getUserMessage(SOAPHeader header) throws OxalisAs4Exception {
        return getMessaging(header)
                .getUserMessage()
                .stream()
                .findFirst()
                .orElseThrow(() -> new OxalisAs4Exception("No UserMessage present in header"));
    }

    private static Messaging getMessaging(SOAPHeader header) throws OxalisAs4Exception {
        Node messagingNode = header.getElementsByTagNameNS(NS_ALL, MESSAGING).item(0);
        return XMLUtil.unmarshal(messagingNode, Messaging.class);
    }

    public static X509Certificate getSenderCertificate(SOAPHeader header) throws OxalisAs4Exception {
        return getX509Certificate(getCertificateAsText(header));
    }

    private static String getCertificateAsText(SOAPHeader header) throws OxalisAs4Exception {
        XPathUtils xu = new XPathUtils(Collections.singletonMap("wsse", "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"));
        return Optional.ofNullable(xu.getValueString("//wsse:BinarySecurityToken[1]/text()", header))
                .orElseThrow(() -> new OxalisAs4Exception("Unable to locate sender certificate"));
    }

    private static X509Certificate getX509Certificate(String cert) throws OxalisAs4Exception {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            byte[] certBytes = Base64.getDecoder().decode(cert.replaceAll("[\r\n]", ""));
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certBytes));
        } catch (CertificateException e) {
            throw new OxalisAs4Exception("Unable to parse sender certificate", e);
        }
    }
}
