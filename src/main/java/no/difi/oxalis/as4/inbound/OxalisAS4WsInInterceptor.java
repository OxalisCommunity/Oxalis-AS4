package no.difi.oxalis.as4.inbound;

import no.difi.oxalis.as4.util.Constants;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Attachment;
import org.apache.cxf.ws.security.SecurityConstants;
import org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor;
import org.apache.wss4j.common.crypto.Crypto;

import javax.xml.namespace.QName;
import javax.xml.soap.AttachmentPart;
import javax.xml.soap.SOAPMessage;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

        super.handleMessage(msg);

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
                    first.orElseThrow(() -> new RuntimeException("Unable to find attachment")); // Todo: must send fault message
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
