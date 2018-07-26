package no.difi.oxalis.as4.inbound;

import no.difi.vefa.peppol.security.api.CertificateValidator;
import org.apache.wss4j.common.crypto.Crypto;
import org.apache.wss4j.common.ext.WSSecurityException;
import org.apache.wss4j.dom.handler.RequestData;
import org.apache.wss4j.dom.validate.SignatureTrustValidator;

import java.security.PublicKey;
import java.security.cert.X509Certificate;

public class CertificateValidatorSignatureTrustValidator extends SignatureTrustValidator {


    private final CertificateValidator certificateValidator;

    public CertificateValidatorSignatureTrustValidator(CertificateValidator certificateValidator) {
        this.certificateValidator = certificateValidator;
    }

    @Override
    protected void validateCertificates(X509Certificate[] certificates) throws WSSecurityException {
        super.validateCertificates(certificates);
    }

    @Override
    protected Crypto getCrypto(RequestData data) {
        return super.getCrypto(data);
    }

    @Override
    protected void verifyTrustInCerts(X509Certificate[] certificates, Crypto crypto, RequestData data, boolean enableRevocation) throws WSSecurityException {
        super.verifyTrustInCerts(certificates, crypto, data, enableRevocation);
    }

    @Override
    protected void validatePublicKey(PublicKey publicKey, Crypto crypto) throws WSSecurityException {
        super.validatePublicKey(publicKey, crypto);
    }
}


