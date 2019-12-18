package no.difi.oxalis.as4.inbound;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import no.difi.oxalis.api.model.TransmissionIdentifier;
import no.difi.oxalis.api.persist.PersisterHandler;
import no.difi.oxalis.as4.lang.OxalisAs4Exception;
import no.difi.oxalis.as4.util.AS4ErrorCode;
import no.difi.oxalis.as4.util.As4MessageFactory;
import no.difi.oxalis.as4.util.MessageId;
import org.apache.cxf.message.Attachment;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.apache.wss4j.common.ext.WSSecurityException;

import javax.activation.DataHandler;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPHandler;
import javax.xml.ws.handler.soap.SOAPMessageContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

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
        String conversationId = (String) context.get("oxalis.as4.conversationId");
        MessageId messageId = (MessageId) context.get(MessageId.MESSAGE_ID);

        Optional.ofNullable(context.get(Exception.class.getName()))
                .map(Exception.class::cast)
                .map(Exception::getCause)
                .map(As4FaultInHandler::launderException)
                .filter(OxalisAs4Exception.class::isInstance)
                .map(OxalisAs4Exception.class::cast)
                .flatMap(as4Exception -> Optional.ofNullable(messageId)
                        .map(id -> toErrorMessage(as4Exception, id, context)))
                .ifPresent(errorMessage -> {
                    context.setMessage(errorMessage);
                    log.debug("Converted default Fault into EBMS error message");
                    log.info("\n\n********************\nMessageId: " + messageId.getValue() + "\nConversationId: " + conversationId + "\n********************");
                });

        return true;
    }

    private SOAPMessage toErrorMessage(OxalisAs4Exception as4Exception, MessageId id, SOAPMessageContext context) {
        Path firstPayloadPath = (Path) context.get(AS4MessageContextKey.FIRST_PAYLOAD_PATH);
        As4PayloadHeader firstPayloadHeader = (As4PayloadHeader) context.get(AS4MessageContextKey.FIRST_PAYLOAD_HEADER);

        if (firstPayloadPath != null) {
            try {
                persisterHandler.persist(TransmissionIdentifier.of(id.getValue()), firstPayloadHeader, firstPayloadPath, as4Exception);
            } catch (Exception e) {
                log.error("Unable to persist exception", e);
            }
        }

        return as4MessageFactory.createErrorMessage(id, as4Exception);
    }


    @Override
    public void close(MessageContext context) {
        // Intentionally left empty
    }

    public static Throwable launderException(Throwable t) {
        // Is there a better way of getting the inMessage using JAX-WS?
        Optional<Message> faultMessage = Optional.ofNullable(PhaseInterceptorChain.getCurrentMessage());
        Optional<Message> inMessage = faultMessage
                .map(Message::getExchange)
                .map(Exchange::getInMessage);

        if (t instanceof WSSecurityException) {

            boolean isCompressionError = (boolean) inMessage.get().getOrDefault("oxalis.as4.compressionErrorDetected", false);
            if (isCompressionError) {

                return new OxalisAs4Exception(
                        "Content cannot be compressed after signature/encryption", AS4ErrorCode.EBMS_0303);
            }

        }

        if (t.getCause() instanceof OxalisAs4Exception) {
            t = t.getCause();
        }

        return t;
    }

    public static boolean attachmentsIsCompressed(Collection<Attachment> attachments) {

        return Optional.of(attachments)
                .map(Collection::stream).orElseGet(Stream::empty)
                .map(Attachment::getDataHandler)
                .map(Optional::of)
                .anyMatch(As4FaultInHandler::isInputStreamZipped);
    }

    public static boolean isInputStreamZipped(Optional<DataHandler> dataHandler) {

        try {

            if (dataHandler.isPresent()) {
                return canExtractZipEntry(dataHandler.get().getInputStream());
            } else {
                return false;
            }

        } catch (IOException e) {
            return false;
        }
    }

    private static boolean canExtractZipEntry(InputStream is) {
        try {

            byte[] testExtraction = new byte[20];
            GZIPInputStream zis = new GZIPInputStream(is);
            zis.read(testExtraction);

        } catch (IOException e) {
            return false;
        }

        return true;
    }
}
