package no.difi.oxalis.as4.inbound;

import com.google.inject.Inject;
import no.difi.oxalis.api.lang.VerifierException;
import no.difi.oxalis.api.model.Direction;
import no.difi.oxalis.api.transmission.TransmissionVerifier;
import no.difi.oxalis.as4.lang.OxalisAs4Exception;
import no.difi.vefa.peppol.common.model.Header;
import no.difi.vefa.peppol.sbdh.SbdReader;
import no.difi.vefa.peppol.sbdh.lang.SbdhException;

import java.io.IOException;
import java.io.InputStream;

public class SBDHProvider {

    private final TransmissionVerifier transmissionVerifier;

    @Inject
    public SBDHProvider(TransmissionVerifier transmissionVerifier) {
        this.transmissionVerifier = transmissionVerifier;
    }

    Header getValidSBDH(InputStream inputStream) throws OxalisAs4Exception {
        Header sbdh = getSbdh(inputStream);
        validateSBDH(sbdh);
        return sbdh;
    }

    private void validateSBDH(Header sbdh) throws OxalisAs4Exception {
        try {
            transmissionVerifier.verify(sbdh, Direction.IN);
        } catch (VerifierException e) {
            throw new OxalisAs4Exception("Error verifying SBDH", e);
        }
    }

    private Header getSbdh(InputStream is) throws OxalisAs4Exception {
        try (SbdReader sbdReader = SbdReader.newInstance(is)) {
            return sbdReader.getHeader();
        } catch (SbdhException | IOException e) {
            throw new OxalisAs4Exception("Could not extract SBDH from payload");
        }
    }
}
