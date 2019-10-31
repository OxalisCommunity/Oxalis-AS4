package no.difi.oxalis.as4.outbound;

import no.difi.oxalis.api.settings.Settings;
import no.difi.oxalis.as4.config.As4Conf;
import no.difi.oxalis.as4.util.PeppolConfiguration;

import javax.inject.Named;
import java.nio.file.Path;

public class MessagingProviderTest_CEF_SBDH extends AbstractMessagingProviderTest {

    @Override
    protected String getPayloadPath() {
        return "/cef-sbd.xml";
    }

    @Override
    protected PeppolConfiguration getPEPPOLOutboundConfiguration() {

        return new As4OutboundModule().getPeppolOutboundConfiguration(new Settings<As4Conf>(){
            @Override
            public String getString(As4Conf as4Conf) {
                return "cef-connectivity";
            }

            @Override
            public int getInt(As4Conf as4Conf) {
                return 0;
            }

            @Override
            public Named getNamed(As4Conf key) {
                return null;
            }

            @Override
            public Path getPath(As4Conf key, Path path) {
                return null;
            }
        });
    }

    @Override
    protected String getAction() {
        return "submitMessage";
    }

    @Override
    protected String getServiceType() {
        return "e-delivery";
    }

    @Override
    protected String getServiceValue() {
        return "http://ec.europa.eu/edelivery/services/connectivity-service";
    }

    @Override
    protected String getPartyType() {
        return "urn:oasis:names:tc:ebcore:partyid-type:unregistered";
    }

    @Override
    protected String getFinalRecipient() {
        return "urn:oasis:names:tc:ebcore:partyid-type:unregistered:c4";
    }

    @Override
    protected String getOriginalSender() {
        return "urn:oasis:names:tc:ebcore:partyid-type:unregistered:c1";
    }
}
