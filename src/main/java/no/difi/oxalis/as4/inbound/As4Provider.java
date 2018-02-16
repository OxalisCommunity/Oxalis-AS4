package no.difi.oxalis.as4.inbound;

import org.apache.commons.io.IOUtils;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.transport.http.AbstractHTTPDestination;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.soap.AttachmentPart;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.*;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.soap.SOAPBinding;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

@WebServiceProvider
@ServiceMode(value = Service.Mode.MESSAGE)
@BindingType(value = SOAPBinding.SOAP12HTTP_BINDING)
public class As4Provider implements Provider<SOAPMessage> {

    @Resource
    private WebServiceContext context;

    @Override
    public SOAPMessage invoke(SOAPMessage request) {
        MessageContext ctx = context.getMessageContext();
        HttpServletRequest httpReq = (HttpServletRequest) ctx.get(AbstractHTTPDestination.HTTP_REQUEST);
        HttpServletResponse httpRes = (HttpServletResponse) ctx.get(AbstractHTTPDestination.HTTP_RESPONSE);
        httpRes.setStatus(HttpServletResponse.SC_OK);

        Iterator<AttachmentPart> attachments = CastUtils.cast(request.getAttachments());
        while (attachments.hasNext()) {
            AttachmentPart a = attachments.next();
            try {
                String s = IOUtils.toString(a.getDataHandler().getInputStream(), StandardCharsets.UTF_8);
                System.out.println(s);
            } catch (IOException | SOAPException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
