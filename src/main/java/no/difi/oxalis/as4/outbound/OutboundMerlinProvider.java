package no.difi.oxalis.as4.outbound;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;
import no.difi.oxalis.api.lang.OxalisLoadingException;
import no.difi.oxalis.api.settings.Settings;
import no.difi.oxalis.as4.config.TrustStoreSettings;
import org.apache.wss4j.common.crypto.Merlin;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

@Slf4j
@Singleton
public class OutboundMerlinProvider {

    @Inject
    @Named("conf")
    private Path confFolder;

    @Inject
    private KeyStore keyStore;

    @Inject
    private Settings<TrustStoreSettings> trustStoreSettings;

    public Merlin getMerlin() {
        Merlin merlin = new Merlin();
        merlin.setCryptoProvider(BouncyCastleProvider.PROVIDER_NAME);
        merlin.setKeyStore(keyStore);
        merlin.setTrustStore(getTrustStore());
        return merlin;
    }

    private KeyStore getTrustStore() {
        Path path = trustStoreSettings.getPath(TrustStoreSettings.PATH, confFolder);

        if (path.endsWith("None")) {
            throw new OxalisLoadingException("Expected a truststore. Please specify the property oxalis.truststore.path");
        }

        try {
            KeyStore keystore = KeyStore.getInstance("JKS");

            if (!path.toFile().exists())
                throw new OxalisLoadingException(String.format("Unable to find extension keystore at '%s'.", path));

            try (InputStream inputStream = Files.newInputStream(path)) {
                keystore.load(inputStream, trustStoreSettings.getString(TrustStoreSettings.PASSWORD).toCharArray());
            }
            return keystore;
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            throw new OxalisLoadingException("Something went wrong during handling of key store.", e);
        } catch (IOException e) {
            throw new OxalisLoadingException(String.format("Error during reading of '%s'.", path), e);
        }
    }
}