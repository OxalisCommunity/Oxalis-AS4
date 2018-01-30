package no.difi.oxalis.as4.outbound;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.name.Names;
import no.difi.oxalis.api.outbound.MessageSender;

public class As4OutboundModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(Key.get(MessageSender.class, Names.named("oxalis-as4")))
                .to(As4MessageSenderFascade.class);

        bind(As4MessageSender.class);
    }
}
