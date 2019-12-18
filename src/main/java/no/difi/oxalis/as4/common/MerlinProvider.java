package no.difi.oxalis.as4.common;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.apache.wss4j.common.crypto.Merlin;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.KeyStore;

@Singleton
public class MerlinProvider {

    @Inject
    @Named("truststore-ap")
    private KeyStore trustStore;
    @Inject
    private KeyStore keyStore;

    public Merlin getMerlib() {
        Merlin merlin = new Merlin();
        merlin.setCryptoProvider(BouncyCastleProvider.PROVIDER_NAME);
        merlin.setKeyStore(keyStore);
        merlin.setTrustStore(trustStore);
        return merlin;
    }
}
