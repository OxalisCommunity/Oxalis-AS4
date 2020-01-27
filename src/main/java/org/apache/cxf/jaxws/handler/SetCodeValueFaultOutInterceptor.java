package org.apache.cxf.jaxws.handler;

import no.difi.oxalis.as4.inbound.As4SoapFaultInterceptor;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.binding.soap.interceptor.AbstractSoapInterceptor;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.jaxws.handler.soap.SOAPHandlerFaultOutInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.Phase;

import java.util.Iterator;

/**
 * Copied from Domibus and adapted to Oxalis-AS4
 * Domibus version created by idragusa on 5/26/16.
 * <p>
 * [EDELIVERY-1117]
 * The scope of this interceptor is to replace the existing CXF SOAPHandlerFaultOutInterceptor with our
 * own As4SoapFaultInterceptor that sets the code value to Receiver instead of HandleFault
 * which is non-standard and causes an exception.
 */
public class SetCodeValueFaultOutInterceptor extends AbstractSoapInterceptor {
    public SetCodeValueFaultOutInterceptor() {
        super(Phase.PRE_PROTOCOL_FRONTEND);
        getBefore().add(SOAPHandlerFaultOutInterceptor.class.getName());
    }

    @Override
    public void handleMessage(SoapMessage message) {
        if (message == null ||
                message.getInterceptorChain() == null ||
                message.getInterceptorChain().iterator() == null)
            return;

        Iterator<Interceptor<? extends Message>> it = message.getInterceptorChain().iterator();
        while (it.hasNext()) {
            Interceptor interceptor = it.next();
            if (interceptor instanceof SOAPHandlerFaultOutInterceptor) {
                message.getInterceptorChain().add(new As4SoapFaultInterceptor(((SOAPHandlerFaultOutInterceptor) interceptor).getBinding()));
                message.getInterceptorChain().remove(interceptor);
            }
        }
    }
}
