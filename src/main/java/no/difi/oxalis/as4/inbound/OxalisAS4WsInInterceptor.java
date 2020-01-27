package no.difi.oxalis.as4.inbound;

import no.difi.oxalis.as4.lang.OxalisAs4Exception;
import no.difi.oxalis.as4.util.Constants;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Attachment;
import org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor;
import org.apache.wss4j.common.crypto.Crypto;

import javax.activation.DataHandler;
import javax.xml.namespace.QName;
import javax.xml.soap.AttachmentPart;
import javax.xml.soap.SOAPMessage;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.apache.cxf.rt.security.SecurityConstants.*;
import static org.apache.cxf.ws.security.SecurityConstants.RETURN_SECURITY_ERROR;
import static org.apache.wss4j.common.ConfigurationConstants.*;

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
        msg.put(ENCRYPT_CRYPTO, crypto);
        msg.put(SIGNATURE_CRYPTO, crypto);
        msg.put(ENCRYPT_USERNAME, alias);
        msg.put(SIGNATURE_USERNAME, alias);
        msg.put(USERNAME, alias);
        msg.put(RETURN_SECURITY_ERROR, Boolean.TRUE);

        msg.putIfAbsent(ACTION, SIGNATURE + " " + ENCRYPT);

        Collection<Attachment> attachments = new ArrayList<>();
        if (msg.getAttachments() != null) {
            attachments.addAll(msg.getAttachments());
        }


        try {
            super.handleMessage(msg);
        } catch (Exception t) {
            if (attachmentsIsCompressed(attachments)) {
                msg.put("oxalis.as4.compressionErrorDetected", true);
            }
            throw t;
        }


        SOAPMessage soapMessage = msg.getContent(SOAPMessage.class);

        if (soapMessage != null && soapMessage.countAttachments() > 0) {
            Iterator<AttachmentPart> it = CastUtils.cast(soapMessage.getAttachments());
            while (it.hasNext()) {
                AttachmentPart part = it.next();
                Attachment first = msg.getAttachments().stream()
                        .filter(a -> a.getId().equals(part.getContentId().replaceAll("[<>]", "")))
                        .findFirst()
                        .orElseThrow(() -> new Fault(new OxalisAs4Exception("Unable to find attachment")));
                part.setDataHandler(first.getDataHandler());
            }
        }
    }

    @Override
    public Set<QName> getUnderstoodHeaders() {
        Set<QName> understoodHeaders = super.getUnderstoodHeaders();
        understoodHeaders.add(Constants.MESSAGING_QNAME);
        return understoodHeaders;
    }

    private boolean attachmentsIsCompressed(Collection<Attachment> attachments) {

        return Optional.of(attachments)
                .map(Collection::stream).orElseGet(Stream::empty)
                .map(Attachment::getDataHandler)
                .filter(Objects::nonNull)
                .anyMatch(this::isInputStreamZipped);
    }

    private boolean isInputStreamZipped(DataHandler dataHandler) {
        try {
            return canExtractZipEntry(dataHandler.getInputStream());
        } catch (IOException e) {
            return false;
        }
    }

    private boolean canExtractZipEntry(InputStream is) {
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
