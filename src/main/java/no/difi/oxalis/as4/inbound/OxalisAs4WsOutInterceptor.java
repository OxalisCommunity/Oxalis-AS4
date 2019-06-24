package no.difi.oxalis.as4.inbound;

import no.difi.oxalis.as4.util.PeppolConfiguration;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.ws.security.SecurityConstants;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.wss4j.common.crypto.Crypto;
import org.apache.wss4j.dom.handler.WSHandlerConstants;

import java.util.*;

public class OxalisAs4WsOutInterceptor extends WSS4JOutInterceptor {
    private Crypto crypto;
    private String alias;

    OxalisAs4WsOutInterceptor(Map<String,Object> props, Crypto crypto, String alias) {
        super(props);
        this.crypto = crypto;
        this.alias = alias;
    }

    @Override
    public void handleMessage(SoapMessage msg) throws Fault {
        msg.put(SecurityConstants.ENCRYPT_CRYPTO, crypto);
        msg.put(SecurityConstants.SIGNATURE_CRYPTO, crypto);
        msg.put(SecurityConstants.SIGNATURE_USERNAME, alias);
        msg.put(SecurityConstants.USERNAME, alias);


        Optional.ofNullable(msg.get("oxalis.tag"))
                .filter(PeppolConfiguration.class::isInstance)
                .map(PeppolConfiguration.class::cast)
                .map(PeppolConfiguration::getActions)
                .ifPresent(actionlist -> {
                    StringJoiner actions = new StringJoiner(" ");
                    for(String action : actionlist ){
                        actions.add(action);
                    }

                    msg.put(WSHandlerConstants.ACTION, actions.toString());
                });

        super.handleMessage(msg);
    }
}
