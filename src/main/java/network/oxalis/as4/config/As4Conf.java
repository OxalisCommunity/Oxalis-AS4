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

package network.oxalis.as4.config;

import network.oxalis.api.settings.DefaultValue;
import network.oxalis.api.settings.Path;
import network.oxalis.api.settings.Title;

@Title("AS4")
public enum As4Conf {

    @Path("oxalis.as4.hostname")
    @DefaultValue("")
    HOSTNAME,

    @Path("oxalis.as4.msgidgen")
    @DefaultValue("default")
    MSGID_GENERATOR,

    @Path("oxalis.as4.type")
    @DefaultValue("peppol")
    TYPE,

    /**
     * Defines maximum possible size of SBDH header in bytes. It is needed to limit 
     * parsing of SBD to prevent DOS attack, and be able to rewind the input 
     * stream to the start before passing it to persister.
     */
    @Path("oxalis.as4.sbdh.limit")
    @DefaultValue("65536")
    SBDH_LIMIT
}
