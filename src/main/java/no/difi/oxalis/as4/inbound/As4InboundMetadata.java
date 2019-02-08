package no.difi.oxalis.as4.inbound;

import no.difi.oxalis.api.inbound.InboundMetadata;
import no.difi.oxalis.api.model.TransmissionIdentifier;
import no.difi.oxalis.api.tag.Tag;
import no.difi.oxalis.api.timestamp.Timestamp;
import no.difi.vefa.peppol.common.model.*;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class As4InboundMetadata implements InboundMetadata {

    private final TransmissionIdentifier transmissionIdentifier;
    private final Header header;
    private final Date timestamp;
    private final TransportProfile transportProfile;
    private final Digest digest;
    private final Receipt primaryReceipt;
    private final List<Receipt> receipts;
    private final X509Certificate certificate;

    As4InboundMetadata(TransmissionIdentifier transmissionIdentifier, Header header, Timestamp timestamp,
                       TransportProfile transportProfile, Digest digest, X509Certificate certificate,
                       byte[] primaryReceipt) {
        this.transmissionIdentifier = transmissionIdentifier;
        this.header = header;
        this.timestamp = timestamp.getDate();
        this.transportProfile = transportProfile;
        this.digest = digest;
        this.certificate = certificate;
        this.primaryReceipt = Receipt.of("message/disposition-notification", primaryReceipt);
        this.receipts = getReceipts(this.primaryReceipt, timestamp);
    }

    private List<Receipt> getReceipts(Receipt primaryReceipt, Timestamp timestamp) {
        List<Receipt> receiptList = new ArrayList<>();
        receiptList.add(primaryReceipt);
        timestamp.getReceipt().ifPresent(receiptList::add);
        return Collections.unmodifiableList(receiptList);
    }

    @Override
    public X509Certificate getCertificate() {
        return certificate;
    }

    @Override
    public TransmissionIdentifier getTransmissionIdentifier() {
        return transmissionIdentifier;
    }

    @Override
    public Header getHeader() {
        return header;
    }

    @Override
    public Date getTimestamp() {
        return timestamp;
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
    public TransportProfile getProtocol() {
        return transportProfile;
    }

    @Override
    public List<Receipt> getReceipts() {
        return receipts;
    }

    @Override
    public Receipt primaryReceipt() {
        return primaryReceipt;
    }

    @Override
    public Tag getTag() {
        return Tag.NONE;
    }
}
