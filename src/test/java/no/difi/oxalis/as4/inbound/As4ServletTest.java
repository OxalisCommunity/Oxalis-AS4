package no.difi.oxalis.as4.inbound;

import no.difi.oxalis.api.settings.Settings;
import no.difi.oxalis.as4.config.TrustStore;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.reflect.Whitebox;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class As4ServletTest {

    @Mock
    private Settings<TrustStore> trustStoreSettings;

    @Mock
    private Path confFolder;

    private KeyStore trustStore = null;

    @InjectMocks
    private As4Servlet servlet = new As4Servlet();


    private KeyStore generateEmptyKeyStore(){
        try {
            KeyStore ks = KeyStore.getInstance("jks");
            ks.load(null, null);

            return ks;
        }catch (Exception e){
            return null;
        }
    }

    @BeforeTest
    public void beforeTest(){
        MockitoAnnotations.initMocks(this);
    }


    @BeforeMethod
    public void beforeMethod (){
        trustStore = generateEmptyKeyStore();
        Whitebox.setInternalState(servlet, "trustStore", trustStore);
    }


    @Test
    public void extendTrustStore_validLocation() throws Exception{

        Path path = new File(getClass().getClassLoader()
                .getResource("oxalis_home/peppol_trust_g2.jks").getFile()).toPath();

        when(trustStoreSettings.getPath(any(TrustStore.class), any(Path.class))).thenReturn(path);
        when(trustStoreSettings.getString(any(TrustStore.class))).thenReturn("changeit");

        assertEquals(trustStore.size(), 0, "precondition: trustStore empty");
        servlet.extendTrustStore();
        assertEquals(trustStore.size(), 4, "trustStore expanded with 4 entries");

    }

    @Test
    public void extendTrustStore_noneLocation() throws Exception{
        Path path = Paths.get("None");
        when(trustStoreSettings.getPath(any(TrustStore.class), any(Path.class))).thenReturn(path);

        assertEquals(trustStore.size(), 0, "precondition: trustStore empty");
        servlet.extendTrustStore();
        assertEquals(trustStore.size(), 0, "trustStore remains empty");
    }
}
