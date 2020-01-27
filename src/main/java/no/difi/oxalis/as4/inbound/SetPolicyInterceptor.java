package no.difi.oxalis.as4.inbound;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import no.difi.oxalis.as4.util.Constants;
import no.difi.oxalis.as4.util.Marshalling;
import no.difi.oxalis.as4.util.PolicyService;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.binding.soap.interceptor.AbstractSoapInterceptor;
import org.apache.cxf.headers.Header;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.ws.policy.PolicyConstants;
import org.apache.cxf.ws.policy.PolicyInInterceptor;
import org.apache.neethi.Policy;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Messaging;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.UserMessage;
import org.w3c.dom.Node;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Singleton
public class SetPolicyInterceptor extends AbstractSoapInterceptor {

    private final JAXBContext jaxbContext = Marshalling.getInstance();
    private final PolicyService policyService;

    @Inject
    public SetPolicyInterceptor(PolicyService policyService) {
        super(Phase.SETUP);
        this.policyService = policyService;
        addBefore(PolicyInInterceptor.class.getName());
    }

    @Override
    public void handleMessage(SoapMessage message) throws Fault {
        Optional<UserMessage> userMessage = getMessaging(message)
                .map(Messaging::getUserMessage)
                .map(Collection::stream).orElseGet(Stream::empty)
                .findFirst();

        try {
            Policy policy = userMessage.isPresent()
                    ? policyService.getPolicy(userMessage.get().getCollaborationInfo())
                    : policyService.getPolicy();
            message.put(PolicyConstants.POLICY_OVERRIDE, policy);
        } catch (Exception e) {
            throw new Fault(e);
        }
    }

    private Optional<Messaging> getMessaging(Message message) {
        SoapMessage soapMessage = (SoapMessage) message;
        Header header = soapMessage.getHeader(Constants.MESSAGING_QNAME);

        if(header == null) {
            return Optional.empty();
        }

        try {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            Messaging messaging = unmarshaller.unmarshal((Node) header.getObject(), Messaging.class).getValue();
            return Optional.of(messaging);
        } catch (JAXBException e) {
            throw new Fault(e);
        }
    }

}
