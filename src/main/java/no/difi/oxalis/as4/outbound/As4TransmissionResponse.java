package no.difi.oxalis.as4.outbound;

import no.difi.oxalis.api.model.TransmissionIdentifier;
import no.difi.oxalis.api.outbound.TransmissionRequest;
import no.difi.oxalis.api.outbound.TransmissionResponse;
import no.difi.oxalis.api.timestamp.Timestamp;
import no.difi.oxalis.as4.lang.OxalisAs4TransmissionException;
import no.difi.vefa.peppol.common.model.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class As4TransmissionResponse implements TransmissionResponse {

    // Original transmission request is kept to allow easy access to immutable objects part of the request.
    private final TransmissionRequest transmissionRequest;
    private final TransmissionIdentifier transmissionIdentifier;
    private final Digest digest;
    private final Receipt receipt;
    private final List<Receipt> receipts;
    private final Date timestamp;
    private final OxalisAs4TransmissionException transmissionException;

    public As4TransmissionResponse(TransmissionIdentifier transmissionIdentifier,
                                   TransmissionRequest transmissionRequest, Digest digest,
                                   byte[] nativeEvidenceBytes, Timestamp timestamp, Date date) {
        this.transmissionIdentifier = transmissionIdentifier;
        this.transmissionRequest = transmissionRequest;
        this.digest = digest;
        this.receipt = Receipt.of("message/disposition-notification", nativeEvidenceBytes);
        this.timestamp = date;
        transmissionException = null;

        List<Receipt> receipts = new ArrayList<>();
        receipts.add(receipt);
        if (timestamp.getReceipt().isPresent())
            receipts.add(timestamp.getReceipt().get());
        this.receipts = Collections.unmodifiableList(receipts);
    }
    public As4TransmissionResponse(OxalisAs4TransmissionException transmissionException, TransmissionIdentifier transmissionIdentifier){
        this.transmissionRequest = null;
        this.transmissionIdentifier = transmissionIdentifier;
        this.transmissionException = transmissionException;
        this.digest = null;
        this.receipt = null;
        this.receipts = null;
        this.timestamp = null;
    }

    @Override
    public Header getHeader() {
        return transmissionRequest.getHeader();
    }

    public TransmissionIdentifier getTransmissionIdentifier() {
        return transmissionIdentifier;
    }

    @Override
    public List<Receipt> getReceipts() {
        return receipts;
    }

    @Override
    public Endpoint getEndpoint() {
        return transmissionRequest.getEndpoint();
    }

    @Override
    public Receipt primaryReceipt() {
        return receipt;
    }

    @Override
    public Digest getDigest() {
        return digest;
    }

    @Override
    public TransportProtocol getTransportProtocol() {
        return TransportProtocol.AS4;
    }

    @Override
    public Date getTimestamp() {
        return timestamp;
    }

    public OxalisAs4TransmissionException getTransmissionException() {
        return transmissionException;
    }
}
