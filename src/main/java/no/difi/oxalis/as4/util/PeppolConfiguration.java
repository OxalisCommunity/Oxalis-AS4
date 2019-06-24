package no.difi.oxalis.as4.util;

import lombok.Getter;
import no.difi.oxalis.api.tag.Tag;
import org.apache.wss4j.dom.handler.WSHandlerConstants;

import java.util.Arrays;
import java.util.List;

@Getter
public class PeppolConfiguration implements Tag {
    public static final String IDENTIFIER = "AS4.OUTBOUND.PEPPOL";

    private List<String> actions = Arrays.asList(WSHandlerConstants.SIGNATURE, WSHandlerConstants.ENCRYPT);

    private String partyIDType = "urn:fdc:peppol.eu:2017:identifiers:ap";
    private String agreementRef = "urn:fdc:peppol.eu:2017:agreements:tia:ap_provider";
    private String serviceType = "cenbii-procid-ubl";

    private String fromRole = "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/initiator";
    private String toRole = "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/responder";


    @Override
    public String getIdentifier() {
        return IDENTIFIER;
    }
}
