package network.oxalis.as4.outbound;

import network.oxalis.as4.util.TransmissionRequestUtil;
import no.difi.vefa.peppol.common.model.DocumentTypeIdentifier;

public class DefaultActionProvider implements ActionProvider {
    @Override
    public String getAction(DocumentTypeIdentifier documentTypeIdentifier) {
        return TransmissionRequestUtil.translateDocumentTypeToAction(documentTypeIdentifier);
    }
}
