package no.difi.oxalis.as4.inbound;

import com.google.inject.Key;
import com.google.inject.name.Names;
import com.google.inject.servlet.ServletModule;

import javax.servlet.http.HttpServlet;

public class As4InboundModule extends ServletModule {

    @Override
    protected void configureServlets() {
        bind(Key.get(HttpServlet.class, Names.named("oxalis-as4")))
                .to(As4Servlet.class)
                .asEagerSingleton();

        bind(As4Provider.class);
        bind(As4InboundHandler.class);

        serve("/as4*").with(Key.get(HttpServlet.class, Names.named("oxalis-as4")));
    }
}
