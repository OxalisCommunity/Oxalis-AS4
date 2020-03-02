# Oxalis-AS4

This is a release candidate of Oxalis with support for PEPPOL AS4 pMode.
It supports Oxalis v4.0.3, v4.0.4, v4.1.0 and v4.1.1 and passes the CEF conformance test (excluding tests requiring multiple payloads).

AS4 messages is triggered by setting the _transport profile identifier_ of one of your endpoints to "_peppol-transport-as4-v2_0_" in the SMP. No further configuration is needed beyond the standard Oxalis setup.
For general instructions on how to install and use Oxalis, please refer to [oxalis installation guide](https://github.com/difi/oxalis/blob/master/doc/installation.md).

* [Installation guide](docs/installation/index.md)
* [OpenPEPPOL Test Bed](docs/peppol-test-bed/index.md)
* [CEF connectivity test](docs/cef-connectivity/index.md)



