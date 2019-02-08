package no.difi.oxalis.as4.inbound;

import com.google.inject.Key;
import com.google.inject.name.Names;
import com.google.inject.servlet.ServletModule;
import no.difi.oxalis.as4.config.TrustStore;
import no.difi.oxalis.commons.settings.SettingsBuilder;

import javax.servlet.http.HttpServlet;

public class As4InboundModule extends ServletModule {

    @Override
    protected void configureServlets() {
        bind(Key.get(HttpServlet.class, Names.named("oxalis-as4")))
                .to(As4Servlet.class)
                .asEagerSingleton();

        bind(As4Provider.class);
        bind(As4InboundParser.class);
        bind(As4ResponseProvider.class);
        bind(As4ReceiptPersister.class);
        bind(As4InboundHandler.class);
        bind(SBDHProvider.class);
        SettingsBuilder.with(binder(), TrustStore.class);

        serve("/as4*").with(Key.get(HttpServlet.class, Names.named("oxalis-as4")));
    }
}
