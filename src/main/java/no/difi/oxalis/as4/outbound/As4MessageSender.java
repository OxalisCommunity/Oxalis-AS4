package no.difi.oxalis.as4.outbound;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import no.difi.oxalis.api.outbound.TransmissionRequest;
import no.difi.oxalis.api.outbound.TransmissionResponse;
import no.difi.oxalis.api.settings.Settings;
import no.difi.oxalis.as4.api.MessageIdGenerator;
import no.difi.oxalis.as4.common.MerlinProvider;
import no.difi.oxalis.as4.config.As4Conf;
import no.difi.oxalis.as4.lang.OxalisAs4TransmissionException;
import no.difi.oxalis.as4.util.CompressionUtil;
import no.difi.oxalis.as4.util.Constants;
import no.difi.oxalis.as4.util.PolicyService;
import no.difi.oxalis.commons.http.HttpConf;
import no.difi.oxalis.commons.security.KeyStoreConf;
import org.apache.cxf.attachment.AttachmentUtil;
import org.apache.cxf.binding.soap.SoapHeader;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.headers.Header;
import org.apache.cxf.jaxb.JAXBDataBinding;
import org.apache.cxf.jaxws.DispatchImpl;
import org.apache.cxf.message.Attachment;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.cxf.ws.policy.WSPolicyFeature;
import org.apache.wss4j.common.crypto.Merlin;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Messaging;

import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Service;
import javax.xml.ws.soap.SOAPBinding;
import java.io.IOException;
import java.util.*;

import static no.difi.oxalis.as4.common.AS4Constants.CEF_CONFORMANCE;
import static org.apache.cxf.rt.security.SecurityConstants.*;

@Slf4j
public class As4MessageSender {

    public static final QName SERVICE_NAME = new QName("oxalis.difi.no/", "outbound-service");
    public static final QName PORT_NAME = new QName("oxalis.difi.no/", "port");

    @Inject
    private MessagingProvider messagingProvider;

    @Inject
    private MessageIdGenerator messageIdGenerator;

    @Inject
    private Settings<KeyStoreConf> settings;

    @Inject
    private Settings<As4Conf> as4settings;

    @Inject
    private CompressionUtil compressionUtil;

    @Inject
    private Settings<HttpConf> httpConfSettings;

    @Inject
    private TransmissionResponseConverter transmissionResponseConverter;

    @Inject
    private MerlinProvider merlinProvider;

    @Inject
    private PolicyService policyService;

    public TransmissionResponse send(TransmissionRequest request) throws OxalisAs4TransmissionException {
        try (DispatchImpl<SOAPMessage> dispatch = createDispatch(request)) {
            Collection<Attachment> attachments = prepareAttachments(request);
            dispatch.getRequestContext().put(Message.ATTACHMENTS, attachments);

            Messaging messaging = messagingProvider.createMessagingHeader(request, attachments);
            SoapHeader header = getSoapHeader(messaging);
            dispatch.getRequestContext().put(Header.HEADER_LIST, new ArrayList<>(Collections.singletonList(header)));
            return invoke(request, dispatch);
        } catch (IOException e) {
            throw new OxalisAs4TransmissionException("Failed to send message", e);
        }
    }

    private TransmissionResponse invoke(TransmissionRequest request, DispatchImpl<SOAPMessage> dispatch) throws OxalisAs4TransmissionException {
        try {
            SOAPMessage response = dispatch.invoke(null);
            return transmissionResponseConverter.convert(request, response);
        } catch (Exception e) {
            throw new OxalisAs4TransmissionException("Failed to send message", e);
        }
    }

    private SoapHeader getSoapHeader(Messaging messaging) throws OxalisAs4TransmissionException {
        try {
            JAXBDataBinding binding = new JAXBDataBinding(Messaging.class);
            return new SoapHeader(
                    Constants.MESSAGING_QNAME,
                    messaging,
                    binding,
                    true);
        } catch (JAXBException e) {
            throw new OxalisAs4TransmissionException("Unable to marshal AS4 header", e);
        }
    }

    private void configureSecurity(TransmissionRequest request, Dispatch<SOAPMessage> dispatch) {
        Merlin merlin = merlinProvider.getMerlin();
        dispatch.getRequestContext().put(SIGNATURE_CRYPTO, merlin);
        dispatch.getRequestContext().put(SIGNATURE_PASSWORD, settings.getString(KeyStoreConf.KEY_PASSWORD));
        dispatch.getRequestContext().put(SIGNATURE_USERNAME, settings.getString(KeyStoreConf.KEY_ALIAS));
        dispatch.getRequestContext().put(ENCRYPT_CERT, request.getEndpoint().getCertificate());
    }

    public Collection<Attachment> prepareAttachments(TransmissionRequest request) throws OxalisAs4TransmissionException {
        Map<String, List<String>> headers = new HashMap<>();

        headers.put("Content-ID", Collections.singletonList(getContentID(request)));
        headers.put("CompressionType", Collections.singletonList("application/gzip"));
        headers.put("MimeType", Collections.singletonList("application/xml"));

        try {
            Attachment attachment = AttachmentUtil.createAttachment(compressionUtil.getCompressedStream(request.getPayload()), headers);
            return new ArrayList<>(Collections.singletonList(attachment));
        } catch (IOException e) {
            throw new OxalisAs4TransmissionException("Unable to compress payload", e);
        }
    }

    private String getContentID(TransmissionRequest request) {
        if (request instanceof As4TransmissionRequest) {
            As4TransmissionRequest as4request = (As4TransmissionRequest) request;
            return as4request.getPayloadHref();
        }

        return messageIdGenerator.generate();
    }

    private DispatchImpl<SOAPMessage> createDispatch(TransmissionRequest request) throws OxalisAs4TransmissionException {
        DispatchImpl<SOAPMessage> dispatch = (DispatchImpl<SOAPMessage>) getService(request)
                .createDispatch(PORT_NAME, SOAPMessage.class, Service.Mode.MESSAGE);
        dispatch.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, request.getEndpoint().getAddress().toString());

        configureSecurity(request, dispatch);

        final Client client = dispatch.getClient();

        if (CEF_CONFORMANCE.equalsIgnoreCase(as4settings.getString(As4Conf.TYPE))) {
            client.getInInterceptors().add(getLoggingBeforeSecurityInInterceptor());
        }

        final HTTPConduit httpConduit = (HTTPConduit) client.getConduit();
        final HTTPClientPolicy httpClientPolicy = httpConduit.getClient();
        httpClientPolicy.setConnectionTimeout(httpConfSettings.getInt(HttpConf.TIMEOUT_CONNECT));
        httpClientPolicy.setReceiveTimeout(httpConfSettings.getInt(HttpConf.TIMEOUT_READ));

        return dispatch;
    }

    private LoggingBeforeSecurityInInterceptor getLoggingBeforeSecurityInInterceptor() {
        LoggingBeforeSecurityInInterceptor interceptor = new LoggingBeforeSecurityInInterceptor();
        interceptor.setPrettyLogging(true);
        interceptor.setLogMultipart(true);
        return interceptor;
    }

    private Service getService(TransmissionRequest request) throws OxalisAs4TransmissionException {
        Service service = Service.create(SERVICE_NAME, new LoggingFeature(), new WSPolicyFeature(policyService.getPolicy(request)));
        service.addPort(PORT_NAME, SOAPBinding.SOAP12HTTP_BINDING, request.getEndpoint().getAddress().toString());
        return service;
    }
}
