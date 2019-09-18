package no.difi.oxalis.as4.config;

import no.difi.oxalis.api.settings.DefaultValue;
import no.difi.oxalis.api.settings.Path;
import no.difi.oxalis.api.settings.Secret;
import no.difi.oxalis.api.settings.Title;

@Title("Trust store")
public enum TrustStore {
    @Path("oxalis.truststore.path")
    @DefaultValue("None")
    PATH,
    @Path("oxalis.truststore.password")
    @DefaultValue("changeit")
    @Secret
    PASSWORD
}
