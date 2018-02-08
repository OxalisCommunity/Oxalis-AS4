package no.difi.oxalis.as4.outbound;

import org.apache.wss4j.dom.handler.RequestData;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;

public class WsSecurityInterceptor extends Wss4jSecurityInterceptor {
    @Override
    protected RequestData initializeRequestData(MessageContext messageContext) {
        RequestData requestData = super.initializeRequestData(messageContext);
        requestData.setAttachmentCallbackHandler(new AttachmentCallbackHandler((SoapMessage)messageContext.getRequest()));
        return requestData;
    }
}
