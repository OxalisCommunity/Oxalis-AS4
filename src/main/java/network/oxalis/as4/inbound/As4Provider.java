package network.oxalis.as4.inbound;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import network.oxalis.as4.lang.OxalisAs4Exception;
import org.apache.cxf.transport.http.AbstractHTTPDestination;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.*;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.soap.SOAPBinding;

@WebServiceProvider
@ServiceMode(value = Service.Mode.MESSAGE)
@BindingType(value = SOAPBinding.SOAP12HTTP_BINDING)
@Singleton
public class As4Provider implements Provider<SOAPMessage> {

    @Resource
    private WebServiceContext context;

    @Inject
    private As4InboundHandler handler;

    @Override
    public SOAPMessage invoke(SOAPMessage request) {
        MessageContext messageContext = context.getMessageContext();
        HttpServletResponse httpRes = (HttpServletResponse) messageContext.get(AbstractHTTPDestination.HTTP_RESPONSE);
        httpRes.setStatus(HttpServletResponse.SC_OK);

        try {
            return handler.handle(request, messageContext);
        } catch (OxalisAs4Exception e) {
            httpRes.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            throw new WebServiceException(e);
        }
    }
}
