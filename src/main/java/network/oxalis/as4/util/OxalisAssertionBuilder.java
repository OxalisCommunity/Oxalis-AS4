package network.oxalis.as4.util;

import static network.oxalis.as4.util.Constants.BASIC_128_GCM_SHA_256;
import static network.oxalis.as4.util.Constants.BASIC_128_GCM_SHA_256_MGF_SHA_256;
import static network.oxalis.as4.util.Constants.OXALIS_ALGORITHM_NAMESPACE;

import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;

import org.apache.cxf.ws.policy.builder.primitive.PrimitiveAssertion;
import org.apache.cxf.ws.policy.builder.primitive.PrimitiveAssertionBuilder;
import org.apache.neethi.Assertion;
import org.apache.neethi.AssertionBuilderFactory;
import org.apache.neethi.builders.xml.XMLPrimitiveAssertionBuilder;
import org.w3c.dom.Element;


/**
 * @author Jonas Hysing Øvrebø (pearl consulting)
 */
public class OxalisAssertionBuilder extends PrimitiveAssertionBuilder {
    private static final Map<QName, Assertion> ASSERTION_MAP = new HashMap<>(2);
    static {
        final QName basic128GCMSha256QName = new QName(OXALIS_ALGORITHM_NAMESPACE, BASIC_128_GCM_SHA_256);
        final QName basic128GCMSha256MgfSha256QName = new QName(OXALIS_ALGORITHM_NAMESPACE, BASIC_128_GCM_SHA_256_MGF_SHA_256);

        ASSERTION_MAP.put(basic128GCMSha256QName, new PrimitiveAssertion(basic128GCMSha256QName));
        ASSERTION_MAP.put(basic128GCMSha256MgfSha256QName, new PrimitiveAssertion(basic128GCMSha256MgfSha256QName));
    }

    @Override
    public Assertion build(final Element element, final AssertionBuilderFactory fact) {
        if (XMLPrimitiveAssertionBuilder.isOptional(element) || XMLPrimitiveAssertionBuilder.isIgnorable(element)) {
            return super.build(element, fact);
        }
        final QName q = new QName(element.getNamespaceURI(), element.getLocalName());
        return ASSERTION_MAP.get(q);
    }
}
