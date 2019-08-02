## Oxalis outside PEPPOL

Oxalis is made to serve as an AccessPoint in the PEPPOL network.
If you intend to use Oxalis sending messages outside PEPPOL you should implement your own version of the relevant [Oxalis extension points](https://github.com/difi/oxalis/blob/master/doc/extension-points.adoc).

### CEF connectivity test

The following Oxalis features needs to be changed to be able to exchange messages with the CEF connectivity test:
* HeaderParser
  ```
  oxalis.header.parser=noop
  ```
  See [Oxalis extention-points](https://github.com/difi/oxalis/blob/master/doc/extension-points.adoc)
  In its default setup Oxalis will extract the SBDH form the payload. Sins the CEF connectivity test does not send messages with a SBDH, this will fail.
  To pass the test you can disable the parsing of the SBDH header by adding the following line to your configuration file:

* CEF pMode
  ```
  oxalis.as4.type=cef-connectivity
  ```
  This setting changes three tings in the AS4 header resulting from the diffrent pModes:
  1. Removes the reference to the PEPPOL TIA agreement
  2. Renamed the serviceType for the transmission to "urn:oasis:names:tc:ebcore:partyid-type:unregistered"
  3. Renamed the partyIdType of the sender and reciever to "urn:oasis:names:tc:ebcore:partyid-type:unregistered"