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
import no.difi.oxalis.as4.api.MessageIdGenerator;
import no.difi.oxalis.as4.common.DefaultMessageIdGenerator;
import no.difi.oxalis.as4.inbound.As4InboundModule;
import no.difi.oxalis.as4.outbound.As4OutboundModule;
import no.difi.oxalis.commons.guice.GuiceModuleLoader;
import no.difi.oxalis.test.jetty.AbstractJettyServerTest;
import no.difi.vefa.peppol.common.model.*;
import org.apache.commons.io.IOUtils;
import org.apache.cxf.BusFactory;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.feature.Feature;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.*;

public class SendReceiveTest extends AbstractJettyServerTest {

    private MemoryPersister memoryPersister = new MemoryPersister();

    private byte[] firstPayload;
    private byte[] secondPayload;


    public SendReceiveTest() throws Exception {
        InputStream is = getClass().getResourceAsStream("/as2-peppol-bis-invoice-sbdh.xml");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        IOUtils.copy(is, buffer);

        this.firstPayload = buffer.toByteArray();



        is = getClass().getResourceAsStream("/simple-sbd.xml");
        buffer = new ByteArrayOutputStream();

        IOUtils.copy(is, buffer);

        this.secondPayload = buffer.toByteArray();
    }

    @Override
    public Injector getInjector() {
        return Guice.createInjector(
                new As4OutboundModule(),
                new As4InboundModule(),
                Modules.override(new GuiceModuleLoader()).with(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ReceiptPersister.class).toInstance((m, p) -> {});
                        bind(PayloadPersister.class).toInstance(memoryPersister);
                        bind(MessageIdGenerator.class).toInstance(new DefaultMessageIdGenerator("test.com"));
                    }
                })
        );
    }

    @BeforeClass
    public void addLoggingInterceptors(){
        Collection<Feature> features = BusFactory.getDefaultBus().getFeatures();
        features.add(new LoggingFeature());
        BusFactory.getDefaultBus().setFeatures(features);
    }

    @BeforeTest
    public void reset() {
        memoryPersister.reset();
    }

    @Test
    public void full() throws Exception {

        MessageSender messageSender = injector.getInstance(Key.get(MessageSender.class, Names.named("oxalis-as4")));

        TransmissionResponse firstResponse = messageSender.send(new TransmissionRequest() {
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
                return new ByteArrayInputStream(firstPayload);
            }
        });

        Assert.assertNotNull(firstResponse);
        Assert.assertEquals(TransportProfile.AS4, firstResponse.getProtocol());

        Assert.assertEquals(memoryPersister.getPersistedData().size(), 1);
        Map<MemoryPersister.Types, Object> dataFromFirstTransmission = memoryPersister.getPersistedData().
                get(firstResponse.getTransmissionIdentifier().getIdentifier());
        Assert.assertNotNull(dataFromFirstTransmission);

        Assert.assertEquals(firstResponse.getTransmissionIdentifier(), dataFromFirstTransmission.get(MemoryPersister.Types.ID));
        Assert.assertNotNull(dataFromFirstTransmission.get(MemoryPersister.Types.HEADER));
        Assert.assertEquals(firstPayload, (byte[]) dataFromFirstTransmission.get(MemoryPersister.Types.PAYLOAD));

        // Perform a second transmission
        TransmissionResponse secondResponse = messageSender.send(new TransmissionRequest() {
            @Override
            public Endpoint getEndpoint() {
                return Endpoint.of(TransportProfile.AS4, URI.create("http://localhost:8080/as4"),
                        injector.getInstance(X509Certificate.class));
            }

            @Override
            public Header getHeader() {
                return Header.newInstance()
                        .sender(ParticipantIdentifier.of("9908:991825827"))
                        .receiver(ParticipantIdentifier.of("9908:991825827"))
                        .documentType(DocumentTypeIdentifier.of("urn:oasis:names:specification:ubl:schema:xsd:Invoice-2::Invoice##urn:www.cenbii.eu:transaction:biicoretrdm010:ver1.0:#urn:test.com:bis:something_else:ver1.0::2.0"))
                        .process(ProcessIdentifier.of("urn:www.cenbii.eu:profile:something_else:ver1.0"));
            }

            @Override
            public InputStream getPayload() {
                return new ByteArrayInputStream(secondPayload);
            }
        });

        Assert.assertNotNull(secondResponse);
        Assert.assertEquals(TransportProfile.AS4, secondResponse.getProtocol());

        Assert.assertNotEquals(secondResponse.getTransmissionIdentifier(), firstResponse.getTransmissionIdentifier());

        Assert.assertEquals(memoryPersister.getPersistedData().size(), 2);
        Map<MemoryPersister.Types, Object> dataFromSecondTransmission = memoryPersister.getPersistedData().
                get(secondResponse.getTransmissionIdentifier().getIdentifier());
        Assert.assertNotNull(dataFromSecondTransmission);

        Assert.assertEquals(secondResponse.getTransmissionIdentifier(), dataFromSecondTransmission.get(MemoryPersister.Types.ID));
        Assert.assertNotNull(dataFromSecondTransmission.get(MemoryPersister.Types.HEADER));
        Assert.assertEquals(secondPayload, (byte[]) dataFromSecondTransmission.get(MemoryPersister.Types.PAYLOAD));
    }


    static class MemoryPersister implements PayloadPersister {
        enum Types{
            ID, HEADER, PAYLOAD
        }

        private Map<String, EnumMap<Types, Object>> persistedData = new TreeMap<>();

        @Override
        public Path persist(TransmissionIdentifier transmissionIdentifier, Header header, InputStream is) throws IOException {

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            IOUtils.copy(is, buffer);

            EnumMap<Types, Object> map = new EnumMap<>(Types.class);
            map.put(Types.ID, transmissionIdentifier);
            map.put(Types.HEADER, header);
            map.put(Types.PAYLOAD, buffer.toByteArray());

            persistedData.put(transmissionIdentifier.getIdentifier(), map);

            return null;
        }

        public void reset() {
            persistedData.clear();
        }

        public Map<String, EnumMap<Types, Object>> getPersistedData(){
            return Collections.unmodifiableMap(persistedData);
        }
    }
}
