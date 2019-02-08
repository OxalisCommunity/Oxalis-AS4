package no.difi.oxalis.as4.inbound;

import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import no.difi.oxalis.api.settings.Settings;
import no.difi.oxalis.as4.config.TrustStore;
import no.difi.oxalis.as4.util.Constants;
import no.difi.oxalis.commons.security.KeyStoreConf;
import no.difi.vefa.peppol.security.api.CertificateValidator;
import org.apache.cxf.binding.soap.interceptor.SoapInterceptor;
import org.apache.cxf.jaxws.EndpointImpl;
import org.apache.cxf.transport.servlet.CXFNonSpringServlet;
import org.apache.cxf.ws.security.SecurityConstants;
import org.apache.wss4j.common.ConfigurationConstants;
import org.apache.wss4j.common.crypto.Merlin;
import org.apache.wss4j.dom.handler.WSHandlerConstants;
import org.apache.wss4j.policy.SPConstants;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.soap.MimeHeaders;
import javax.xml.ws.Endpoint;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Enumeration;
import java.util.Map;
import java.util.StringTokenizer;

@Singleton
public class As4Servlet extends CXFNonSpringServlet {

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
    private As4Provider provider;

    @Inject
    private CertificateValidator certificateValidator;

    private static final Merlin encryptCrypto = new Merlin();

    @Override
    protected void loadBus(ServletConfig servletConfig) {
        super.loadBus(servletConfig);

        encryptCrypto.setCryptoProvider(BouncyCastleProvider.PROVIDER_NAME);
        encryptCrypto.setKeyStore(keyStore);
        encryptCrypto.setTrustStore(getTrustStore());

        EndpointImpl endpointImpl = (EndpointImpl) Endpoint.publish("/", provider);
        endpointImpl.getInInterceptors().add(createWsInInterceptor());
        endpointImpl.getOutInterceptors().add(createWsOutInterceptor());
    }

    private KeyStore getTrustStore() {
        try {
            KeyStore trustStore;
            trustStore = KeyStore.getInstance("jks");
            Path path = trustStoreSettings.getPath(TrustStore.PATH, confFolder);
            try (InputStream is = Files.newInputStream(path)) {
                trustStore.load(is, trustStoreSettings.getString(TrustStore.PASSWORD).toCharArray());
            }
            return trustStore;
        } catch (Exception e) {
            throw new RuntimeException("Unable to load TrustStore!");
        }
    }

    private SoapInterceptor createWsInInterceptor() {
        String alias = settings.getString(KeyStoreConf.KEY_ALIAS);

        Map<String, Object> inProps = Maps.newHashMap();
        inProps.put(WSHandlerConstants.ACTION, WSHandlerConstants.ENCRYPT + " " + WSHandlerConstants.SIGNATURE);
        inProps.put(WSHandlerConstants.PW_CALLBACK_REF, getPasswordCallbackHandler());
        inProps.put(WSHandlerConstants.ENCRYPTION_PARTS, "{}cid:Attachments");
        inProps.put(WSHandlerConstants.SIGNATURE_PARTS, "{}{}Body; {}{http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/} Messaging; {}cid:Attachments;");
        inProps.put(WSHandlerConstants.SIG_KEY_ID, "DirectReference");
        inProps.put(WSHandlerConstants.USE_SINGLE_CERTIFICATE, "true");
        inProps.put(WSHandlerConstants.USE_REQ_SIG_CERT, "true");
        inProps.put(SecurityConstants.SIGNATURE_TOKEN_VALIDATOR, new CertificateValidatorSignatureTrustValidator(certificateValidator));

        return new OxalisAS4WsInInterceptor(inProps, encryptCrypto, alias);
    }

    private PasswordCallbackHandler getPasswordCallbackHandler() {
        String password = settings.getString(KeyStoreConf.KEY_PASSWORD);
        return new PasswordCallbackHandler(password);
    }

    private SoapInterceptor createWsOutInterceptor() {

        String alias = settings.getString(KeyStoreConf.KEY_ALIAS);

        Map<String, Object> outProps = Maps.newHashMap();
        outProps.put(WSHandlerConstants.ACTION, WSHandlerConstants.SIGNATURE);
        outProps.put(WSHandlerConstants.PW_CALLBACK_REF, getPasswordCallbackHandler());
        outProps.put(WSHandlerConstants.SIGNATURE_PARTS, "{}{}Body; {}{http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/} Messaging;{}cid:Attachments;");
        outProps.put(WSHandlerConstants.SIG_KEY_ID, "SKIKeyIdentifier");
        outProps.put(WSHandlerConstants.USE_SINGLE_CERTIFICATE, "true");
        outProps.put(WSHandlerConstants.USE_REQ_SIG_CERT, "true");
        outProps.put(ConfigurationConstants.USER, alias);
        outProps.put(WSHandlerConstants.SIG_ALGO, Constants.RSA_SHA256);
        outProps.put(WSHandlerConstants.SIG_DIGEST_ALGO, SPConstants.SHA256);
        outProps.put(SecurityConstants.ENCRYPT_CRYPTO, encryptCrypto);
        outProps.put(ConfigurationConstants.SIG_PROP_REF_ID, SecurityConstants.ENCRYPT_CRYPTO);
        outProps.put(WSHandlerConstants.ENABLE_SIGNATURE_CONFIRMATION, "false");
        outProps.put(SecurityConstants.SIGNATURE_TOKEN_VALIDATOR, new CertificateValidatorSignatureTrustValidator(certificateValidator));

        return new OxalisAs4WsOutInterceptor(outProps, encryptCrypto, alias);
    }

    private MimeHeaders getHeaders(HttpServletRequest httpServletRequest) {
        Enumeration<?> enumeration = httpServletRequest.getHeaderNames();
        MimeHeaders headers = new MimeHeaders();
        while (enumeration.hasMoreElements()) {
            String headerName = (String) enumeration.nextElement();
            String headerValue = httpServletRequest.getHeader(headerName);
            StringTokenizer values = new StringTokenizer(headerValue, ",");
            while (values.hasMoreTokens()) {
                headers.addHeader(headerName, values.nextToken().trim());
            }
        }
        return headers;
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
