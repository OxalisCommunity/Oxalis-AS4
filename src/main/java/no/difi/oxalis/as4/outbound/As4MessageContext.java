package no.difi.oxalis.as4.outbound;

import com.google.common.collect.Lists;
import org.springframework.ws.context.MessageContext;
import org.w3.xmldsig.ReferenceType;

import java.util.List;

public class As4MessageContext {

    private static final String PROP_NAME = "no.difi.oxalis.as4.context";

    private List<ReferenceType> references = Lists.newArrayList();

    public static As4MessageContext from(final MessageContext messageContext) {
        As4MessageContext context = (As4MessageContext) messageContext.getProperty(PROP_NAME);
        if (context == null) {
            context = new As4MessageContext();
            messageContext.setProperty(PROP_NAME, context);
        }

        return context;
    }

    public void addReference(ReferenceType ref) {
        references.add(ref);
    }

    public void addReference(List<ReferenceType> refs) {
        references.addAll(refs);
    }

    public List<ReferenceType> getReferences() {
        return references;
    }
}
