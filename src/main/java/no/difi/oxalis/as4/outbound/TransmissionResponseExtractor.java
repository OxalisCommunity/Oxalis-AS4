package no.difi.oxalis.as4.outbound;

import no.difi.oxalis.api.lang.TimestampException;
import no.difi.oxalis.api.model.Direction;
import no.difi.oxalis.api.model.TransmissionIdentifier;
import no.difi.oxalis.api.outbound.TransmissionRequest;
import no.difi.oxalis.api.timestamp.Timestamp;
import no.difi.oxalis.api.timestamp.TimestampProvider;
import no.difi.oxalis.as4.util.Marshalling;
import no.difi.oxalis.commons.bouncycastle.BCHelper;
import no.difi.vefa.peppol.common.code.DigestMethod;
import no.difi.vefa.peppol.common.model.Digest;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.SignalMessage;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.client.core.WebServiceMessageExtractor;
import org.springframework.ws.soap.SoapMessage;
import org.w3c.dom.NodeList;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.TransformerException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static no.difi.oxalis.as4.util.Constants.DIGEST_ALGORITHM_SHA256;

public class TransmissionResponseExtractor implements WebServiceMessageExtractor<As4TransmissionResponse> {

    private TransmissionRequest request;
    private TimestampProvider timestampProvider;

    public TransmissionResponseExtractor(TransmissionRequest request, TimestampProvider timestampProvider) {
        this.request = request;
        this.timestampProvider = timestampProvider;
    }

    @Override
    public As4TransmissionResponse extractData(WebServiceMessage webServiceMessage) throws IOException, TransformerException {
        SoapMessage soapMessage = (SoapMessage) webServiceMessage;

        SignalMessage signalMessage = getSignalMessage(soapMessage);
        String refToMessageId = signalMessage.getMessageInfo().getRefToMessageId();
        TransmissionIdentifier ti = TransmissionIdentifier.of(refToMessageId);

        Timestamp ts;
        try {
            ts = timestampProvider.generate(null, Direction.OUT);
        } catch (TimestampException e) {
            throw new TransformerException("Could not create timestamp", e);
        }

        MessageDigest md;
        try {
            md = BCHelper.getMessageDigest(DIGEST_ALGORITHM_SHA256);
        } catch (NoSuchAlgorithmException e) {
            throw new TransformerException("Could not create message digest", e);
        }
        Digest digest = Digest.of(DigestMethod.SHA256, md.digest());

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        soapMessage.writeTo(bos);

        return new As4TransmissionResponse(
                ti,
                request,
                digest,
                bos.toByteArray(),
                ts,
                ts.getDate()
        );
    }

    private SignalMessage getSignalMessage(SoapMessage soapMessage) throws TransformerException {
        NodeList signalNode = soapMessage.getDocument().getElementsByTagNameNS("*", "SignalMessage");
        if (signalNode.getLength() != 1) {
            throw new TransformerException("SOAP header contains zero or multiple SignalMessage elements, should only contain one");
        }
        try {
            Unmarshaller unmarshaller = Marshalling.getInstance().getJaxbContext().createUnmarshaller();
            return unmarshaller.unmarshal(signalNode.item(0), SignalMessage.class).getValue();
        } catch (JAXBException e) {
            throw new TransformerException("Could not create unmarshaller", e);
        }
    }
}
