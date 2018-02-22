package no.difi.oxalis.as4;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import no.difi.oxalis.api.outbound.MessageSender;
import no.difi.oxalis.api.outbound.TransmissionRequest;
import no.difi.oxalis.api.outbound.TransmissionResponse;
import no.difi.oxalis.api.persist.ReceiptPersister;
import no.difi.oxalis.as4.inbound.As4InboundModule;
import no.difi.oxalis.as4.outbound.As4OutboundModule;
import no.difi.oxalis.commons.guice.GuiceModuleLoader;
import no.difi.oxalis.commons.http.ApacheHttpModule;
import no.difi.oxalis.test.jetty.AbstractJettyServerTest;
import no.difi.vefa.peppol.common.model.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.net.URI;
import java.security.cert.X509Certificate;

public class SimpleSendTest extends AbstractJettyServerTest {

    @Override
    public Injector getInjector() {
        return Guice.createInjector(
                new As4OutboundModule(),
                new As4InboundModule(),
                new ApacheHttpModule(),
                Modules.override(new GuiceModuleLoader()).with(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ReceiptPersister.class).toInstance((m,p) -> {});
                    }
                })
        );
    }

    @Test
    public void simple() throws Exception {
        MessageSender messageSender = injector.getInstance(Key.get(MessageSender.class, Names.named("oxalis-as4")));

        TransmissionResponse response = messageSender.send(new TransmissionRequest() {
            @Override
            public Endpoint getEndpoint() {
                return Endpoint.of(TransportProfile.AS4, URI.create("http://localhost:8080/as4"),
                        injector.getInstance(X509Certificate.class));
            }

            @Override
            public Header getHeader() {
                return Header.newInstance()
                        .sender(ParticipantIdentifier.of("0007:5567125082"))
                        .receiver(ParticipantIdentifier.of("0007:4455454480"))
                        .documentType(DocumentTypeIdentifier.of("urn:oasis:names:specification:ubl:schema:xsd:Invoice-2::Invoice##urn:www.cenbii.eu:transaction:biicoretrdm010:ver1.0:#urn:www.peppol.eu:bis:peppol4a:ver1.0::2.0"))
                        .process(ProcessIdentifier.of("urn:www.cenbii.eu:profile:bii04:ver1.0"));
            }

            @Override
            public InputStream getPayload() {
                return getClass().getResourceAsStream("/as2-peppol-bis-invoice-sbdh.xml");
            }
        });

        Assert.assertNotNull(response);
    }
}
