package no.difi.oxalis.as4.outbound;

import no.difi.vefa.peppol.common.model.DocumentTypeIdentifier;

public interface ActionProvider {

    String getAction(DocumentTypeIdentifier documentTypeIdentifier);
}
