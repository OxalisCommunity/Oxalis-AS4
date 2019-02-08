package no.difi.oxalis.as4.inbound;

import com.google.inject.Inject;
import no.difi.oxalis.as4.api.MessageIdGenerator;
import no.difi.oxalis.as4.lang.OxalisAs4Exception;
import no.difi.oxalis.as4.util.Constants;
import no.difi.oxalis.as4.util.Marshalling;
import no.difi.oxalis.as4.util.MessageIdUtil;
import no.difi.oxalis.as4.util.XMLUtil;
import org.oasis_open.docs.ebxml_bp.ebbp_signals_2.MessagePartNRInformation;
import org.oasis_open.docs.ebxml_bp.ebbp_signals_2.NonRepudiationInformation;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.MessageInfo;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Receipt;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.SignalMessage;
import org.w3.xmldsig.ReferenceType;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.soap.*;
import java.util.List;
import java.util.stream.Collectors;

public class As4ResponseProvider {

    private final MessageIdGenerator messageIdGenerator;

    @Inject
    public As4ResponseProvider(MessageIdGenerator messageIdGenerator) {
        this.messageIdGenerator = messageIdGenerator;
    }

    SOAPMessage getSOAPResponse(As4InboundInfo inboundInfo) throws OxalisAs4Exception {
        SOAPMessage message = getSOAPMessage();
        SOAPHeaderElement messagingHeader = addResponseHeader(message);
        SignalMessage signalMessage = getSignalMessage(inboundInfo);

        try {
            getMarshaller().marshal(
                    new JAXBElement<>(Constants.SIGNAL_MESSAGE_QNAME, SignalMessage.class, signalMessage),
                    messagingHeader);
        } catch (JAXBException e) {
            throw new OxalisAs4Exception("Could not marshal signal message to header", e);
        }

        return message;
    }

    private SignalMessage getSignalMessage(As4InboundInfo inboundInfo) throws OxalisAs4Exception {
        return SignalMessage.builder()
                .withMessageInfo(getMessageInfo(inboundInfo))
                .withReceipt(getReceipt(inboundInfo.getReferenceListFromSignedInfo()))
                .build();
    }

    private SOAPHeaderElement addResponseHeader(SOAPMessage message) throws OxalisAs4Exception {
        try {
            SOAPHeaderElement messagingHeader = message.getSOAPHeader().addHeaderElement(Constants.MESSAGING_QNAME);
            messagingHeader.setMustUnderstand(true);
            return messagingHeader;
        } catch (SOAPException e) {
            throw new OxalisAs4Exception("Could not create SOAP header", e);
        }
    }

    private Marshaller getMarshaller() throws JAXBException {
        return Marshalling.getInstance().getJaxbContext().createMarshaller();
    }

    private SOAPMessage getSOAPMessage() throws OxalisAs4Exception {
        try {
            return createMessageFactory().createMessage();
        } catch (SOAPException e) {
            throw new OxalisAs4Exception("Could not create SOAP message", e);
        }
    }

    private MessageFactory createMessageFactory() throws SOAPException {
        return MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL);
    }

    private MessageInfo getMessageInfo(As4InboundInfo inboundInfo) throws OxalisAs4Exception {
        return MessageInfo.builder()
                .withTimestamp(XMLUtil.toXmlGregorianCalendar(inboundInfo.getTimestamp()))
                .withMessageId(getMessageId())
                .withRefToMessageId(inboundInfo.getTransmissionIdentifier().getIdentifier())
                .build();
    }

    private String getMessageId() throws OxalisAs4Exception {
        String messageId = messageIdGenerator.generate();

        if (MessageIdUtil.verify(messageId)) {
            return messageId;
        }

        throw new OxalisAs4Exception("Invalid Message-ID '" + messageId + "' generated.");
    }

    private Receipt getReceipt(List<ReferenceType> referenceList) {
        return Receipt.builder().withAny(getNonRepudiationInformation(referenceList)).build();
    }

    private NonRepudiationInformation getNonRepudiationInformation(List<ReferenceType> referenceList) {
        return NonRepudiationInformation.builder()
                .addMessagePartNRInformation(getMessagePartNRInformationList(referenceList))
                .build();
    }

    private List<MessagePartNRInformation> getMessagePartNRInformationList(List<ReferenceType> referenceList) {
        return referenceList.stream()
                .map(r -> MessagePartNRInformation.builder().withReference(r).build())
                .collect(Collectors.toList());
    }
}
