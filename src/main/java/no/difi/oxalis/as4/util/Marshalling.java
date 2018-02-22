package no.difi.oxalis.as4.util;

import no.difi.commons.sbdh.jaxb.StandardBusinessDocument;
import org.oasis_open.docs.ebxml_bp.ebbp_signals_2.NonRepudiationInformation;
import org.oasis_open.docs.ebxml_msg.ebms.v3_0.ns.core._200704.Messaging;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.w3.xmldsig.ReferenceType;
import org.xmlsoap.schemas.soap.envelope.Envelope;

public class Marshalling {

    public static Jaxb2Marshaller getInstance() {
        return InitializedMarshaller.instance;
    }

    public static Jaxb2Marshaller createMarshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPaths(
                StandardBusinessDocument.class.getPackage().getName(),
                Envelope.class.getPackage().getName(),
                org.w3.soap.Envelope.class.getPackage().getName(),
                ReferenceType.class.getPackage().getName(),
                NonRepudiationInformation.class.getPackage().getName(),
                Messaging.class.getPackage().getName());
        return marshaller;
    }

    private static class InitializedMarshaller {
        private static final Jaxb2Marshaller instance = createMarshaller();

        static {
            try {
                instance.afterPropertiesSet();
            } catch (Exception e) {
                throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e.getMessage(), e);
            }
        }
    }
}

