package no.difi.oxalis.as4.util;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import no.difi.oxalis.api.timestamp.Timestamp;
import no.difi.oxalis.as4.lang.OxalisAs4Exception;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@UtilityClass
public class XMLUtil {

    private static final String NS_ALL = "*";

    static <T> List<T> unmarshal(NodeList nodeList, Class<T> returnType) throws OxalisAs4Exception {
        try {
            Unmarshaller unmarshaller = getUnmarshaller();

            return nodeStream(nodeList)
                    .map(p -> unmarshal(p, returnType, unmarshaller))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new OxalisAs4Exception(
                    String.format("Could not unmarshal %s nodeList from %s", returnType.getName(), getPath(nodeList.item(0)))
            );
        }
    }

    static <T> T unmarshal(Node node, Class<T> returnType) throws OxalisAs4Exception {
        try {
            return unmarshal(node, returnType, getUnmarshaller());
        } catch (Exception e) {
            throw new OxalisAs4Exception(
                    String.format("Could not unmarshal %s node from %s", returnType.getName(), getPath(node))
            );
        }
    }

    @SneakyThrows(JAXBException.class)
    private static <T> T unmarshal(Node node, Class<T> returnType, Unmarshaller unmarshaller) {
        return unmarshaller.unmarshal(node, returnType).getValue();
    }

    @SneakyThrows(JAXBException.class)
    private static Unmarshaller getUnmarshaller() {
        return Marshalling.getInstance().getJaxbContext().createUnmarshaller();
    }

    static Stream<Element> elementStream(NodeList nodeList) {
        return nodeStream(nodeList)
                .filter(p -> p instanceof Element)
                .map(p -> (Element) p);
    }

    private static Stream<Node> nodeStream(NodeList nodeList) {
        return IntStream.range(0, nodeList.getLength())
                .mapToObj(nodeList::item);
    }

    private static String getPath(Node in) {
        List<String> names = new ArrayList<>();

        for (Node node = in; node.getParentNode() != null; node = node.getParentNode()) {
            names.add(node.getNodeName());
        }

        ListIterator li = names.listIterator(names.size());

        StringBuilder sb = new StringBuilder();

        while (li.hasPrevious()) {
            sb.append(li.previous());
            if (li.hasPrevious()) {
                sb.append("/");
            }
        }

        return sb.toString();
    }

    static Element getElementByTagPath(Element in, String... localNames) throws OxalisAs4Exception {
        Element out = in;

        for (String localName : localNames) {
            out = getElementByTagName(out, localName);
        }

        return out;
    }

    static Element getElementByTagName(Element in, String localName) throws OxalisAs4Exception {
        return getElementByTagNameNS(in, NS_ALL, localName);
    }

    private static Element getElementByTagNameNS(Element in, String namespaceURI,
                                                 String localName) throws OxalisAs4Exception {
        NodeList sigNode = in.getElementsByTagNameNS(namespaceURI, localName);

        if (sigNode.getLength() == 1) {
            return (Element) sigNode.item(0);
        }

        throw new OxalisAs4Exception(String.format(
                "%d %s children of %s", sigNode.getLength(), localName, getPath(in)));
    }

    public static XMLGregorianCalendar toXmlGregorianCalendar(Timestamp ts) throws OxalisAs4Exception {
        GregorianCalendar gc = new GregorianCalendar();
        gc.setTime(ts.getDate());

        try {
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(gc);
        } catch (DatatypeConfigurationException e) {
            throw new OxalisAs4Exception("Could not parse timestamp", e);
        }
    }
}
