package no.difi.oxalis.as4.util;

import org.apache.commons.io.IOUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

public class CompressionUtilTest {
    static final String DATA = "Lorem ipsum dolor sit amet";

    @Test
    public void simple() throws Exception{
        InputStream sourceStream = new ByteArrayInputStream(DATA.getBytes());
        ExecutorService executor = Executors.newSingleThreadExecutor();

        InputStream compressedStream = new CompressionUtil(executor).getCompressedStream(sourceStream);

        GZIPInputStream decompressedStream = new GZIPInputStream(compressedStream);
        List<String> lines = IOUtils.readLines(decompressedStream, Charset.defaultCharset());

        Assert.assertEquals(1, lines.size());
        Assert.assertEquals(DATA, lines.get(0));
    }
}
