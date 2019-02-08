package no.difi.oxalis.as4.inbound;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import no.difi.oxalis.as4.lang.OxalisAs4Exception;

import javax.xml.soap.SOAPMessage;

@Singleton
public class As4InboundHandler {

    private final As4InboundParser as4InboundParser;
    private final As4ResponseProvider as4ResponseProvider;
    private final As4ReceiptPersister as4ReceiptPersister;

    @Inject
    public As4InboundHandler(As4InboundParser as4InboundParser, As4ResponseProvider as4ResponseProvider, As4ReceiptPersister as4ReceiptPersister) {
        this.as4InboundParser = as4InboundParser;
        this.as4ResponseProvider = as4ResponseProvider;
        this.as4ReceiptPersister = as4ReceiptPersister;
    }

    SOAPMessage handle(SOAPMessage request) throws OxalisAs4Exception {
        As4InboundInfo inboundInfo = as4InboundParser.parse(request);
        SOAPMessage response = as4ResponseProvider.getSOAPResponse(inboundInfo);
        as4ReceiptPersister.persistReceipt(inboundInfo, response);
        return response;
    }
}
