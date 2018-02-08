package no.difi.oxalis.as4.outbound;

import com.google.inject.Inject;
import no.difi.oxalis.api.outbound.TransmissionRequest;
import no.difi.oxalis.api.outbound.TransmissionResponse;
import no.difi.oxalis.api.settings.Settings;
import no.difi.oxalis.as4.util.Marshalling;
import no.difi.oxalis.commons.security.KeyStoreConf;
import org.apache.wss4j.common.WSS4JConstants;
import org.apache.wss4j.common.crypto.Merlin;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.soap.SoapVersion;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.springframework.ws.transport.http.HttpComponentsMessageSender;

import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import static no.difi.oxalis.as4.util.Constants.RSA_SHA256;

public class As4MessageSender {

    private X509Certificate certificate;
    private KeyStore keyStore;
    private Settings<KeyStoreConf> settings;

    @Inject
    public As4MessageSender(X509Certificate certificate, KeyStore keyStore, Settings<KeyStoreConf> settings) {
        this.certificate = certificate;
        this.keyStore = keyStore;
        this.settings = settings;
    }

    public TransmissionResponse send(TransmissionRequest request) {
        WebServiceTemplate template = createTemplate();
        As4Sender sender = new As4Sender(request, certificate);
        template.sendAndReceive(request.getEndpoint().getAddress().toString(), sender, new TransmissionResponseExtractor());
        return null;
    }

    private SaajSoapMessageFactory createSoapMessageFactory() {
        SaajSoapMessageFactory factory;
        try {
            factory = new SaajSoapMessageFactory(MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL));
            factory.setSoapVersion(SoapVersion.SOAP_12);
            factory.afterPropertiesSet();
            return factory;
        } catch (SOAPException e) {
            throw new RuntimeException("Error creating SoapMessageFactory", e);
        }
    }

    private WebServiceTemplate createTemplate() {
        As4WebServiceTemplate template = new As4WebServiceTemplate(createSoapMessageFactory());
        template.setMarshaller(Marshalling.getInstance());
        template.setUnmarshaller(Marshalling.getInstance());
        template.setMessageSender(createMessageSender());
        template.setInterceptors(new ClientInterceptor[] {createWsSecurityInterceptor()});
        return template;
    }

    private ClientInterceptor createWsSecurityInterceptor() {
        WsSecurityInterceptor interceptor = new WsSecurityInterceptor();
        Merlin crypto = new Merlin();
        crypto.setCryptoProvider(BouncyCastleProvider.PROVIDER_NAME);
        crypto.setKeyStore(keyStore);
        interceptor.setSecurementSignatureCrypto(crypto);
        interceptor.setValidationSignatureCrypto(crypto);
        interceptor.setSecurementEncryptionCrypto(crypto);

        interceptor.setSecurementSignatureAlgorithm(RSA_SHA256);
        interceptor.setSecurementSignatureDigestAlgorithm(DigestMethod.SHA256);
        interceptor.setSecurementEncryptionSymAlgorithm(WSS4JConstants.AES_128_GCM);

        String alias = settings.getString(KeyStoreConf.KEY_ALIAS);
        String password = settings.getString(KeyStoreConf.PASSWORD);
        interceptor.setSecurementSignatureUser(alias);
        interceptor.setSecurementEncryptionUser(alias);
        interceptor.setSecurementPassword(password);
        interceptor.setSecurementActions("Signature Encrypt");
        interceptor.setSecurementSignatureKeyIdentifier("DirectReference");

        interceptor.setSecurementSignatureParts("{}{}Body; {}cid:Attachments");
        interceptor.setSecurementEncryptionParts("{}{} Body; {}cid:Attachments");

        try {
            interceptor.afterPropertiesSet();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return interceptor;
    }

    private HttpComponentsMessageSender createMessageSender() {
        HttpComponentsMessageSender messageSender = new HttpComponentsMessageSender();
        return messageSender;
    }
}
