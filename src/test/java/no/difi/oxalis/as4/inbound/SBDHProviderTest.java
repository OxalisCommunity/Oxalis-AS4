package no.difi.oxalis.as4.inbound;

import no.difi.oxalis.api.lang.VerifierException;
import no.difi.oxalis.api.model.Direction;
import no.difi.oxalis.api.transmission.TransmissionVerifier;
import no.difi.oxalis.as4.lang.OxalisAs4Exception;
import no.difi.vefa.peppol.common.model.Header;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class SBDHProviderTest {

    @Mock TransmissionVerifier transmissionVerifier;
    @InjectMocks SBDHProvider sBDHProvider;

    @Test
    public void testGetValidSBDH() throws Exception {
        InputStream is = getClass().getResourceAsStream("/as2-peppol-bis-invoice-sbdh.xml");
        Header sbdh = sBDHProvider.getValidSBDH(is);

        assertThat(sbdh)
                .hasFieldOrPropertyWithValue("sender.scheme.identifier", "iso6523-actorid-upis")
                .hasFieldOrPropertyWithValue("sender.identifier", "0007:5567125082")
                .hasFieldOrPropertyWithValue("receiver.scheme.identifier", "iso6523-actorid-upis")
                .hasFieldOrPropertyWithValue("receiver.identifier", "0007:4455454480");

        verify(transmissionVerifier).verify(same(sbdh), same(Direction.IN));
    }

    @Test
    public void testGetInvalidSBDH() throws Exception {
        InputStream is = getClass().getResourceAsStream("/as2-peppol-bis-invoice-sbdh.xml");

        VerifierException verifierException = VerifierException.becauseOf(VerifierException.Reason.DOCUMENT_TYPE, "Something is wrong");
        doThrow(verifierException).when(transmissionVerifier).verify(any(), any());

        assertThatThrownBy(() -> sBDHProvider.getValidSBDH(is))
                .isInstanceOf(OxalisAs4Exception.class)
                .hasMessage("Error verifying SBDH")
                .hasCause(verifierException);
    }
}
