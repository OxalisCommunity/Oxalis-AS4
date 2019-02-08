package no.difi.oxalis.as4.inbound;

import lombok.Builder;
import lombok.Value;
import no.difi.oxalis.api.model.TransmissionIdentifier;
import no.difi.oxalis.api.timestamp.Timestamp;
import no.difi.vefa.peppol.common.model.Digest;
import no.difi.vefa.peppol.common.model.Header;
import org.w3.xmldsig.ReferenceType;

import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.List;

@Value
@Builder
class As4InboundInfo {

    private final TransmissionIdentifier transmissionIdentifier;
    private final Header sbdh;
    private final Timestamp timestamp;
    private final Path payloadPath;
    private final List<ReferenceType> referenceListFromSignedInfo;
    private final Digest attachmentDigest;
    private final X509Certificate senderCertificate;
}
