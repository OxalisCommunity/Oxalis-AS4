package no.difi.oxalis.as4.inbound;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import no.difi.oxalis.as4.lang.OxalisAs4Exception;
import org.apache.cxf.transport.http.AbstractHTTPDestination;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
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
        MessageContext ctx = context.getMessageContext();
        HttpServletRequest httpReq = (HttpServletRequest) ctx.get(AbstractHTTPDestination.HTTP_REQUEST);
        HttpServletResponse httpRes = (HttpServletResponse) ctx.get(AbstractHTTPDestination.HTTP_RESPONSE);
        httpRes.setStatus(HttpServletResponse.SC_OK);


//        JCEMapper.register("http://custom.difi.no/2018/07/xmlenc#rsa-oaep-sha256-mgf1",
//                new JCEMapper.Algorithm("RSA", "RSA/ECB/OAEPWithSHA-256AndMGF1Padding", "KeyTransport"));


        try {
            return handler.handle(request);
        } catch (OxalisAs4Exception e) {
            // TODO: process error
            httpRes.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            throw new RuntimeException(e);
        }
    }
}
