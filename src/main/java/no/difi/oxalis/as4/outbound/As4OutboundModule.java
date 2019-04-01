package no.difi.oxalis.as4.outbound;

import com.google.inject.*;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import no.difi.oxalis.api.outbound.MessageSender;
import no.difi.oxalis.as4.util.CompressionUtil;
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
                .to(As4MessageSenderFascade.class);

        bind(Key.get(ExecutorService.class, Names.named("compression-pool")))
                .toProvider(() -> Executors.newFixedThreadPool(5)).in(Scopes.SINGLETON);

        bind(CompressionUtil.class);

//        bind(As4MessageSender.class);

    }

//    @Provides
//    @Named("PEPPOL AS4 Sender")
//    @Singleton
//    protected As4MessageSender getAs4SignatureOnlySender(Provider<As4MessageSender> messageSenderProvider){
//        return new As4MessageSenderFascade(messageSenderProvider, true);
//    }
}
