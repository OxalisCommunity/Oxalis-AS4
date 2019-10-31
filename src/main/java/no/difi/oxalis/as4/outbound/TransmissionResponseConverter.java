package no.difi.oxalis.as4.outbound;

import com.google.inject.Inject;
import no.difi.oxalis.api.lang.TimestampException;
import no.difi.oxalis.api.model.Direction;
import no.difi.oxalis.api.model.TransmissionIdentifier;
import no.difi.oxalis.api.outbound.TransmissionRequest;
import no.difi.oxalis.api.outbound.TransmissionResponse;
import no.difi.oxalis.api.timestamp.Timestamp;
import no.difi.oxalis.api.timestamp.TimestampProvider;
import no.difi.oxalis.as4.lang.OxalisAs4TransmissionException;
import no.difi.oxalis.as4.util.AS4ErrorCode;
import no.difi.oxalis.as4.util.Marshalling;
import no.difi.oxalis.commons.bouncycastle.BCHelper;
import no.difi.vefa.peppol.common.code.DigestMethod;
import no.difi.vefa.peppol.common.model.Digest;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Error;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.SignalMessage;
import org.w3c.dom.NodeList;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.TransformerException;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import static no.difi.oxalis.as4.util.Constants.DIGEST_ALGORITHM_SHA256;

public class TransmissionResponseConverter {

    private TimestampProvider timestampProvider;

    @Inject
    public TransmissionResponseConverter(TimestampProvider timestampProvider) {
        this.timestampProvider = timestampProvider;
    }

    public TransmissionResponse convert(TransmissionRequest request, SOAPMessage response) {
        try {
            SignalMessage signalMessage = getSignalMessage(response);

            String refToMessageId = signalMessage.getMessageInfo().getRefToMessageId();
            TransmissionIdentifier ti = TransmissionIdentifier.of(refToMessageId);

            if( !signalMessage.getError().isEmpty() ){
                List<Error> errorList = signalMessage.getError();

                for(Error error : errorList){
                    // TODO: Find better handling of AS4 errors
                    OxalisAs4TransmissionException exception = new OxalisAs4TransmissionException(error.getErrorDetail(), AS4ErrorCode.nameOf(error.getErrorCode()), AS4ErrorCode.Severity.nameOf(error.getSeverity()));
                    return new As4TransmissionResponse(exception, ti);
                }

            }

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
            response.writeTo(bos);

            return new As4TransmissionResponse(
                    ti,
                    request,
                    digest,
                    bos.toByteArray(),
                    ts,
                    ts.getDate()
            );

        }catch (Exception e){
            return null;
        }
    }

    private SignalMessage getSignalMessage(SOAPMessage soapMessage) throws TransformerException {

        try {
            NodeList signalNode = soapMessage.getSOAPHeader().getElementsByTagNameNS("*", "SignalMessage");
            if (signalNode.getLength() != 1) {
                throw new TransformerException("SOAP header contains zero or multiple SignalMessage elements, should only contain one");
            }
            try {
                Unmarshaller unmarshaller = Marshalling.getInstance().createUnmarshaller();
                return unmarshaller.unmarshal(signalNode.item(0), SignalMessage.class).getValue();
            } catch (JAXBException e) {
                throw new TransformerException("Could not create unmarshaller", e);
            }
        }catch (SOAPException e){
            throw new TransformerException("Could not access response body", e);
        }
    }
}
