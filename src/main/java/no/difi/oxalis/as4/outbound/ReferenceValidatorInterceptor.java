package no.difi.oxalis.as4.outbound;

import no.difi.oxalis.as4.lang.OxalisAs4Exception;
import no.difi.oxalis.as4.util.SOAPHeaderParser;
import org.springframework.ws.client.WebServiceClientException;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.saaj.SaajSoapMessage;
import org.w3.xmldsig.ReferenceType;
import org.w3.xmldsig.TransformType;

import javax.xml.soap.SOAPException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class ReferenceValidatorInterceptor implements ClientInterceptor {

    @Override
    public boolean handleRequest(MessageContext messageContext) throws WebServiceClientException {
        As4MessageContext as4context = As4MessageContext.from(messageContext);
        SaajSoapMessage request = (SaajSoapMessage) messageContext.getRequest();
        try {
            List<ReferenceType> refs = SOAPHeaderParser.getReferenceListFromSignedInfo(request.getSaajMessage().getSOAPHeader());
            as4context.addReference(refs);
        } catch (OxalisAs4Exception | SOAPException e) {
//            throw new RuntimeException("Could not get references from soap header", e);
        }

        return true;
    }

    @Override
    public boolean handleResponse(MessageContext messageContext) throws WebServiceClientException {
        As4MessageContext as4context = As4MessageContext.from(messageContext);
        SaajSoapMessage response = (SaajSoapMessage) messageContext.getResponse();
        try {
            List<ReferenceType> refs = SOAPHeaderParser.getReferenceListFromNonRepudiationInformation(response.getSaajMessage().getSOAPHeader());
            validateReferences(as4context.getReferences(), refs);
        } catch (OxalisAs4Exception | SOAPException e) {
//            throw new RuntimeException("Could not get references from soap header", e);
        }
        return true;
    }

    @Override
    public boolean handleFault(MessageContext messageContext) throws WebServiceClientException {
        return true;
    }

    @Override
    public void afterCompletion(MessageContext messageContext, Exception ex) throws WebServiceClientException {

    }


    /**
     * https://github.com/digipost/sdp-shared/blob/master/api-client/src/main/java/no/digipost/api/interceptors/steps/ReferenceValidatorStep.java
     *
     * @param expected references
     * @param actual references
     */
    private void validateReferences(List<ReferenceType> expected, List<ReferenceType> actual) {
        for (ReferenceType expectedRef : expected) {
            boolean found = false;
            for (ReferenceType actualRef : actual) {
                if (actualRef.getURI().equals(expectedRef.getURI())) {
                    found = true;
                    validateDigest(expectedRef, actualRef);
                    break;
                }
            }
            if (!found) {
                throw new RuntimeException("Missing NonRepudiationInformation->MessagePartNRInformation for " + expectedRef.getURI());
            }
        }
    }

    private void validateDigest(ReferenceType expectedRef, ReferenceType actualRef) {
        if (!expectedRef.getDigestMethod().getAlgorithm().equals(actualRef.getDigestMethod().getAlgorithm())) {
            throw new RuntimeException("Unexpected digest method. Expected:" + expectedRef.getDigestMethod().getAlgorithm() + " Actual:" + actualRef.getDigestMethod().getAlgorithm());
        }
        if (!Arrays.equals(expectedRef.getDigestValue(), actualRef.getDigestValue())) {
            throw new RuntimeException("Unexpected digest value. Expected:" + new String(Base64.getEncoder().encode(expectedRef.getDigestValue())) + " Actual:" + new String(Base64.getEncoder().encode(actualRef.getDigestValue())));
        }
        validateTransforms(expectedRef, actualRef);
    }

    private void validateTransforms(ReferenceType expectedRef, ReferenceType actualRef) {
        boolean expHasTransforms = expectedRef.getTransforms() != null;
        boolean actHasTransforms = actualRef.getTransforms() != null;
        if (expHasTransforms != actHasTransforms) {
            throw new RuntimeException("Expected to " + (expHasTransforms ? "" : "not ") + "have transforms");
        }
        if (!expHasTransforms) {
            return;
        }

        if (expectedRef.getTransforms().getTransform().size() != actualRef.getTransforms().getTransform().size()) {
            throw new RuntimeException("Unexpected number of transforms. Expected:" + expectedRef.getTransforms().getTransform().size() + " Actual:" + actualRef.getTransforms().getTransform().size());
        }
        for (int i = 0; i < expectedRef.getTransforms().getTransform().size(); i++) {
            TransformType expT = expectedRef.getTransforms().getTransform().get(i);
            TransformType actT = actualRef.getTransforms().getTransform().get(i);
            if (!expT.getAlgorithm().equals(actT.getAlgorithm())) {
                throw new RuntimeException("Unexpected transform. Expected:" + expT.getAlgorithm() + " Actual:" + actT.getAlgorithm());
            }
        }
    }
}
