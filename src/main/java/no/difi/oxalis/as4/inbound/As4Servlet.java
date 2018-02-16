package no.difi.oxalis.as4.inbound;

import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import no.difi.oxalis.api.settings.Settings;
import no.difi.oxalis.commons.security.KeyStoreConf;
import org.apache.cxf.BusFactory;
import org.apache.cxf.jaxws.EndpointImpl;
import org.apache.cxf.transport.servlet.CXFNonSpringServlet;
import org.apache.wss4j.common.crypto.Merlin;
import org.apache.wss4j.dom.handler.WSHandlerConstants;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServletRequest;
import javax.xml.soap.MimeHeaders;
import javax.xml.ws.Endpoint;
import java.security.KeyStore;
import java.security.Security;
import java.util.Enumeration;
import java.util.Map;
import java.util.StringTokenizer;

@Singleton
public class As4Servlet extends CXFNonSpringServlet {

    @Inject
    private KeyStore keyStore;

    @Inject
    private Settings<KeyStoreConf> settings;

    @Override
    protected void loadBus(ServletConfig servletConfig) {
        super.loadBus(servletConfig);
        BusFactory.setDefaultBus(getBus());
        EndpointImpl endpointImpl = (EndpointImpl) Endpoint.publish("/", new As4Provider());

        Security.addProvider(new BouncyCastleProvider());
        Merlin encryptCrypto = new Merlin();
        encryptCrypto.setCryptoProvider(BouncyCastleProvider.PROVIDER_NAME);
        encryptCrypto.setKeyStore(keyStore);


        // Properties
        Map<String, Object> inProps = Maps.newHashMap();
        inProps.put(WSHandlerConstants.ACTION, WSHandlerConstants.ENCRYPT+" "+WSHandlerConstants.SIGNATURE);
        String alias = settings.getString(KeyStoreConf.KEY_ALIAS);
        String password = settings.getString(KeyStoreConf.KEY_PASSWORD);
        PasswordCallbackHandler cb = new PasswordCallbackHandler(password);
        inProps.put(WSHandlerConstants.PW_CALLBACK_REF, cb);
        inProps.put(WSHandlerConstants.ENCRYPTION_PARTS, "{}cid:Attachments");
        inProps.put(WSHandlerConstants.SIGNATURE_PARTS, "{}{}Body; {}cid:Attachments");
        inProps.put(WSHandlerConstants.SIG_KEY_ID, "DirectReference");
        inProps.put(WSHandlerConstants.USE_SINGLE_CERTIFICATE, "true");
        inProps.put(WSHandlerConstants.USE_REQ_SIG_CERT, "true");

        WsInInterceptor interceptor = new WsInInterceptor(inProps, encryptCrypto, alias);
        org.apache.cxf.endpoint.Endpoint endpoint = endpointImpl.getServer().getEndpoint();
        endpoint.getInInterceptors().add(interceptor);
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
}
