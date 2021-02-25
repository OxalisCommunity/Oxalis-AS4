[![Oxalis-AS4 Master Build](https://github.com/OxalisCommunity/Oxalis-AS4/workflows/Oxalis-AS4%20Master%20Build/badge.svg?branch=master)](https://github.com/OxalisCommunity/oxalis-as4/actions?query=workflow%3A%22oxalis-as4%20Master%20Build%22)
[![Maven Central](https://img.shields.io/maven-central/v/network.oxalis/oxalis-as4.svg)](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22network.oxalis%22%20AND%20a%3A%22oxalis-as4%22)

The contents of this repository is currently in the process of switching ownership to [NorStella Oxalis Community](https://www.oxalis.network/). You as a user of Oxalis may find it interesting to [join the community](https://www.oxalis.network/join) for access to support, roadmap, early access and more. The founding meeting held online, Thursday November 19, 2020, at 08:30–10:30 CET. 

The Oxalis Community annual meeting scheduled to be held on 25th of March 2021. Should you wish to sign up as a member of the Oxalis Community, please use the registration form available from this site: [link](https://www.oxalis.network/join)  

---
# Oxalis-AS4

This is a release candidate of Oxalis with support for PEPPOL AS4 pMode.
It supports Oxalis v4.0.3, v4.0.4 and v4.1.x and passes the CEF conformance test (excluding tests requiring multiple payloads).

AS4 messages is triggered by setting the _transport profile identifier_ of one of your endpoints to "_peppol-transport-as4-v2_0_" in the SMP. No further configuration is needed beyond the standard Oxalis setup.
For general instructions on how to install and use Oxalis, please refer to [oxalis installation guide](https://github.com/difi/oxalis/blob/master/doc/installation.md).

* [Installation guide](docs/installation/index.md)
* [OpenPEPPOL Test Bed](docs/peppol-test-bed/index.md)
* [CEF connectivity test](docs/cef-connectivity/index.md)



