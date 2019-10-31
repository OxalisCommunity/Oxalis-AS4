package no.difi.oxalis.as4.util;

import no.difi.vefa.peppol.common.model.DocumentTypeIdentifier;
import no.difi.vefa.peppol.common.model.ParticipantIdentifier;
import no.difi.vefa.peppol.common.model.ProcessIdentifier;
import no.difi.vefa.peppol.common.model.Scheme;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TransmissionRequestUtilTest {

    private static String PROVIDED_VALUE = "provided value";
    private static String PROVIDED_SCHEME = "provided scheme";

    @Test
    public void testTranslateDocumentTypeToAction_defaultScheme() {
        String result = TransmissionRequestUtil.translateDocumentTypeToAction( DocumentTypeIdentifier.of(PROVIDED_VALUE) );
        Assert.assertEquals(DocumentTypeIdentifier.DEFAULT_SCHEME + "::" + PROVIDED_VALUE, result);
    }

    @Test
    public void testTranslateDocumentTypeToAction_nullScheme() {
        String result = TransmissionRequestUtil.translateDocumentTypeToAction( DocumentTypeIdentifier.of(PROVIDED_VALUE, null) );
        Assert.assertEquals(PROVIDED_VALUE, result);
    }

    @Test
    public void testTranslateDocumentTypeToAction_providedScheme() {
        String result = TransmissionRequestUtil.translateDocumentTypeToAction( DocumentTypeIdentifier.of(PROVIDED_VALUE, Scheme.of(PROVIDED_SCHEME)) );
        Assert.assertEquals(PROVIDED_SCHEME + "::" + PROVIDED_VALUE, result);
    }



    @Test
    public void testTranslateParticipantIdentifierToRecipient_defaultScheme() {
        String result = TransmissionRequestUtil.translateParticipantIdentifierToRecipient( ParticipantIdentifier.of(PROVIDED_VALUE) );
        Assert.assertEquals(ParticipantIdentifier.DEFAULT_SCHEME + "::" + PROVIDED_VALUE, result);
    }

    @Test
    public void testTranslateParticipantIdentifierToRecipient_nullScheme() {
        String result = TransmissionRequestUtil.translateParticipantIdentifierToRecipient( ParticipantIdentifier.of(PROVIDED_VALUE, null) );
        Assert.assertEquals(PROVIDED_VALUE, result);
    }

    @Test
    public void testTranslateParticipantIdentifierToRecipient_providedScheme() {
        String result = TransmissionRequestUtil.translateParticipantIdentifierToRecipient( ParticipantIdentifier.of(PROVIDED_VALUE, Scheme.of(PROVIDED_SCHEME)) );
        Assert.assertEquals(PROVIDED_SCHEME + "::" + PROVIDED_VALUE, result);
    }

}
