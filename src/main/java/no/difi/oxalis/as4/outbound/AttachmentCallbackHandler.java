package no.difi.oxalis.as4.outbound;

import org.apache.wss4j.common.ext.Attachment;
import org.apache.wss4j.common.ext.AttachmentRequestCallback;
import org.springframework.util.StringUtils;
import org.springframework.ws.soap.SoapMessage;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AttachmentCallbackHandler implements CallbackHandler {
    private final SoapMessage message;

    public AttachmentCallbackHandler(final SoapMessage message) {
        this.message = message;

    }

    protected static Attachment convert(final org.springframework.ws.mime.Attachment n) throws IOException {
        Attachment e = new Attachment();
        e.setId(n.getContentId().replaceFirst("<", "").replace(">", ""));
        e.setMimeType(n.getContentType());
        e.setSourceStream(n.getInputStream());
        return e;
    }

    @Override
    public void handle(final Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        for (Callback c : callbacks) {
            if (c instanceof AttachmentRequestCallback) {
                AttachmentRequestCallback arg = (AttachmentRequestCallback) c;
                List<Attachment> attList = new ArrayList<Attachment>();
                if (StringUtils.isEmpty(arg.getAttachmentId()) || arg.getAttachmentId().equals("Attachments")) {
                    Iterator<org.springframework.ws.mime.Attachment> attz = message.getAttachments();
                    while (attz.hasNext()) {
                        attList.add(convert(attz.next()));
                    }
                } else {
                    org.springframework.ws.mime.Attachment attachment = message.getAttachment("<" + arg.getAttachmentId() + ">");
                    if (attachment == null) {
                        throw new IllegalArgumentException("No such attachment: " + arg.getAttachmentId());
                    }
                    attList.add(convert(attachment));
                }
                arg.setAttachments(attList);
            }
        }
    }
}
