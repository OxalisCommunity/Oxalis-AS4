package no.difi.oxalis.as4.util;

import lombok.experimental.UtilityClass;
import no.difi.oxalis.as4.lang.OxalisAs4Exception;
import no.difi.oxalis.commons.io.PeekingInputStream;
import org.apache.cxf.helpers.CastUtils;

import javax.xml.soap.AttachmentPart;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPMessage;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPInputStream;

@UtilityClass
public class SOAPMessageUtil {

    public static SOAPHeader getSoapHeader(SOAPMessage request) throws OxalisAs4Exception {
        try {
            return request.getSOAPHeader();
        } catch (SOAPException e) {
            throw new OxalisAs4Exception("Could not get SOAP header", e);
        }
    }

    public static PeekingInputStream getPeekingAttachmentStream(SOAPMessage request) throws OxalisAs4Exception {
        try {
            return new PeekingInputStream(getAttachmentStream(request));
        } catch (IOException e) {
            throw new OxalisAs4Exception("Could not get peeking attachment input stream", e);
        }
    }

    private static InputStream getAttachmentStream(SOAPMessage request) throws OxalisAs4Exception {
        Iterable<AttachmentPart> iterable = () -> CastUtils.cast(request.getAttachments());

        return getAttachmentStream(StreamSupport.stream(iterable.spliterator(), false)
                .findFirst()
                .orElseThrow(() -> new OxalisAs4Exception("No attachment present")));
    }

    private static InputStream getAttachmentStream(AttachmentPart attachmentPart) throws OxalisAs4Exception {
        try {
            return new GZIPInputStream(attachmentPart.getDataHandler().getInputStream());
        } catch (IOException | SOAPException e) {
            throw new OxalisAs4Exception("Could not get attachment input stream", e);
        }
    }
}
