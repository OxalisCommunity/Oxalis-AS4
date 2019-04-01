package no.difi.oxalis.as4.util;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;

public class TestInterceptor extends AbstractPhaseInterceptor<Message> {

    public TestInterceptor(String phase) {
        super(phase);
    }

    @Override
    public void handleMessage(Message message) throws Fault {

    }
}
