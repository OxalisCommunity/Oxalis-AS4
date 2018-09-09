package no.difi.oxalis.as4.inbound;

import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.ws.security.SecurityConstants;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.wss4j.common.crypto.Crypto;

import java.util.Map;

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

        super.handleMessage(msg);
    }
}
