package no.difi.oxalis.as4.inbound;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;
import no.difi.oxalis.api.settings.Settings;
import no.difi.oxalis.as4.common.MerlinProvider;
import no.difi.oxalis.as4.config.TrustStore;
import no.difi.oxalis.as4.lang.OxalisAs4TransmissionException;
import no.difi.oxalis.as4.util.OxalisAlgorithmSuiteLoader;
import no.difi.oxalis.commons.security.KeyStoreConf;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.jaxws.EndpointImpl;
import org.apache.cxf.transport.servlet.CXFNonSpringServlet;
import org.apache.cxf.ws.security.wss4j.PolicyBasedWSS4JInInterceptor;
import org.apache.cxf.ws.security.wss4j.PolicyBasedWSS4JOutInterceptor;
import org.apache.wss4j.common.crypto.Merlin;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Enumeration;

import static org.apache.cxf.rt.security.SecurityConstants.*;

@Slf4j
@Singleton
public class As4Servlet extends CXFNonSpringServlet {

    @Inject
    @Named("truststore-ap")
    private KeyStore trustStore;

    @Inject
    private Settings<KeyStoreConf> settings;

    @Inject
    private Settings<TrustStore> trustStoreSettings;

    @Inject
    @Named("conf")
    private Path confFolder;

    @Inject
    private As4EndpointsPublisher endpointsPublisher;

    @Inject
    private MerlinProvider merlinProvider;

    @Override
    protected void loadBus(ServletConfig servletConfig) {
        super.loadBus(servletConfig);
        new OxalisAlgorithmSuiteLoader(bus);
        extendTrustStore();

        EndpointImpl endpointImpl = endpointsPublisher.publish(getBus());

        Merlin merlin = merlinProvider.getMerlib();

        endpointImpl.getProperties().put(SIGNATURE_CRYPTO, merlin);
        endpointImpl.getProperties().put(SIGNATURE_PASSWORD, settings.getString(KeyStoreConf.KEY_PASSWORD));
        endpointImpl.getProperties().put(SIGNATURE_USERNAME, settings.getString(KeyStoreConf.KEY_ALIAS));

        endpointImpl.getProperties().put(ENCRYPT_CRYPTO, merlin);
        endpointImpl.getProperties().put(ENCRYPT_USERNAME, settings.getString(KeyStoreConf.KEY_ALIAS));

        endpointImpl.getInInterceptors().add(new PolicyBasedWSS4JInInterceptor());
        endpointImpl.getOutInterceptors().add(new PolicyBasedWSS4JOutInterceptor());

        endpointImpl.getFeatures().add(new LoggingFeature());
    }

    public void extendTrustStore() {
        Path trustStorePath = trustStoreSettings.getPath(TrustStore.PATH, confFolder);
        if (trustStorePath.endsWith("None")) {
            return;
        }

        try {

            KeyStore extraTrustStore;
            extraTrustStore = KeyStore.getInstance("jks");
            try (InputStream is = Files.newInputStream(trustStorePath)) {
                extraTrustStore.load(is, trustStoreSettings.getString(TrustStore.PASSWORD).toCharArray());
            }

            Enumeration<String> aliases = extraTrustStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (!this.trustStore.containsAlias(alias)) {
                    log.info("Adding {} to truststore", alias);
                    this.trustStore.setCertificateEntry(alias, extraTrustStore.getCertificate(alias));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format("Unable to load TrustStore: %s", trustStorePath), e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException {
        try {
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("Hello AS4 world\n");
        } catch (IOException e) {
            throw new ServletException("Unable to send response", e);
        }
    }
}
