package no.difi.oxalis.as4.common;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import no.difi.oxalis.api.header.HeaderParser;
import no.difi.oxalis.api.util.Type;
import no.difi.vefa.peppol.common.model.Header;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Singleton
@Type("dummy")
public class DummyHeaderParser implements HeaderParser {

    @Override
    public Header parse(InputStream inputStream) {

        log.debug("DummyHeaderParser: parse");

        try {
            byte[] drain = new byte[500];
            inputStream.read(drain);
        } catch (IOException e) {
            log.error("IOException while parsing header", e);
        }

        return new Header();
    }
}
