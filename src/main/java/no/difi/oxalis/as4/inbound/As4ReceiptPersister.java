package no.difi.oxalis.as4.inbound;

import com.google.inject.Inject;
import no.difi.oxalis.api.persist.ReceiptPersister;
import no.difi.oxalis.as4.lang.OxalisAs4Exception;
import no.difi.vefa.peppol.common.model.TransportProfile;

import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class As4ReceiptPersister {

    private final ReceiptPersister receiptPersister;

    @Inject
    public As4ReceiptPersister(ReceiptPersister receiptPersister) {
        this.receiptPersister = receiptPersister;
    }

    void persistReceipt(As4InboundInfo inboundInfo, SOAPMessage response) throws OxalisAs4Exception {
        try {
            receiptPersister.persist(
                    getAs4InboundMetadata(inboundInfo, response),
                    inboundInfo.getPayloadPath());
        } catch (IOException e) {
            throw new OxalisAs4Exception("Error persisting AS4 metadata", e);
        }
    }

    private As4InboundMetadata getAs4InboundMetadata(As4InboundInfo inboundInfo, SOAPMessage response) throws OxalisAs4Exception {
        return new As4InboundMetadata(
                inboundInfo.getTransmissionIdentifier(),
                inboundInfo.getSbdh(),
                inboundInfo.getTimestamp(),
                TransportProfile.AS4,
                inboundInfo.getAttachmentDigest(),
                inboundInfo.getSenderCertificate(),
                getResponseAsByteArray(response)
        );
    }

    private byte[] getResponseAsByteArray(SOAPMessage response) throws OxalisAs4Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            response.writeTo(bos);
        } catch (SOAPException | IOException e) {
            throw new OxalisAs4Exception("Could not write SOAP response", e);
        }
        return bos.toByteArray();
    }

}
