package no.difi.oxalis.as4.inbound;

import com.google.inject.Inject;
import com.google.inject.Provides;
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
import java.util.Enumeration;
import java.util.Optional;

@Slf4j
@Singleton
public class InboundMerlinProvider {

    @Inject
    @Named("conf")
    private Path confFolder;

    @Inject
    private KeyStore keyStore;

    @Inject
    private Settings<TrustStoreSettings> trustStoreSettings;

    @Inject(optional = true)
    @Named("truststore-ap")
    private KeyStore trustStoreAp;

    public Merlin getMerlin() {
        Merlin merlin = new Merlin();
        merlin.setCryptoProvider(BouncyCastleProvider.PROVIDER_NAME);
        merlin.setKeyStore(keyStore);
        merlin.setTrustStore(getTrustStore());
        return merlin;
    }

    private KeyStore getTrustStore() {
        Optional<KeyStore> trustStoreExtension = loadTrustStoreExtension(trustStoreSettings, confFolder);

        if (trustStoreAp != null) {
            trustStoreExtension.ifPresent(p ->
                    extendKeyStore(trustStoreAp, p)
            );

            return trustStoreAp;
        }

        return trustStoreExtension
                .orElseThrow(() -> new OxalisLoadingException("Expected a truststore. Please specify the property oxalis.truststore.path"));
    }

    private void extendKeyStore(KeyStore trustStoreAp, KeyStore trustStoreExtension) {
        try {
            Enumeration<String> aliases = trustStoreExtension.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (!trustStoreAp.containsAlias(alias)) {
                    log.info("Adding {} to truststore", alias);
                    trustStoreAp.setCertificateEntry(alias, trustStoreExtension.getCertificate(alias));
                }
            }
        } catch (KeyStoreException e) {
            throw new OxalisLoadingException("Something went wrong during extension of key store.", e);
        }
    }

    private Optional<KeyStore> loadTrustStoreExtension(Settings<TrustStoreSettings> settings, Path confFolder) {
        Path path = settings.getPath(TrustStoreSettings.PATH, confFolder);

        if (path.endsWith("None")) {
            return Optional.empty();
        }

        try {
            KeyStore keystore = KeyStore.getInstance("JKS");

            if (!path.toFile().exists())
                throw new OxalisLoadingException(String.format("Unable to find extension keystore at '%s'.", path));

            try (InputStream inputStream = Files.newInputStream(path)) {
                keystore.load(inputStream, settings.getString(TrustStoreSettings.PASSWORD).toCharArray());
            }
            return Optional.of(keystore);
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            throw new OxalisLoadingException("Something went wrong during handling of key store.", e);
        } catch (IOException e) {
            throw new OxalisLoadingException(String.format("Error during reading of '%s'.", path), e);
        }
    }
}