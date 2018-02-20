package no.difi.oxalis.as4.util;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.soap.SOAPHeader;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

public class SecurityCertificateExtractor {

    private static final String NS_ALL = "*";
    private static final String BST = "BinarySecurityToken";
    private static final String SIG = "Signature";
    private static final String KEY_INFO = "KeyInfo";
    private static final String REF = "Reference";

    public static X509Certificate getSenderCertificate(SOAPHeader header) {

        NodeList sigNode = header.getElementsByTagNameNS(NS_ALL, SIG);
        if (sigNode.getLength() != 1) {
            throw new RuntimeException("Zero or multiple Signature elements in header");
        }
        Element sigElement = (Element) sigNode.item(0);
        NodeList keyInfoNode = sigElement.getElementsByTagNameNS(NS_ALL, KEY_INFO);
        if (keyInfoNode.getLength() != 1) {
            throw new RuntimeException("Zero or multiple KeyInfo children of Signature");
        }
        Element keyInfoElement = (Element) keyInfoNode.item(0);
        NodeList refNode = keyInfoElement.getElementsByTagNameNS(NS_ALL, REF);
        if (refNode.getLength() != 1) {
            throw new RuntimeException(("Zero or multiple Reference nodes under Signature->KeyInfo"));
        }
        String refUri = ((Element) refNode.item(0)).getAttribute("URI").replace("#", "");

        NodeList bstNodes = header.getElementsByTagNameNS(NS_ALL, BST);
        for (int i=0; i<bstNodes.getLength(); i++) {
            Element bstElem = (Element) bstNodes.item(i);
            if (bstElem.getAttribute("wsu:Id").equals(refUri)) {
                try {
                    String pem = bstElem.getTextContent().replace("\r\n", "");
                    byte[] buf = Base64.getDecoder().decode(pem);
                    return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(buf));
                } catch (CertificateException e) {
                    throw new RuntimeException("Could not create certificate from BinarySecurityToken",  e);
                }
            }
        }

        return null;
    }
}
