package no.difi.oxalis.as4.inbound;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class As4EnvelopeHeader {

    private String messageId;
    private String conversationId;

    private List<String> fromPartyId;
    private String fromPartyRole;

    private List<String> toPartyId;
    private String toPartyRole;

    private String service;
    private String action;

    private Map<String, String> messageProperties = new HashMap<>();

    private List<String> payloadCIDs = new ArrayList<>();
}
