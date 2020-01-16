/*
 * Copyright 2010-2017 Norwegian Agency for Public Management and eGovernment (Difi)
 *
 * Licensed under the EUPL, Version 1.1 or – as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 *
 * You may not use this work except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/community/eupl/og_page/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package no.difi.oxalis.as4.common;

import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import no.difi.oxalis.api.header.HeaderParser;
import no.difi.oxalis.api.settings.Settings;
import no.difi.oxalis.as4.api.MessageIdGenerator;
import no.difi.oxalis.as4.config.As4Conf;
import no.difi.oxalis.as4.config.TrustStoreSettings;
import no.difi.oxalis.as4.util.As4MessageFactory;
import no.difi.oxalis.as4.util.PolicyService;
import no.difi.oxalis.commons.guice.ImplLoader;
import no.difi.oxalis.commons.guice.OxalisModule;
import no.difi.oxalis.commons.settings.SettingsBuilder;

import static no.difi.oxalis.as4.common.AS4Constants.CEF_CONNECTIVITY;

@Slf4j
public class As4CommonModule extends OxalisModule {

    @Override
    protected void configure() {
        bindTyped(MessageIdGenerator.class, DefaultMessageIdGenerator.class);
        bindTyped(HeaderParser.class, DummyHeaderParser.class);
        bind(As4MessageFactory.class);
        SettingsBuilder.with(binder(), TrustStoreSettings.class);
        bindSettings(As4Conf.class);
    }

    @Provides
    @Singleton
    public PolicyService policyService(Settings<As4Conf> settings) {
        return new PolicyService(getPolicyClasspath(settings));
    }

    private String getPolicyClasspath(Settings<As4Conf> settings) {
        return CEF_CONNECTIVITY.equalsIgnoreCase(settings.getString(As4Conf.TYPE))
                ? "/eDeliveryAS4Policy.xml"
                : "/eDeliveryAS4Policy_BST.xml";
    }

    @Provides
    @Singleton
    public MessageIdGenerator getMessageIdGenerator(Injector injector, Settings<As4Conf> settings) {
        return ImplLoader.get(injector, MessageIdGenerator.class, settings, As4Conf.MSGID_GENERATOR);
    }
}
