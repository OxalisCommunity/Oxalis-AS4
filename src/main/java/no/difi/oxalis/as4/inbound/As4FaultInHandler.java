package no.difi.oxalis.as4.inbound;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import no.difi.oxalis.api.model.TransmissionIdentifier;
import no.difi.oxalis.api.persist.PersisterHandler;
import no.difi.oxalis.as4.lang.AS4Error;
import no.difi.oxalis.as4.lang.OxalisAs4Exception;
import no.difi.oxalis.as4.util.AS4ErrorCode;
import no.difi.oxalis.as4.util.As4MessageFactory;
import no.difi.oxalis.as4.util.MessageId;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.apache.wss4j.common.ext.WSSecurityException;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPHandler;
import javax.xml.ws.handler.soap.SOAPMessageContext;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Singleton
public class As4FaultInHandler implements SOAPHandler<SOAPMessageContext> {

    private final As4MessageFactory as4MessageFactory;
    private final PersisterHandler persisterHandler;

    @Inject
    public As4FaultInHandler(As4MessageFactory as4MessageFactory, PersisterHandler persisterHandler) {
        this.as4MessageFactory = as4MessageFactory;
        this.persisterHandler = persisterHandler;
    }

    @Override
    public Set<QName> getHeaders() {
        return Collections.emptySet();
    }

    @Override
    public boolean handleMessage(SOAPMessageContext context) {
        return true;
    }

    @Override
    public boolean handleFault(SOAPMessageContext context) {
        String messageId = Optional.ofNullable((MessageId) context.get(MessageId.MESSAGE_ID))
                .map(MessageId::getValue)
                .orElse(null);

        if (messageId == null) {
            log.info("No messageId found!");
        }

        Exception exception = (Exception) context.get(Exception.class.getName());

        if (exception == null) {
            log.info("No exception found!");
            return true;
        }

        log.info("handleFault for Exception", exception);

        AS4Error as4Error = toAS4Error(exception);

        handleAS4Error(context, messageId, as4Error);

        return true;
    }

    protected void handleAS4Error(SOAPMessageContext context, String messageId, AS4Error as4Error) {
        SOAPMessage errorMessage = as4MessageFactory.createErrorMessage(messageId, as4Error);
        context.setMessage(errorMessage);

        Path firstPayloadPath = (Path) context.get(AS4MessageContextKey.FIRST_PAYLOAD_PATH);
        As4PayloadHeader firstPayloadHeader = (As4PayloadHeader) context.get(AS4MessageContextKey.FIRST_PAYLOAD_HEADER);

        if (messageId != null && firstPayloadPath != null) {
            try {
                persisterHandler.persist(TransmissionIdentifier.of(messageId), firstPayloadHeader, firstPayloadPath, as4Error.getException());
            } catch (Exception e) {
                log.error("Unable to persist exception", e);
            }
        }
    }

    @Override
    public void close(MessageContext context) {
        // Intentionally left empty
    }

    public static AS4Error toAS4Error(Throwable t) {
        // Is there a better way of getting the inMessage using JAX-WS?
        Optional<Message> faultMessage = Optional.ofNullable(PhaseInterceptorChain.getCurrentMessage());
        Optional<Message> inMessage = faultMessage
                .map(Message::getExchange)
                .map(Exchange::getInMessage);

        if (t instanceof Fault) {
            Fault fault = (Fault) t;
            t = fault.getCause();
        }

        if (t instanceof WebServiceException) {
            WebServiceException webServiceException = (WebServiceException) t;
            t = webServiceException.getCause();
        }

        if (t instanceof WSSecurityException && inMessage.isPresent()) {

            boolean isCompressionError = (boolean) inMessage.get().getOrDefault("oxalis.as4.compressionErrorDetected", false);
            if (isCompressionError) {

                return new OxalisAs4Exception(
                        "Content cannot be compressed after signature/encryption", AS4ErrorCode.EBMS_0303);
            }

            return new OxalisAs4Exception(t.getMessage(), t, AS4ErrorCode.EBMS_0009, AS4ErrorCode.Severity.ERROR);
        }

        if (t instanceof AS4Error) {
            return (AS4Error) t;
        }

        return new OxalisAs4Exception(t.getMessage(), t, AS4ErrorCode.EBMS_0004, AS4ErrorCode.Severity.ERROR);
    }
}
