package no.difi.oxalis.as4.util;

import lombok.Getter;
import no.difi.oxalis.api.tag.Tag;

@Getter
public class PeppolConfiguration implements Tag {
    public static final String IDENTIFIER = "AS4.PEPPOL";

    boolean signature, encryption = true;

    @Override
    public String getIdentifier() {
        return IDENTIFIER;
    }
}
