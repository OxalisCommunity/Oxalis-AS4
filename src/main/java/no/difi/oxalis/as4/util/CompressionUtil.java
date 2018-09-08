package no.difi.oxalis.as4.util;

import com.google.inject.Inject;
import org.apache.commons.io.IOUtils;

import javax.inject.Named;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.zip.GZIPOutputStream;

// https://stackoverflow.com/a/24673254
public class CompressionUtil {
    private final ExecutorService pool;

    @Inject
    public CompressionUtil(@Named("compression-pool") ExecutorService executorService){
        this.pool = executorService;
    }

    /**
     * Gets Compressed Stream for given input Stream
     * @param sourceStream  : Input Stream to be compressed to
     * @return  Compressed Stream
     * @throws IOException when some thing bad happens
     */
    public InputStream getCompressedStream(final InputStream sourceStream) throws IOException {

        if(sourceStream == null) {
            throw new IllegalArgumentException("Source Stream cannot be NULL");
        }

        /**
         *  sourceStream --> zipperOutStream(->intermediateStream -)--> resultStream
         */
        final PipedInputStream resultStream = new PipedInputStream();
        final PipedOutputStream intermediateStream = new PipedOutputStream(resultStream);
        final OutputStream zipperOutStream = new GZIPOutputStream(intermediateStream);

        Runnable copyTask = () -> {
            try {
                int content;
                while((content = sourceStream.read()) >= 0) {
                    zipperOutStream.write(content);
                }
                zipperOutStream.flush();
            } catch (IOException e) {
                IOUtils.closeQuietly(resultStream);  // close it on error case only
                throw new RuntimeException(e);
            } finally {
                // close source stream and intermediate streams
                IOUtils.closeQuietly(sourceStream);
                IOUtils.closeQuietly(zipperOutStream);
                IOUtils.closeQuietly(intermediateStream);
            }
        };

        pool.submit(copyTask);

        return resultStream;
    }
}