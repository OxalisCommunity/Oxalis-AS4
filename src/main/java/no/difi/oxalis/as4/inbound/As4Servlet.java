package no.difi.oxalis.as4.inbound;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import no.difi.oxalis.api.settings.Settings;
import no.difi.oxalis.as4.config.TrustStore;
import no.difi.oxalis.commons.security.KeyStoreConf;
import org.apache.cxf.jaxws.EndpointImpl;
import org.apache.cxf.transport.servlet.CXFNonSpringServlet;
import org.apache.cxf.ws.security.SecurityConstants;
import org.apache.cxf.ws.security.wss4j.PolicyBasedWSS4JInInterceptor;
import org.apache.cxf.ws.security.wss4j.PolicyBasedWSS4JOutInterceptor;
import org.apache.wss4j.common.ConfigurationConstants;
import org.apache.wss4j.common.crypto.Merlin;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

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

@Singleton
public class As4Servlet extends CXFNonSpringServlet {

    @Inject
    @Named("truststore-ap")
    private KeyStore trustStore;

    @Inject
    private KeyStore keyStore;

    @Inject
    private Settings<KeyStoreConf> settings;

    @Inject
    private Settings<TrustStore> trustStoreSettings;

    @Inject
    @Named("conf")
    private Path confFolder;

    @Inject
    private As4EndpointsPublisher endpointsPublisher;

    @Override
    protected void loadBus(ServletConfig servletConfig) {
        super.loadBus(servletConfig);

        extendTrustStore();

        EndpointImpl endpointImpl = endpointsPublisher.publish(getBus());

        Merlin encryptCrypto = new Merlin();
        encryptCrypto.setCryptoProvider(BouncyCastleProvider.PROVIDER_NAME);
        encryptCrypto.setKeyStore(keyStore);
        encryptCrypto.setTrustStore(trustStore);

        endpointImpl.getProperties().put(SecurityConstants.SIGNATURE_CRYPTO, encryptCrypto);
        endpointImpl.getProperties().put(SecurityConstants.SIGNATURE_PASSWORD, settings.getString(KeyStoreConf.KEY_PASSWORD));
        endpointImpl.getProperties().put(SecurityConstants.SIGNATURE_USERNAME, settings.getString(KeyStoreConf.KEY_ALIAS));
        endpointImpl.getProperties().put(ConfigurationConstants.SIG_VER_PROP_REF_ID, "oxalisTrustStore");

        endpointImpl.getProperties().put(SecurityConstants.ENCRYPT_CRYPTO, encryptCrypto);
        endpointImpl.getProperties().put(SecurityConstants.ENCRYPT_USERNAME, settings.getString(KeyStoreConf.KEY_ALIAS));
        endpointImpl.getProperties().put(ConfigurationConstants.DEC_PROP_REF_ID, "oxalisAPCrypto");

        endpointImpl.getInInterceptors().add(new PolicyBasedWSS4JInInterceptor());
        endpointImpl.getOutInterceptors().add(new PolicyBasedWSS4JOutInterceptor());


    }

    public void extendTrustStore() {
        Path trustStorePath = trustStoreSettings.getPath(TrustStore.PATH, confFolder);
        if(trustStorePath.endsWith("None")){
            return;
        }

        try {

            KeyStore extraTrustStore;
            extraTrustStore = KeyStore.getInstance("jks");
            try (InputStream is = Files.newInputStream(trustStorePath)) {
                extraTrustStore.load(is, trustStoreSettings.getString(TrustStore.PASSWORD).toCharArray());
            }

            Enumeration<String> aliases = extraTrustStore.aliases();
            while(aliases.hasMoreElements()){
                String alias = aliases.nextElement();
                if(!this.trustStore.containsAlias(alias)){
                    this.trustStore.setCertificateEntry(alias, extraTrustStore.getCertificate(alias));
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Unable to load TrustStore!");
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
