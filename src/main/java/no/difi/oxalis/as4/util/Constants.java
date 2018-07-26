package no.difi.oxalis.as4.util;

import javax.xml.namespace.QName;

public class Constants {

//    public static final String PARTY_ID_TYPE = "urn:fdc:peppol.eu:2017:identifiers:ap";
    public static final String PARTY_ID_TYPE = "urn:oasis:names:tc:ebcore:partyid-type:unregistered"; // CEF connectivitytest
    public static final String FROM_ROLE = "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/initiator";
    public static final String TO_ROLE = "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/responder";
//    public static final String SERVICE_TYPE = "urn:fdc:peppol.eu:2017:identifiers:proc-id";
    public static final String SERVICE_TYPE = "connectivity-procid-qns"; // CEF connectivitytest

    public static final String EBMS_NAMESPACE = "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/";

    public static final QName MESSAGING_QNAME = new QName(EBMS_NAMESPACE, "Messaging", "eb");
    public static final QName USER_MESSAGE_QNAME = new QName(EBMS_NAMESPACE, "UserMessage");
    public static final QName SIGNAL_MESSAGE_QNAME = new QName(EBMS_NAMESPACE, "SignalMessage");

    public static final String RSA_SHA256 = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";
    public static final String DIGEST_ALGORITHM_SHA256 = "sha256";
}
