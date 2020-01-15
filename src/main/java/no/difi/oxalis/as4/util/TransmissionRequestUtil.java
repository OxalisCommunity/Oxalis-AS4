package no.difi.oxalis.as4.util;

import lombok.experimental.UtilityClass;
import no.difi.vefa.peppol.common.model.DocumentTypeIdentifier;
import no.difi.vefa.peppol.common.model.ParticipantIdentifier;

@UtilityClass
public class TransmissionRequestUtil {

    public static String translateDocumentTypeToAction(DocumentTypeIdentifier documentTypeIdentifier) {
        return documentTypeIdentifier.getScheme() == null ||
                documentTypeIdentifier.getScheme().getIdentifier() == null ||
                documentTypeIdentifier.getScheme().getIdentifier().trim().isEmpty() ?

                documentTypeIdentifier.getIdentifier() :
                documentTypeIdentifier.toString();
    }

    public static String translateParticipantIdentifierToRecipient(ParticipantIdentifier documentTypeIdentifier) {
        return documentTypeIdentifier.getScheme() == null ||
                documentTypeIdentifier.getScheme().getIdentifier() == null ||
                documentTypeIdentifier.getScheme().getIdentifier().trim().isEmpty() ?

                documentTypeIdentifier.getIdentifier() :
                documentTypeIdentifier.toString();
    }
}
