package no.difi.oxalis.as4.outbound;

import com.google.inject.*;
import com.google.inject.name.Names;
import no.difi.oxalis.api.outbound.MessageSender;
import no.difi.oxalis.api.settings.Settings;
import no.difi.oxalis.as4.config.As4Conf;
import no.difi.oxalis.as4.util.CompressionUtil;
import no.difi.oxalis.as4.util.PeppolConfiguration;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class As4OutboundModule extends AbstractModule {

    @Override
    protected void configure() {
        if(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null){
            Security.addProvider(new BouncyCastleProvider());
        }

        bind(Key.get(MessageSender.class, Names.named("oxalis-as4")))
                .to(As4MessageSenderFacade.class);

        bind(Key.get(ExecutorService.class, Names.named("compression-pool")))
                .toProvider(() -> Executors.newFixedThreadPool(5)).in(Scopes.SINGLETON);

        bind(CompressionUtil.class);

        bind(MessagingProvider.class);

        bind(As4MessageSender.class);

    }

    @Provides
    @Singleton
    protected PeppolConfiguration getPeppolOutboundConfiguration(Settings<As4Conf> settings){

        if("cef-connectivity".equalsIgnoreCase(settings.getString(As4Conf.TYPE))){
            return new PeppolConfiguration(){

                @Override
                public String getServiceType() {
                    return "urn:oasis:names:tc:ebcore:partyid-type:unregistered";
                }

                @Override
                public String getPartyIDType() {
                    return "urn:oasis:names:tc:ebcore:partyid-type:unregistered";
                }

                @Override
                public String getAgreementRef() {
                    return null;
                }
            };
        }

        return new PeppolConfiguration();
    }

}
