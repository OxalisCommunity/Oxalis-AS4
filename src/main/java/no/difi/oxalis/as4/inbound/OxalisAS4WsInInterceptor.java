package no.difi.oxalis.as4.inbound;

import no.difi.oxalis.as4.lang.OxalisAs4Exception;
import no.difi.oxalis.as4.util.Constants;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Attachment;
import org.apache.cxf.ws.security.SecurityConstants;
import org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor;
import org.apache.wss4j.common.crypto.Crypto;
import org.apache.wss4j.dom.handler.WSHandlerConstants;

import javax.xml.namespace.QName;
import javax.xml.soap.AttachmentPart;
import javax.xml.soap.SOAPMessage;
import java.util.*;

public class OxalisAS4WsInInterceptor extends WSS4JInInterceptor {

    private Crypto crypto;
    private String alias;

    OxalisAS4WsInInterceptor(Map<String, Object> props, Crypto crypto, String alias) {
        super(props);
        this.crypto = crypto;
        this.alias = alias;
    }

    @Override
    public void handleMessage(SoapMessage msg) throws Fault {
        msg.put(SecurityConstants.ENCRYPT_CRYPTO, crypto);
        msg.put(SecurityConstants.SIGNATURE_CRYPTO, crypto);
        msg.put(SecurityConstants.ENCRYPT_USERNAME, alias);
        msg.put(SecurityConstants.SIGNATURE_USERNAME, alias);
        msg.put(SecurityConstants.USERNAME, alias);
        msg.put(SecurityConstants.RETURN_SECURITY_ERROR, Boolean.TRUE);

        if(null == msg.get(WSHandlerConstants.ACTION) ){
            msg.put(WSHandlerConstants.ACTION, WSHandlerConstants.SIGNATURE + " " + WSHandlerConstants.ENCRYPT);
        }

        Collection<Attachment> attachments = new ArrayList<>();
        if(msg.getAttachments() != null){
            attachments.addAll(msg.getAttachments());
        }


        try {
            super.handleMessage(msg);
        } catch (Throwable t){

            if (As4FaultInHandler.attachmentsIsCompressed(attachments)){
                msg.put("oxalis.as4.compressionErrorDetected", true);
            }
            throw t;
        }


        SOAPMessage soapMessage = msg.getContent(SOAPMessage.class);
        if (soapMessage != null) {
            if (soapMessage.countAttachments() > 0) {
                Iterator<AttachmentPart> it = CastUtils.cast(soapMessage.getAttachments());
                while (it.hasNext()) {
                    AttachmentPart part = it.next();
                    Optional<Attachment> first = msg.getAttachments().stream()
                            .filter(a -> a.getId().equals(part.getContentId().replaceAll("<|>", "")))
                            .findFirst();
                    first.ifPresent(a -> part.setDataHandler(a.getDataHandler()));
                    first.orElseThrow(() -> new Fault(new OxalisAs4Exception("Unable to find attachment")));
                }
            }
        }
    }

    @Override
    public Set<QName> getUnderstoodHeaders() {
        Set<QName> understoodHeaders = super.getUnderstoodHeaders();
        understoodHeaders.add(Constants.MESSAGING_QNAME);
        return understoodHeaders;
    }
}
