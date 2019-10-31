package no.difi.oxalis.as4.util;

import java.util.Iterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class GeneralUtils {

    public static <T> Stream<T> iteratorToStreamOfUnknownSize(Iterator<T> iterator, int characteristics, boolean parallel){
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator, characteristics),
                parallel);
    }
}
