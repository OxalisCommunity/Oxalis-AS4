package no.difi.oxalis.as4;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import no.difi.oxalis.api.model.TransmissionIdentifier;
import no.difi.oxalis.api.outbound.MessageSender;
import no.difi.oxalis.api.outbound.TransmissionRequest;
import no.difi.oxalis.api.outbound.TransmissionResponse;
import no.difi.oxalis.api.persist.PayloadPersister;
import no.difi.oxalis.api.persist.ReceiptPersister;
import no.difi.oxalis.as4.inbound.As4InboundModule;
import no.difi.oxalis.as4.outbound.As4OutboundModule;
import no.difi.oxalis.commons.guice.GuiceModuleLoader;
import no.difi.oxalis.commons.http.ApacheHttpModule;
import no.difi.oxalis.test.jetty.AbstractJettyServerTest;
import no.difi.vefa.peppol.common.model.*;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public class SimpleSendTest extends AbstractJettyServerTest {

    private MemoryPersister memoryPersister = new MemoryPersister();
    private byte[] testPayload;


    public SimpleSendTest() throws Exception{
        InputStream is = getClass().getResourceAsStream("/as2-peppol-bis-invoice-sbdh.xml");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[1024];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();
        this.testPayload = buffer.toByteArray();

    }

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
                        bind(PayloadPersister.class).toInstance(memoryPersister);
                    }
                })
        );
    }

    @BeforeTest
    public void reset(){
        memoryPersister.reset();
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
                return new ByteArrayInputStream(testPayload);
            }
        });

        Assert.assertNotNull(response);
        Assert.assertEquals(TransportProfile.AS4, response.getProtocol());

        // Validate payload
        Assert.assertEquals(1, memoryPersister.getValues().size());
        Object[] persistedPayload = memoryPersister.getValues().get(response.getTransmissionIdentifier().getIdentifier());
        Assert.assertNotNull(persistedPayload);

        TransmissionIdentifier responseTransmissionIdentifier = (TransmissionIdentifier)persistedPayload[0];
        Assert.assertEquals(response.getTransmissionIdentifier(), responseTransmissionIdentifier);

        Header responseHeader = (Header) persistedPayload[1];

        byte[] responePayload = (byte[]) persistedPayload[2];
        Assert.assertEquals(testPayload, responePayload);
    }

    static class MemoryPersister implements PayloadPersister{

        private Map<String, Object[]> values = new TreeMap<>();

        @Override
        public Path persist(TransmissionIdentifier transmissionIdentifier, Header header, InputStream is) throws IOException {

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            int nRead;
            byte[] data = new byte[1024];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }

            buffer.flush();
            byte[] byteArray = buffer.toByteArray();

            values.put(transmissionIdentifier.getIdentifier(), new Object[]{transmissionIdentifier, header, byteArray});

            return null;
        }

        public void reset(){
            values.clear();
        }

        public Map<String, Object[]> getValues(){
            return Collections.unmodifiableMap(values);
        }
    }
}

