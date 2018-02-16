package no.difi.oxalis.as4.inbound;

import org.apache.wss4j.common.ext.WSPasswordCallback;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.io.IOException;

public class PasswordCallbackHandler implements CallbackHandler {

    private String encryptPassword;

    PasswordCallbackHandler(String encryptPassword) {
        this.encryptPassword = encryptPassword;
    }

    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        WSPasswordCallback cb = (WSPasswordCallback) callbacks[0];
        cb.setPassword(encryptPassword);
    }
}
