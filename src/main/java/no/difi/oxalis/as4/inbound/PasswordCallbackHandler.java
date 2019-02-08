package no.difi.oxalis.as4.inbound;

import lombok.RequiredArgsConstructor;
import org.apache.wss4j.common.ext.WSPasswordCallback;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

@RequiredArgsConstructor
public class PasswordCallbackHandler implements CallbackHandler {

    private final String encryptPassword;

    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        for (Callback callback : callbacks) {
            if (callback instanceof WSPasswordCallback) {
                WSPasswordCallback cb = (WSPasswordCallback) callback;
                cb.setPassword(encryptPassword);
            } else if (callback instanceof PasswordCallback) {
                PasswordCallback cb = (PasswordCallback) callback;
                cb.setPassword(encryptPassword != null ? encryptPassword.toCharArray() : new char[0]);
            } else {
                throw new UnsupportedEncodingException("Unable to process callback of type " + callback.getClass().getSimpleName());
            }
        }
    }
}
