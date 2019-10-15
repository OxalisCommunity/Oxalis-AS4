package no.difi.oxalis.as4.inbound;

import com.google.inject.Inject;
import no.difi.oxalis.as4.util.OxalisAlgorithmSuiteLoader;
import org.apache.cxf.Bus;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.binding.soap.SoapVersion;
import org.apache.cxf.binding.soap.interceptor.CheckFaultInterceptor;
import org.apache.cxf.binding.soap.interceptor.ReadHeadersInterceptor;
import org.apache.cxf.binding.soap.interceptor.StartBodyInterceptor;
import org.apache.cxf.interceptor.AttachmentInInterceptor;
import org.apache.cxf.interceptor.StaxInInterceptor;
import org.apache.cxf.jaxws.EndpointImpl;
import org.apache.cxf.jaxws.handler.soap.SOAPHandlerFaultInInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.MultipleEndpointObserver;
import org.apache.cxf.ws.policy.PolicyBuilder;
import org.apache.cxf.ws.policy.PolicyConstants;
import org.apache.cxf.ws.policy.WSPolicyFeature;
import org.apache.cxf.ws.security.policy.custom.AlgorithmSuiteLoader;
import org.apache.cxf.wsdl.interceptors.AbstractEndpointSelectionInterceptor;
import org.apache.neethi.Policy;

import javax.xml.ws.Endpoint;
import javax.xml.ws.handler.Handler;
import java.util.ArrayList;
import java.util.List;

public class As4EndpointsPublisherImpl implements As4EndpointsPublisher {

    @Inject
    private As4Provider as4Provider;

    @Inject
    private AbstractEndpointSelectionInterceptor endpointSelector;

    @Inject
    private As4FaultInHandler as4FaultInHandler;

    @Inject
    private As4Interceptor oxalisAs4Interceptor;

    @Override
    public EndpointImpl publish(Bus bus) {


        bus.setExtension(new OxalisAlgorithmSuiteLoader(bus), AlgorithmSuiteLoader.class);

        EndpointImpl endpoint = (EndpointImpl) Endpoint.publish("/", as4Provider, new WSPolicyFeature());

        try {

            PolicyBuilder builder = bus.getExtension(org.apache.cxf.ws.policy.PolicyBuilder.class);
            Policy policy = builder.getPolicy(getClass().getResourceAsStream("/policy.xml"));
            bus.getProperties().put(PolicyConstants.POLICY_OVERRIDE, policy);
        }catch (Exception e){

        }

        endpoint.getServer().getEndpoint().put("allow-multiplex-endpoint", Boolean.TRUE);
        endpoint.getServer().getEndpoint()
                .put(As4EndpointSelector.ENDPOINT_NAME, As4EndpointSelector.OXALIS_AS4_ENDPOINT_NAME);


        List<Handler> chain = new ArrayList<>();
        chain.add(as4FaultInHandler);
        chain.add(new MessagingHandler());
        endpoint.getBinding().setHandlerChain(chain);


        endpoint.getInInterceptors().add(oxalisAs4Interceptor);

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
