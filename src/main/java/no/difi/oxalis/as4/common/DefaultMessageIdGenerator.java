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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.SneakyThrows;
import no.difi.oxalis.api.settings.Settings;
import no.difi.oxalis.api.util.Type;
import no.difi.oxalis.as4.api.MessageIdGenerator;
import no.difi.oxalis.as4.config.As4Conf;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

@Singleton
@Type("default")
public class DefaultMessageIdGenerator implements MessageIdGenerator {

    private final String hostname;

    public DefaultMessageIdGenerator(String hostname) {
        this.hostname = hostname;
    }

    @Inject
    public DefaultMessageIdGenerator(Settings<As4Conf> settings) {
        this.hostname = getHostname(settings);
    }

    private String getHostname(Settings<As4Conf> settings) {
        String name = settings.getString(As4Conf.HOSTNAME).trim();
        return name.isEmpty() ? getLocalHostName() : name;
    }

    @SneakyThrows(UnknownHostException.class)
    private String getLocalHostName() {
        return InetAddress.getLocalHost().getCanonicalHostName();
    }

    @Override
    public String generate() {
        return String.format("%s@%s", UUID.randomUUID().toString(), hostname);
    }
}
