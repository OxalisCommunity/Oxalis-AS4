package no.difi.oxalis.as4.outbound;

import com.google.inject.Inject;
import com.google.inject.Provider;
import no.difi.oxalis.api.lang.OxalisTransmissionException;
import no.difi.oxalis.api.outbound.MessageSender;
import no.difi.oxalis.api.outbound.TransmissionRequest;
import no.difi.oxalis.api.outbound.TransmissionResponse;

public class As4MessageSenderFascade implements MessageSender {

    private Provider<As4MessageSender> messageSenderProvider;

    @Inject
    public As4MessageSenderFascade(Provider<As4MessageSender> messageSenderProvider) {
        this.messageSenderProvider = messageSenderProvider;
    }


    @Override
    public TransmissionResponse send(TransmissionRequest transmissionRequest) throws OxalisTransmissionException {
        return messageSenderProvider.get().send(transmissionRequest);
    }
}
