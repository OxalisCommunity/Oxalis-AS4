package no.difi.oxalis.as4.inbound;

import com.google.inject.Inject;
import org.apache.cxf.Bus;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.binding.soap.SoapVersion;
import org.apache.cxf.binding.soap.interceptor.CheckFaultInterceptor;
import org.apache.cxf.binding.soap.interceptor.ReadHeadersInterceptor;
import org.apache.cxf.binding.soap.interceptor.StartBodyInterceptor;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.interceptor.AttachmentInInterceptor;
import org.apache.cxf.interceptor.StaxInInterceptor;
import org.apache.cxf.jaxws.EndpointImpl;
import org.apache.cxf.jaxws.handler.soap.SOAPHandlerFaultInInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.MultipleEndpointObserver;
import org.apache.cxf.ws.policy.WSPolicyFeature;
import org.apache.cxf.wsdl.interceptors.AbstractEndpointSelectionInterceptor;

import javax.xml.ws.Endpoint;
import java.util.Arrays;

public class As4EndpointsPublisherImpl implements As4EndpointsPublisher {

    @Inject
    private As4Provider as4Provider;

    @Inject
    private AbstractEndpointSelectionInterceptor endpointSelector;

    @Inject
    private As4FaultInHandler as4FaultInHandler;

    @Inject
    private As4Interceptor oxalisAs4Interceptor;

    @Inject
    private SetPolicyInInterceptor setPolicyInInterceptor;

    @Inject
    private SetPolicyOutInterceptor setPolicyOutInterceptor;

    @Override
    public EndpointImpl publish(Bus bus) {
        EndpointImpl endpoint = (EndpointImpl) Endpoint.publish("/", as4Provider,
                new LoggingFeature(),
                new WSPolicyFeature());

        endpoint.getServer().getEndpoint().put("allow-multiplex-endpoint", Boolean.TRUE);
        endpoint.getServer().getEndpoint()
                .put(As4EndpointSelector.ENDPOINT_NAME, As4EndpointSelector.OXALIS_AS4_ENDPOINT_NAME);

        endpoint.getBinding().setHandlerChain(Arrays.asList(as4FaultInHandler, new MessagingHandler()));
        endpoint.getInInterceptors().add(oxalisAs4Interceptor);
        endpoint.getInInterceptors().add(setPolicyInInterceptor);
        endpoint.getOutInterceptors().add(setPolicyOutInterceptor);
        endpoint.getInFaultInterceptors().add(setPolicyInInterceptor);
        endpoint.getOutFaultInterceptors().add(setPolicyOutInterceptor);

        MultipleEndpointObserver newMO = new MultipleEndpointObserver(bus) {
            @Override
            protected Message createMessage(Message message) {
                return new SoapMessage(message);
            }
        };

        newMO.getBindingInterceptors().add(new AttachmentInInterceptor());
        newMO.getBindingInterceptors().add(new StaxInInterceptor());
        newMO.getBindingInterceptors().add(new SOAPHandlerFaultInInterceptor(endpoint.getBinding()));
        newMO.getBindingInterceptors().add(new ReadHeadersInterceptor(bus, (SoapVersion) null));
        newMO.getBindingInterceptors().add(new StartBodyInterceptor());
        newMO.getBindingInterceptors().add(new CheckFaultInterceptor());

        // Add in a default selection interceptor
        newMO.getRoutingInterceptors().add(endpointSelector);
        newMO.getEndpoints().add(endpoint.getServer().getEndpoint());

        endpoint.getServer().getDestination().setMessageObserver(newMO);

        return endpoint;
    }
}
