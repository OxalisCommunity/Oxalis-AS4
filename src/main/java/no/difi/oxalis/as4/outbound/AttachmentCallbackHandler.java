package no.difi.oxalis.as4.outbound;

import no.difi.oxalis.as4.util.InputStreamDataSource;
import org.apache.wss4j.common.ext.AttachmentRequestCallback;
import org.apache.wss4j.common.ext.AttachmentResultCallback;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.saaj.SaajSoapMessage;

import javax.activation.DataHandler;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.xml.soap.AttachmentPart;
import javax.xml.soap.MimeHeader;
import javax.xml.soap.SOAPException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A CallbackHandler to be used to sign/encrypt SAAJ SOAP Attachments.
 */
public class AttachmentCallbackHandler implements CallbackHandler {

    private SaajSoapMessage soapMessage;

    public AttachmentCallbackHandler(SoapMessage soapMessage) {
        this.soapMessage = (SaajSoapMessage) soapMessage;
    }

    @Override
    public void handle (Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        for (Callback callback : callbacks) {
            if (callback instanceof AttachmentRequestCallback) {
                AttachmentRequestCallback attachmentRequestCallback = (AttachmentRequestCallback) callback;

                List<org.apache.wss4j.common.ext.Attachment> attachmentList = new ArrayList<>();
                attachmentRequestCallback.setAttachments(attachmentList);

                String attachmentId = attachmentRequestCallback.getAttachmentId();
                if ("Attachments".equals(attachmentId)) {
                    attachmentId = null;
                }
                loadAttachments(attachmentList, attachmentId, attachmentRequestCallback.isRemoveAttachments());
            } else if (callback instanceof AttachmentResultCallback) {
                AttachmentResultCallback attachmentResultCallback = (AttachmentResultCallback) callback;
                AttachmentPart attachmentPart = soapMessage.getSaajMessage()
                        .createAttachmentPart(new DataHandler(
                                new InputStreamDataSource(
                                        attachmentResultCallback.getAttachment()
                                                .getSourceStream(),
                                        attachmentResultCallback.getAttachment()
                                                .getMimeType())));
                attachmentPart.setContentId(attachmentResultCallback.getAttachmentId());

                Map<String, String> headers = attachmentResultCallback.getAttachment()
                        .getHeaders();
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    attachmentPart.addMimeHeader(entry.getKey(), entry.getValue());
                }

                soapMessage.getSaajMessage()
                        .addAttachmentPart(attachmentPart);

            } else {
                throw new UnsupportedCallbackException(callback, "Unsupported callback");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void loadAttachments (List<org.apache.wss4j.common.ext.Attachment> attachmentList,
            String attachmentId,
            boolean removeAttachments)
            throws IOException {

        Iterator<AttachmentPart> iterator = soapMessage.getSaajMessage().getAttachments();
        while (iterator.hasNext()) {
            AttachmentPart attachmentPart = iterator.next();
            if (attachmentId != null && !attachmentId.equals(attachmentPart.getContentId())) {
                continue;
            }
            org.apache.wss4j.common.ext.Attachment att = new org.apache.wss4j.common.ext.Attachment();
            att.setMimeType(attachmentPart.getContentType());
            att.setId(attachmentPart.getContentId());
            try {
                att.setSourceStream(attachmentPart.getDataHandler()
                        .getInputStream());
            } catch (SOAPException e) {
                throw new IOException("Soap exception: " + e.getMessage());
            }
            Iterator<MimeHeader> mimeHeaders = attachmentPart.getAllMimeHeaders();
            while (mimeHeaders.hasNext()) {
                MimeHeader mimeHeader = mimeHeaders.next();
                att.addHeader(mimeHeader.getName(), mimeHeader.getValue());
            }
            attachmentList.add(att);

            if (removeAttachments) {
                iterator.remove();
            }
        }
    }

}
