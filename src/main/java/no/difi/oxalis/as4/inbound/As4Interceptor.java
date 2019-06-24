package no.difi.oxalis.as4.inbound;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import no.difi.oxalis.as4.lang.OxalisAs4Exception;
import no.difi.oxalis.as4.util.As4MessageFactory;
import no.difi.oxalis.as4.util.Constants;
import no.difi.oxalis.as4.util.Marshalling;
import no.difi.oxalis.as4.util.MessageId;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.binding.soap.interceptor.AbstractSoapInterceptor;
import org.apache.cxf.headers.Header;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.Phase;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.CollaborationInfo;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.MessageInfo;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Messaging;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.UserMessage;
import org.w3c.dom.Node;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Singleton
public class As4Interceptor extends AbstractSoapInterceptor {

    private As4MessageFactory as4MessageFactory;

    @Inject
    public As4Interceptor(As4MessageFactory as4MessageFactory) {
        super(Phase.PRE_PROTOCOL);
        addBefore(OxalisAS4WsInInterceptor.class.getName());

        this.as4MessageFactory = as4MessageFactory;
    }

    @Override
    public void handleMessage(SoapMessage message) throws Fault {

        storeMessageIdInContext(message);
    }


    private void storeMessageIdInContext(Message message) throws Fault {

        SoapMessage soapMessage = (SoapMessage) message;
        Header header = soapMessage.getHeader(Constants.MESSAGING_QNAME);

        try {

            Unmarshaller unmarshaller = Marshalling.getInstance().getJaxbContext().createUnmarshaller();
            Messaging messaging =  unmarshaller.unmarshal((Node) header.getObject(), Messaging.class).getValue();



            Optional<String> messageId = Optional.ofNullable( messaging )
                    .map( Messaging::getUserMessage )
                    .map( Collection::stream ).orElseGet( Stream::empty )
                    .map( UserMessage::getMessageInfo )
                    .map( MessageInfo::getMessageId )
                    .findFirst( );

            messageId.ifPresent(id -> message.put( MessageId.MESSAGE_ID, new MessageId(id) ));
            messageId.orElseThrow(
                    () -> new Fault( new OxalisAs4Exception("MessageID is missing from UserMessage") )
            );


            Optional.ofNullable( messaging )
                    .map( Messaging::getUserMessage )
                    .map( Collection::stream ).orElseGet( Stream::empty )
                    .map( UserMessage::getCollaborationInfo )
                    .map(CollaborationInfo::getConversationId)
                    .findFirst()
                    .map(conversationId -> message.put("oxalis.as4.conversationId", conversationId));





        } catch (JAXBException e) {
            throw new Fault(e);
        }
    }


}
