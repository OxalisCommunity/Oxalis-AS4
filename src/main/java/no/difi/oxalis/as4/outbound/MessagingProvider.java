package no.difi.oxalis.as4.outbound;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import no.difi.oxalis.api.outbound.TransmissionRequest;
import no.difi.oxalis.as4.api.MessageIdGenerator;
import no.difi.oxalis.as4.common.As4MessageProperties;
import no.difi.oxalis.as4.util.PeppolConfiguration;
import no.difi.oxalis.as4.util.TransmissionRequestUtil;
import no.difi.oxalis.commons.security.CertificateUtils;
import no.difi.vefa.peppol.common.model.ProcessIdentifier;
import org.apache.cxf.attachment.AttachmentUtil;
import org.apache.cxf.message.Attachment;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.*;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.Spliterator;
import java.util.stream.Collectors;

import static no.difi.oxalis.as4.util.Constants.TEST_ACTION;
import static no.difi.oxalis.as4.util.Constants.TEST_SERVICE;
import static no.difi.oxalis.as4.util.GeneralUtils.iteratorToStreamOfUnknownSize;

public class MessagingProvider {

    private final X509Certificate certificate;
    private final MessageIdGenerator messageIdGenerator;
    private final PeppolConfiguration defaultOutboundConfiguration;
    private final ActionProvider actionProvider;

    @Inject
    public MessagingProvider(X509Certificate certificate, MessageIdGenerator messageIdGenerator, PeppolConfiguration defaultOutboundConfiguration, ActionProvider actionProvider) {
        this.certificate = certificate;
        this.messageIdGenerator = messageIdGenerator;
        this.defaultOutboundConfiguration = defaultOutboundConfiguration;
        this.actionProvider = actionProvider;
    }

    public Messaging createMessagingHeader(TransmissionRequest request, Collection<Attachment> attachments) {
        return Messaging.builder()
                .addUserMessage(getUserMessage(request, attachments))
                .build();
    }

    public UserMessage getUserMessage(TransmissionRequest request, Collection<Attachment> attachments) {
        return UserMessage.builder()
                .withMessageInfo(createMessageInfo(request))
                .withPartyInfo(createPartyInfo(request))
                .withCollaborationInfo(createCollaborationInfo(request))
                .withMessageProperties(createMessageProperties(request))
                .withPayloadInfo(createPayloadInfo(request, attachments))
                .build();
    }

    private PayloadInfo createPayloadInfo(TransmissionRequest request, Collection<Attachment> attachments) {

        ArrayList<PartInfo> partInfos = Lists.newArrayList();
        for (Attachment attachment : attachments) {

            PartProperties.Builder<Void> partProperties = PartProperties.builder();

            String cid = "cid:" + AttachmentUtil.cleanContentId(attachment.getId());

            iteratorToStreamOfUnknownSize(attachment.getHeaderNames(), Spliterator.ORDERED, false)
                    .filter(header -> !"Content-ID".equals(header))
                    .map(header -> Property.builder().withName(header).withValue(attachment.getHeader(header)).build())
                    .forEach(partProperties::addProperty);

            if (request instanceof As4TransmissionRequest) {
                As4TransmissionRequest as4TransmissionRequest = (As4TransmissionRequest) request;
                if (null != as4TransmissionRequest.getPayloadCharset()) {
                    partProperties.addProperty(
                            Property.builder()
                                    .withName("CharacterSet")
                                    .withValue(as4TransmissionRequest.getPayloadCharset().name().toLowerCase())
                                    .build()
                    );
                }

                if (null != as4TransmissionRequest.getCompressionType()) {
                    partProperties.addProperty(
                            Property.builder()
                                    .withName("CompressionType")
                                    .withValue(as4TransmissionRequest.getCompressionType())
                                    .build()
                    );
                }
            }

            PartInfo partInfo = PartInfo.builder()
                    .withHref(cid)
                    .withPartProperties(partProperties.build())
                    .build();
            partInfos.add(partInfo);
        }

        return PayloadInfo.builder()
                .withPartInfo(partInfos)
                .build();
    }


    private MessageProperties createMessageProperties(TransmissionRequest request) {
        As4MessageProperties properties = new As4MessageProperties();

        if (request instanceof As4TransmissionRequest) {
            As4TransmissionRequest as4TransmissionRequest = (As4TransmissionRequest) request;
            if (as4TransmissionRequest.getMessageProperties() != null) {
                properties.addAll(as4TransmissionRequest.getMessageProperties());
            }
        }

        if (properties.isMissing("originalSender")) {
            properties.add(TransmissionRequestUtil.toAs4MessageProperty("originalSender", request.getHeader().getSender()));
        }

        if (properties.isMissing("finalRecipient")) {
            properties.add(TransmissionRequestUtil.toAs4MessageProperty("finalRecipient", request.getHeader().getReceiver()));
        }

        return MessageProperties.builder()
                .withProperty(properties.stream()
                        .map(p -> Property.builder()
                                .withName(p.getName())
                                .withType(p.getType())
                                .withValue(p.getValue())
                                .build())
                        .collect(Collectors.toList())
                )
                .build();
    }

    private PartyInfo createPartyInfo(TransmissionRequest request) {

        String fromName = CertificateUtils.extractCommonName(certificate);
        String toName = CertificateUtils.extractCommonName(request.getEndpoint().getCertificate());

        PeppolConfiguration outboundConfiguration = request.getTag() instanceof PeppolConfiguration ?
                (PeppolConfiguration) request.getTag() : defaultOutboundConfiguration;

        return PartyInfo.builder()
                .withFrom(From.builder()
                        .withPartyId(PartyId.builder()
                                .withType(outboundConfiguration.getPartyIDType())
                                .withValue(fromName)
                                .build())
                        .withRole(outboundConfiguration.getFromRole())
                        .build())
                .withTo(To.builder()
                        .withPartyId(PartyId.builder()
                                .withType(outboundConfiguration.getPartyIDType())
                                .withValue(toName)
                                .build())
                        .withRole(outboundConfiguration.getToRole())
                        .build()
                ).build();
    }

    public CollaborationInfo createCollaborationInfo(TransmissionRequest request) {
        String action = actionProvider.getAction(request.getHeader().getDocumentType());

        ProcessIdentifier process = request.getHeader().getProcess();

        CollaborationInfo.Builder<Void> cib = CollaborationInfo.builder()
                .withConversationId(getConversationId(request))
                .withAction(action)
                .withService(Service.builder()
                        .withType(process.getScheme().getIdentifier())
                        .withValue(process.getIdentifier())
                        .build()
                );

        if (request instanceof As4TransmissionRequest && ((As4TransmissionRequest) request).isPing()) {
            cib = cib.withAction(TEST_ACTION)
                    .withService(Service.builder()
                            .withValue(TEST_SERVICE)
                            .build());
        }

        if (defaultOutboundConfiguration.getAgreementRef() != null) {
            cib = cib.withAgreementRef(AgreementRef.builder()
                    .withValue(defaultOutboundConfiguration.getAgreementRef())
                    .build()
            );
        }

        return cib.build();
    }

    private MessageInfo createMessageInfo(TransmissionRequest request) {
        GregorianCalendar gcal = GregorianCalendar.from(LocalDateTime.now().atZone(ZoneId.systemDefault()));
        XMLGregorianCalendar xmlDate;
        try {
            xmlDate = DatatypeFactory.newInstance().newXMLGregorianCalendar(gcal);
        } catch (DatatypeConfigurationException e) {
            throw new RuntimeException("Error getting xml date", e);
        }

        MessageInfo.Builder<Void> builder = MessageInfo.builder()
                .withMessageId(getMessageId(request))
                .withTimestamp(xmlDate);

        if (request instanceof As4TransmissionRequest) {
            As4TransmissionRequest as4TransmissionRequest = (As4TransmissionRequest) request;
            if (as4TransmissionRequest.getRefToMessageId() != null) {
                builder.withRefToMessageId(as4TransmissionRequest.getRefToMessageId());
            }
        }

        return builder.build();
    }

    private String getMessageId(TransmissionRequest request) {
        String messageId = null;

        if (request instanceof As4TransmissionRequest) {
            As4TransmissionRequest as4TransmissionRequest = (As4TransmissionRequest) request;
            messageId = as4TransmissionRequest.getMessageId();
        }

        return messageId != null ? messageId : newId();
    }

    private String getConversationId(TransmissionRequest request) {
        String conversationId = null;

        if (request instanceof As4TransmissionRequest) {
            As4TransmissionRequest as4TransmissionRequest = (As4TransmissionRequest) request;
            conversationId = as4TransmissionRequest.getConversationId();
        }

        return conversationId != null ? conversationId : newId();
    }

    private String newId() {
        return messageIdGenerator.generate();
    }
}
