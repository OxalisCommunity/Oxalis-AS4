package no.difi.oxalis.as4.outbound;

import com.google.inject.Inject;
import no.difi.oxalis.api.lang.OxalisTransmissionException;
import no.difi.oxalis.api.outbound.MessageSender;
import no.difi.oxalis.api.outbound.TransmissionRequest;
import no.difi.oxalis.api.outbound.TransmissionResponse;

public class As4MessageSenderFacade implements MessageSender {

    private As4MessageSender messageSender;

    @Inject
    public As4MessageSenderFacade(As4MessageSender messageSender) {
        this.messageSender = messageSender;
    }

    @Override
    public TransmissionResponse send(TransmissionRequest transmissionRequest) throws OxalisTransmissionException {
        return messageSender.send(transmissionRequest);
    }
}
